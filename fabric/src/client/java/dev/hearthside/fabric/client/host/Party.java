package dev.hearthside.fabric.client.host;

import dev.hearthside.core.auth.GuestTokenAuthority;
import dev.hearthside.core.channel.ControlConnection;
import dev.hearthside.core.channel.ControlServer;
import dev.hearthside.core.channel.HelloVerdict;
import dev.hearthside.core.config.Preset;
import dev.hearthside.core.config.PresetChooser;
import dev.hearthside.core.config.SessionTuning;
import dev.hearthside.core.config.SplitScreenConfig;
import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.ControlSchemeJson;
import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.GamepadSource;
import dev.hearthside.core.input.PadButton;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.launch.GraphicsBackend;
import dev.hearthside.core.launch.HostJvm;
import dev.hearthside.core.launch.LaunchCommand;
import dev.hearthside.core.launch.LaunchCommandBuilder;
import dev.hearthside.core.launch.SatelliteLaunch;
import dev.hearthside.core.launch.UnsupportedLauncherException;
import dev.hearthside.core.layout.ConsoleLayout;
import dev.hearthside.core.layout.LayoutSettings;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.TwoPlayerSplit;
import dev.hearthside.core.process.JdkProcessLauncher;
import dev.hearthside.core.process.RunningProcess;
import dev.hearthside.core.process.SatelliteSupervisor;
import dev.hearthside.core.profile.GuestNames;
import dev.hearthside.core.profile.GuestProfile;
import dev.hearthside.core.profile.PlayerColors;
import dev.hearthside.core.profile.ProfileCarousel;
import dev.hearthside.core.profile.ProfileFile;
import dev.hearthside.core.profile.ProfileStore;
import dev.hearthside.core.profile.QuickSetup;
import dev.hearthside.core.profile.SkinRef;
import dev.hearthside.core.protocol.ControlCodec;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.core.protocol.LifecycleCommand;
import dev.hearthside.core.session.JoinFlow;
import dev.hearthside.core.session.SatelliteDirectories;
import dev.hearthside.core.session.ViewPhases;
import dev.hearthside.core.supervise.RestartPolicy;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.dev.FakeGamepadSource;
import dev.hearthside.fabric.client.input.ButtonGuideRenderer;
import dev.hearthside.fabric.client.input.FrameHook;
import dev.hearthside.fabric.client.input.SdlGamepadSource;
import dev.hearthside.fabric.client.satellite.SessionOptions;
import dev.hearthside.fabric.client.satellite.SodiumVersion;
import dev.hearthside.core.compositor.GuiScaleRule;
import dev.hearthside.core.compositor.SharingFacts;
import dev.hearthside.core.compositor.Transport;
import dev.hearthside.core.compositor.TransportChoice;
import dev.hearthside.core.compositor.TransportDecision;
import dev.hearthside.fabric.client.compositor.Compositor;
import dev.hearthside.fabric.client.compositor.FrameHooks;
import dev.hearthside.fabric.client.compositor.GuiScaleOverride;
import dev.hearthside.fabric.client.compositor.OverlayPass;
import dev.hearthside.fabric.client.compositor.Presentation;
import dev.hearthside.fabric.client.compositor.backend.ExportDevice;
import dev.hearthside.fabric.client.compositor.backend.SharedImages;
import dev.hearthside.fabric.client.window.WindowPlacer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

/**
 * The co-op party: controllers, the control channel and one supervised game per guest seat. It can exist before a
 * world does (a lobby) and attaches to each world's {@link WorldLink} when it opens, sending every ready guest in.
 * The host's pause-on-focus-loss setting is changed only while at least one guest is present.
 */
public final class Party {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static volatile Party current;

	private final Minecraft minecraft;
	private final Path gameDir;
	private final GuestTokenAuthority tokens = GuestTokenAuthority.withRandomSecret(Clock.systemUTC());
	/** The open world guests join, or null before one opens (a lobby) and after it closes. */
	private volatile WorldLink world;
	private final ControlServer controlServer;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
			Thread.ofPlatform().daemon().name("hearthside-restarts").factory());
	private final Map<Integer, Slot> slots = new ConcurrentSkipListMap<>();
	private final Thread shutdownHook = new Thread(this::stopAllSatellites, "hearthside-shutdown");
	/** The controller loop; created and used on the render thread. */
	private volatile HostInput input;

	// Render-thread state for split-screen mode.
	private boolean splitScreen;
	private Rect hostWindowBefore;
	private boolean pauseOnLostFocusBefore;
	private Rect workArea;
	/** Where every player's view is drawn in the one window while compositing (fresh for each split-screen run). */
	private Views views;
	/** What each guest's view shows: their card while they join, then their game (fresh for each split-screen run). */
	private ViewPhases phases = new ViewPhases();
	/** Guests whose views are fading out: who they were (and their card, if they were still picking), drawn as they were. */
	private record Leaving(dev.hearthside.fabric.client.ui.CardPainter.Who who, SeatCard card) {}
	private final Map<Integer, Leaving> leaving = new HashMap<>();
	/** A view is fading out; once it has gone, the others glide into its space. */
	private boolean relayoutAfterFade;
	/** The views are gliding after a leave; once they settle it is logged (and with nobody left, split-screen ends). */
	private boolean settling;
	/** When the last leave began. */
	private long leaveAt;
	/** Player 1's GUI-scale setting the party's GUI scale was last worked out from (-1: not yet, or not compositing). */
	private int guiScaleSetting = -1;
	private final ViewOverlay viewOverlay = new ViewOverlay(this);
	/** The party panel's live map: sampled on the server thread, drawn from a texture. */
	private final MapSampler mapSampler;
	private final PartyMapTexture mapTexture = new PartyMapTexture();
	private int lastMarkerCount = -1;
	/** Whether guests render hidden and this window composites them (decided on the render thread at session start). */
	private volatile boolean composite;
	/** Player 1's colour, from the host profile. */
	private volatile int hostColor = PlayerColors.DEFAULTS[0];
	private volatile boolean hostReduceMotion;
	/** The controller model player 1 last took in the lobby; taken again automatically there. */
	private volatile String hostControllerGuid;
	/** "Use a controller" was pressed: the next A on a free controller becomes player 1's. */
	private volatile boolean hostClaimArmed;
	/** GPU sharing or copying, as this machine's probe decided at session start. */
	private volatile Transport hostTransport = Transport.COPY;
	/** The graphics API guests render with: this game's own, so its compositor can take their frames (decided at session start). */
	private volatile GraphicsBackend guestBackend = GraphicsBackend.OPENGL;
	/** Each guest's heap and render-distance cap, from {@code config/hearthside.json} as it was when this session opened. */
	private final SplitScreenConfig config;
	/** The party's preset: the configured one, or what Auto chose for this machine when the party was created (plan R5). */
	private final Preset preset;
	/** Whether this party lowered the world's simulation distance below player 1's own. Server thread. */
	private boolean simulationLowered;
	/**
	 * A world opened: the ticks to wait before its simulation distance is set. The integrated server sets player 1's own
	 * at the very end of its first tick, after every tick event. Server thread.
	 */
	private int simulationAfterTicks;

	private static final class Slot {
		final int index;
		/** Null while the seat is still choosing on its lobby card. */
		volatile GuestProfile profile;
		volatile String secret;
		volatile SatelliteSupervisor supervisor;
		volatile ControlConnection connection;
		/** The frame-slot generation this guest's game announced last, as it arrived (ahead of the import it queues). */
		volatile int announcedGeneration;

		Slot(int index, GuestProfile profile) {
			this.index = index;
			this.profile = profile;
		}

		String displayName() {
			GuestProfile p = profile;
			return p != null ? p.name() : "Player " + (index + 1);
		}
	}

	private Party(Minecraft minecraft, JoinFlow.Mode mode) throws IOException {
		this.minecraft = minecraft;
		this.flow = new JoinFlow(mode, 3, Clock.systemUTC());
		this.gameDir = minecraft.gameDirectory.toPath().toAbsolutePath();
		this.config = SplitScreenConfigFile.load(gameDir);
		this.preset = choosePreset();
		HearthsideMod.LOGGER.info(
				"Hearthside split-screen config guestMemoryMb={} guestRenderDistance={} layout={} preset={} renderDistanceCap={}",
				config.guestMemoryMb(), config.guestRenderDistance(), config.layout(), name(preset),
				SessionTuning.of(preset, config).renderDistanceCap());
		this.views = new Views(config.layout());
		this.mapSampler = new MapSampler(minecraft.getUser().getName());
		this.controlServer = ControlServer.start(this::verifyHello);
		Runtime.getRuntime().addShutdownHook(shutdownHook);
		minecraft.execute(this::choosePresentation);
		minecraft.execute(this::startInput);
		HearthsideMod.LOGGER.info("Hearthside session open: control channel on {}", controlServer.port());
	}

	/** Composite into this window when this game can; otherwise tile real windows. Render thread. */
	private void choosePresentation() {
		GraphicsBackend own = "Vulkan".equals(RenderSystem.getDevice().getDeviceInfo().backendName())
				? GraphicsBackend.VULKAN : GraphicsBackend.OPENGL;
		HearthsideMod.LOGGER.info("Hearthside graphics backend={}", own.argument());
		guestBackend = own;
		// E2E: guests on a given backend, whatever the host's.
		String override = System.getProperty("hearthside.e2e.guestBackend");
		if (override != null) {
			for (GraphicsBackend b : GraphicsBackend.values()) {
				if (b.argument().equalsIgnoreCase(override)) {
					guestBackend = b;
				}
			}
			HearthsideMod.LOGGER.info("Hearthside guests' backend override \"{}\": guests use {}", override, guestBackend.argument());
		}
		try {
			// E2E: tiled windows, as where the canvas can't be created.
			composite = !Boolean.getBoolean("hearthside.e2e.forceTiled") && Compositor.canComposite();
			if (composite) {
				// Decided once per session here, where the window is: a host that can't import launches every guest
				// copying (Spec C section 3.7, step 1). Each guest's slots are checked against it as they arrive.
				SharingFacts facts = SharedImages.forThisGame().facts();
				TransportDecision decision = TransportChoice.decide(facts, Presentation.forceCopy());
				HearthsideMod.LOGGER.info("Hearthside GPU sharing on this host: {} -> {}", facts, decision);
				hostTransport = decision.transport();
			}
		} catch (RuntimeException | LinkageError e) {
			HearthsideMod.LOGGER.error("Hearthside could not set up compositing; tiled windows for this session", e);
			composite = false;
		}
		HearthsideMod.LOGGER.info("Hearthside split-screen presentation: {}", composite ? "one window" : "tiled windows");
	}

	// ---- Performance presets (Spec D section 3.3) ----

	/**
	 * The party's preset: the configured one, or for Auto what {@link PresetChooser} makes of this machine (its GPU, its
	 * memory, the players the connected controllers make and the window's size), logged with its reasons. The render
	 * thread for a lobby, the server thread for a world opened without one.
	 */
	private Preset choosePreset() {
		if (config.preset() != Preset.AUTO) {
			HearthsideMod.LOGGER.info("Hearthside preset {} (config)", name(config.preset()));
			return config.preset();
		}
		PresetChooser.Choice choice;
		try {
			Window window = minecraft.getWindow();
			choice = PresetChooser.choose(new PresetChooser.Machine(gpuKind(), memoryBytes(),
					Math.min(4, connectedControllers() + 1), window.getWidth(), window.getHeight()));
		} catch (RuntimeException | LinkageError e) {
			// Never a party that can't open: what every party had before presets.
			HearthsideMod.LOGGER.warn("Hearthside could not look at this machine for Auto; Balanced for this party", e);
			choice = new PresetChooser.Choice(Preset.BALANCED, List.of("machine unknown"));
		}
		HearthsideMod.LOGGER.info("Hearthside preset auto → {} ({})", name(choice.preset()), String.join(", ", choice.reasons()));
		return choice.preset();
	}

	/**
	 * This game's GPU, as Minecraft's device reports it (E2E: {@code hearthside.e2e.fakeGpuType}). Minecraft's OpenGL
	 * only tells Intel GPUs apart; for any other, Vulkan is asked which kind the GPU OpenGL runs on is (plan ruling T5-6).
	 */
	private static PresetChooser.GpuKind gpuKind() {
		String fake = System.getProperty("hearthside.e2e.fakeGpuType");
		if (fake != null) {
			try {
				return kindOf(DeviceType.valueOf(fake.strip().toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException e) {
				return PresetChooser.GpuKind.UNKNOWN;
			}
		}
		com.mojang.renderpearl.api.device.DeviceInfo info;
		try {
			info = RenderSystem.getDevice().getDeviceInfo();
		} catch (RuntimeException e) {
			return PresetChooser.GpuKind.UNKNOWN;
		}
		PresetChooser.GpuKind kind = kindOf(info.type());
		if (kind == PresetChooser.GpuKind.UNKNOWN && "OpenGL".equals(info.backendName())) {
			return dev.hearthside.fabric.client.compositor.backend.VulkanProbe.gpuKind(info.name(), info.vendorName());
		}
		return kind;
	}

	private static PresetChooser.GpuKind kindOf(DeviceType type) {
		return switch (type) {
			case INTEGRATED -> PresetChooser.GpuKind.INTEGRATED;
			case DISCRETE -> PresetChooser.GpuKind.DISCRETE;
			default -> PresetChooser.GpuKind.UNKNOWN;
		};
	}

	/** The machine's physical memory (E2E: {@code hearthside.e2e.fakeRamGb}). */
	private static long memoryBytes() {
		String fake = System.getProperty("hearthside.e2e.fakeRamGb");
		if (fake != null) {
			try {
				return Long.parseLong(fake.strip()) << 30;
			} catch (NumberFormatException e) {
				HearthsideMod.LOGGER.warn("Hearthside ignores hearthside.e2e.fakeRamGb={}", fake);
			}
		}
		try {
			return ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
		} catch (RuntimeException | LinkageError e) {
			// A Java without the JDK's management extension: Minecraft's own hardware library knows it too.
			return new oshi.SystemInfo().getHardware().getMemory().getTotal();
		}
	}

	/** Controllers connected now, nobody's yet: each could be a player. */
	private int connectedControllers() {
		if (Boolean.getBoolean("hearthside.e2e.fakePads")) {
			return FakeGamepadSource.INSTANCE.poll().size();
		}
		return minecraft.isSameThread() ? SdlGamepadSource.connectedCount() : SdlGamepadSource.connectedCountAnyThread();
	}

	/** The preset this session plays at now: the party's (E2E: {@code hearthside.e2e.preset} forces one, read live). */
	private Preset sessionPreset() {
		String forced = System.getProperty("hearthside.e2e.preset");
		if (forced != null) {
			for (Preset p : Preset.values()) {
				if (p != Preset.AUTO && p.name().equalsIgnoreCase(forced.strip())) {
					return p;
				}
			}
		}
		return preset;
	}

	/** What the session's preset sets: guests' cap, graphics and frame rate, the world's simulation distance. */
	private SessionTuning tuning() {
		return SessionTuning.of(sessionPreset(), config);
	}

	/**
	 * The world simulates at most the preset's distance, for this session: player 1's own setting is left as it is, and
	 * the integrated server keeps this until player 1 moves their own slider. With no such limit (any more), player 1's
	 * own distance again. Any thread; done on the server's.
	 */
	private void applySimulationDistance(SessionTuning tuning) {
		WorldLink link = world;
		if (link == null) {
			return;
		}
		net.minecraft.server.MinecraftServer server = link.server();
		server.execute(() -> {
			var players = server.getPlayerList();
			int own = minecraft.options.simulationDistance().get();
			int limit = tuning.simulationDistance();
			if (limit > 0 && own >= limit) {
				if (players.getSimulationDistance() != limit) {
					players.setSimulationDistance(limit);
				}
				// Logged each time it applies: as the world opens, and at each guest's launch.
				HearthsideMod.LOGGER.info("Hearthside simulation distance {} (preset)", limit);
				simulationLowered = true;
			} else if (simulationLowered) {
				simulationLowered = false;
				players.setSimulationDistance(own);
				HearthsideMod.LOGGER.info("Hearthside simulation distance {} (own setting)", own);
			}
		});
	}

	private static String name(Preset preset) {
		return preset.name().toLowerCase(Locale.ROOT);
	}

	/** Opens the controllers and starts the per-frame input loop. Render thread (SDL). */
	private void startInput() {
		GamepadSource source;
		try {
			source = Boolean.getBoolean("hearthside.e2e.fakePads") ? FakeGamepadSource.INSTANCE : SdlGamepadSource.open();
		} catch (IllegalStateException e) {
			HearthsideMod.LOGGER.error("Controllers are unavailable", e);
			return;
		}
		ControlScheme hostScheme;
		try {
			ProfileFile file = profileStore().loadFile();
			hostScheme = file.hostControls();
			hostColor = file.host().color();
			hostReduceMotion = file.host().settings().reduceMotion();
			ButtonGuideRenderer.setReduceMotion(hostReduceMotion);
			dev.hearthside.fabric.client.compositor.OverlayPass.setReduceMotion(hostReduceMotion);
			OverlayPass.unregister(joinToast);
			OverlayPass.register(joinToast);
			hostControllerGuid = file.host().preferredControllerGuid();
		} catch (IOException e) {
			HearthsideMod.LOGGER.warn("Could not read the host's controls; using Bedrock defaults", e);
			hostScheme = ControlScheme.BEDROCK;
		}
		input = new HostInput(minecraft, this, source, hostScheme);
		FrameHook.register(input);
		if (FabricLoader.getInstance().isModLoaded("controlify")) {
			minecraft.gui.chatListener().handleSystemMessage(Component.literal(
					"Controlify is installed: it may also react to controllers. Hearthside handles controllers itself.")
					.withStyle(ChatFormatting.YELLOW), false);
		}
	}

	/** Lowest free guest slot (1-3), or -1. */
	public int nextFreeIndex() {
		for (int index = 1; index <= 3; index++) {
			if (!slots.containsKey(index)) {
				return index;
			}
		}
		return -1;
	}

	/** A world opened: let every guest whose game is ready log in to it. */
	private void attach(WorldLink link) {
		world = link;
		// Once the world has ticked: its first tick sets player 1's own simulation distance.
		simulationAfterTicks = 2;
		// The lobby is over: seats stay, and from now on new controllers join with A, A. (The flow is render-thread
		// only; this runs on the server thread.)
		minecraft.execute(flow::enterGame);
		// Seats still choosing go in with what their card shows (an in-view picker is Plan 5c).
		minecraft.execute(this::confirmWaitingCards);
		minecraft.execute(this::afterGuestsChanged);
		for (Slot slot : slots.values()) {
			ControlConnection connection = slot.connection;
			// A seat with its card open (choosing, or changing profile) goes in once its card settles.
			if (connection != null && !cards.containsKey(slot.index)) {
				sendIn(link, slot, connection);
			}
		}
	}

	/** Sends a guest into the world once they have a profile (a seat still choosing waits). */
	private void sendIn(WorldLink link, Slot slot, ControlConnection connection) {
		GuestProfile profile = slot.profile;
		if (profile == null) {
			return;
		}
		try {
			link.sendIn(slot.index, profile, connection);
		} catch (IOException | IllegalArgumentException e) {
			HearthsideMod.LOGGER.error("Could not send {} into the world", profile.name(), e);
		}
	}

	/** {@code /hearthside pad}: the next controller to press A becomes the host's. Render thread. */
	public void armHostClaim() {
		if (input == null) {
			throw new IllegalStateException("Controllers are unavailable");
		}
		input.armClaim();
	}

	/** E2E: a guest's game leaves the world, as its own pause menu's Disconnect would. */
	public void e2eDisconnectGuest(int index) {
		sendToGuest(index, new ControlMessage.Lifecycle(LifecycleCommand.DISCONNECT));
	}

	void sendToGuest(int index, ControlMessage message) {
		Slot slot = slots.get(index);
		ControlConnection connection = slot == null ? null : slot.connection;
		if (connection != null) {
			send(connection, message);
		}
	}

	/** Whether a world is open (otherwise the party is on shared screens: the lobby, world select). */
	public boolean inWorld() {
		return world != null;
	}

	private ProfileStore profileStore() {
		return new ProfileStore(gameDir.resolve("hearthside").resolve("profiles.json"));
	}

	/** The join toast for the party, if one is open (the E2E reads it). */
	public dev.hearthside.fabric.client.ui.JoinToast joinToast() {
		return joinToast;
	}

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			Party party = current;
			if (party != null) {
				party.mapSampler.tick(server);
				if (party.simulationAfterTicks > 0 && --party.simulationAfterTicks == 0) {
					party.applySimulationDistance(party.tuning());
				}
			}
		});
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Party party = current;
			if (party != null) {
				party.followGuiScaleSetting();
			}
		});
		// When player 1 plays alone there is no overlay pass: the join toast draws on their HUD instead.
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
				net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
				net.minecraft.resources.Identifier.fromNamespaceAndPath(HearthsideMod.MOD_ID, "join_toast"), (g, delta) -> {
					Party party = current;
					if (party != null && Compositor.active() == null) {
						party.joinToast.draw(g, g.guiWidth(), System.nanoTime());
					}
				});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (server instanceof IntegratedServer) {
				try {
					if (current == null) {
						current = new Party(Minecraft.getInstance(), JoinFlow.Mode.IN_GAME);
					}
					current.attach(new WorldLink(server, current.tokens));
				} catch (IOException e) {
					HearthsideMod.LOGGER.error("Could not open a Hearthside session", e);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Party session = current;
			current = null;
			if (session != null) {
				session.close();
			}
		});
	}

	/** The party for the co-op lobby: the current one, or a new one waiting for a world. Render thread. */
	public static Party openLobby(Minecraft minecraft) throws IOException {
		return openLobby(minecraft, null);
	}

	/**
	 * The lobby's party; {@code titlePad} is the controller that pressed Co-op on the title screen (null for the mouse),
	 * which becomes player 1's.
	 */
	public static Party openLobby(Minecraft minecraft, Integer titlePad) throws IOException {
		if (current == null) {
			current = new Party(minecraft, JoinFlow.Mode.LOBBY);
		}
		current.pendingHostPad = titlePad;
		return current;
	}

	/** The title screen's controller, for the controller loop to hand to player 1 when it first sees it. */
	volatile Integer pendingHostPad;
	/**
	 * A controller still holding the button that answered a card before the lobby (the Vulkan offer's Not now): that
	 * press is not a join. It is player 1's only if it also pressed Co-op or is their remembered controller, and then
	 * the button it still holds presses nothing in the lobby either.
	 */
	volatile Integer answeringPad;

	/** Ends the party: every guest's game stops. */
	public void disband() {
		if (current == this) {
			current = null;
		}
		close();
	}

	/** Every guest leaves (their games stop); the party stays. Render thread. */
	public void leaveAll() {
		for (Slot slot : List.copyOf(slots.values())) {
			endSlot(slot);
			removeSlot(slot);
		}
	}

	/** What a lobby card shows for a guest seat. */
	public record SeatView(String name, String status, int color, boolean taken) {}

	/** The lobby card for player {@code index} (1-3). */
	public SeatView seatView(int index) {
		Slot slot = slots.get(index);
		if (slot == null) {
			return new SeatView("Press A to join", "", 0x555555, false);
		}
		String controller = controllerOfSlot.get(index);
		dev.hearthside.core.session.SeatState state = controller == null ? null : flow.seats().get(controller);
		String status = state instanceof dev.hearthside.core.session.SeatState.Disconnected ? "Controller disconnected"
				: slot.connection == null ? "Getting ready" : "Ready";
		GuestProfile profile = slot.profile;
		if (profile == null) {
			return new SeatView(slot.displayName(), "Choosing…", PlayerColors.DEFAULTS[index % PlayerColors.DEFAULTS.length],
					true);
		}
		return new SeatView(profile.name(), status, profile.color(), true);
	}

	public int seatedCount() {
		return slots.size();
	}

	/** How this session shares the screen, from {@code config/hearthside.json} as it was when the session opened. */
	public LayoutSettings layout() {
		return config.layout();
	}

	public static Party current() {
		return current;
	}

	/** Adds a guest and starts their satellite; returns their player index. Runs on the render thread. */
	public int join(String name) throws IOException, UnsupportedLauncherException {
		GuestNames.requireValid(name);
		if (name.equalsIgnoreCase(minecraft.getUser().getName())) {
			throw new IllegalArgumentException("A guest can't use the host's name (" + minecraft.getUser().getName() + ")");
		}
		if (slots.values().stream().anyMatch(s -> s.profile != null && s.profile.name().equalsIgnoreCase(name))) {
			throw new IllegalArgumentException(name + " is already playing");
		}
		return startGuest(loadOrCreateProfile(name));
	}

	/** The command with one more environment variable for the guest's game (E2E hooks: satellites get no -Dhearthside.*). */
	private static LaunchCommand withEnvironment(LaunchCommand command, String name, String value) {
		Map<String, String> environment = new HashMap<>(command.environment());
		environment.put(name, value);
		return new LaunchCommand(command.command(), environment, command.removedEnvironment(), command.workingDirectory());
	}

	/** Starts a guest's game in the lowest free slot, as {@code profile}; returns their player index. Render thread. */
	private int startGuest(GuestProfile profile) throws IOException, UnsupportedLauncherException {
		HostJvm host = HostJvm.capture(HostLaunchFacts.read());
		int index = nextFreeIndex();
		if (index < 0) {
			throw new IllegalStateException("All 3 guest slots are taken");
		}
		// The seat may still be fading out from whoever had it last: the newcomer starts afresh.
		leaving.remove(index);
		phases.remove(index);

		Slot slot = new Slot(index, profile);
		Path satelliteDir = gameDir.resolve("hearthside").resolve("satellite-" + index);
		slot.supervisor = new SatelliteSupervisor(new JdkProcessLauncher(), () -> {
			slot.secret = newSecret();
			try {
				Path dir = SatelliteDirectories.prepare(gameDir, index);
				// The party's preset, for this game's session: a drop-in gets the same as everyone (plan R5).
				SessionTuning tuning = tuning();
				LaunchCommand command = LaunchCommandBuilder.build(host,
						// A placeholder: the seat's game takes its player's name when sent into the world.
						new SatelliteLaunch(index, controlServer.port(), dir, "Seat" + index, slot.secret, presentation(),
								guestBackend, config.guestMemoryMb(), tuning.renderDistanceCap(), tuning.lightGraphics(),
								tuning.fpsCap()));
				applySimulationDistance(tuning);
				if (Boolean.getBoolean("hearthside.e2e.noVulkanLoader")) {
					// E2E: the guest behaves as if its machine had no Vulkan runtime.
					command = withEnvironment(command, ExportDevice.NO_VULKAN_LOADER_ENV, "1");
				}
				for (Map.Entry<String, String> hook : FrameHooks.GUEST_ENV.entrySet()) {
					// E2E: the frame hand-over's stress hooks (F1, F3), for the guest's side of it.
					String value = System.getProperty(hook.getKey());
					if (value != null) {
						command = withEnvironment(command, hook.getValue(), value);
					}
				}
				if (Boolean.getBoolean("hearthside.e2e.guestTryFullscreen")) {
					// E2E: once in the world, the guest tries every way into fullscreen (Q4).
					command = withEnvironment(command, dev.hearthside.fabric.client.dev.GuestFullscreenAttempt.ENV, "1");
				}
				if (Boolean.getBoolean("hearthside.e2e.guestSaveOptions")) {
					// E2E: once in the world, the guest saves its options, as closing Video Settings does (Q1b).
					command = withEnvironment(command, dev.hearthside.fabric.client.dev.GuestSavesOptions.ENV, "1");
				}
				String sodium = System.getProperty("hearthside.e2e.pretendSodiumVersion");
				if (sodium != null) {
					// E2E: the guest takes its Sodium for this version (Q4b).
					command = withEnvironment(command, SodiumVersion.PRETEND_ENV, sodium);
				}
				return command;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}, satelliteDir.resolve("logs").resolve("hearthside-launch.log"),
				// E2E: no restarts, so the first crash gives up.
				Boolean.getBoolean("hearthside.e2e.failRestarts") ? new RestartPolicy(List.of(), Duration.ofMinutes(1))
						: RestartPolicy.DEFAULT,
				(task, delay) -> scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS),
				Clock.systemUTC(), new SlotListener(slot));
		if (slots.isEmpty()) {
			enterSplitScreen();
		}
		slots.put(index, slot);
		try {
			slot.supervisor.start();
		} catch (IOException | RuntimeException e) {
			removeSlot(slot);
			throw e;
		}
		minecraft.execute(this::afterGuestsChanged);
		return index;
	}

	/** Removes a guest and closes their satellite: their view fades out and the layout glides back. Render thread. */
	public void leave(String name) {
		Slot slot = slots.values().stream()
				.filter(s -> s.profile != null && s.profile.name().equalsIgnoreCase(name))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(name + " isn't playing"));
		endSlot(slot);
		removeSlot(slot);
	}

	private void close() {
		for (Slot slot : slots.values()) {
			endSlot(slot);
		}
		slots.clear();
		controlServer.close();
		scheduler.shutdownNow();
		WorldLink link = world;
		world = null;
		if (link != null) {
			link.close();
		}
		try {
			Runtime.getRuntime().removeShutdownHook(shutdownHook);
		} catch (IllegalStateException alreadyShuttingDown) {
			// the hook is running or about to; nothing to undo
		}
		minecraft.execute(() -> {
			if (input != null) {
				FrameHook.unregister(input);
				input.close();
				input = null;
			}
			OverlayPass.unregister(joinToast);
			leaveSplitScreen();
		});
		HearthsideMod.LOGGER.info("Hearthside session closed");
	}

	private void endSlot(Slot slot) {
		ControlConnection connection = slot.connection;
		if (connection != null) {
			try {
				connection.send(new ControlMessage.Lifecycle(LifecycleCommand.QUIT));
			} catch (IOException ignored) {
				// the supervisor stops the process regardless
			}
		}
		slot.supervisor.stop();
	}

	// ---- Join flow: controllers joining with A, A (Spec A section 3.2) ----

	private final JoinFlow flow;
	/** Join-flow controller id ("pad-<SDL id>") of each guest slot. */
	private final Map<Integer, String> controllerOfSlot = new ConcurrentHashMap<>();
	private final Map<String, Integer> instanceOf = new HashMap<>();
	private final Map<String, String> guidOf = new HashMap<>();
	/** Each unassigned controller's family, so the join toast names its own button. */
	private final Map<String, PadStyle> styleOfController = new HashMap<>();
	/** The drop-in prompt (over the views, or on player 1's HUD when they play alone). */
	final dev.hearthside.fabric.client.ui.JoinToast joinToast = new dev.hearthside.fabric.client.ui.JoinToast();
	/** Controllers whose lobby card opens once their seat's game has started. */
	private final java.util.Set<String> pendingCards = new java.util.HashSet<>();
	/** Lobby cards of seats still choosing a profile, by player index. */
	private final Map<Integer, SeatCard> cards = new ConcurrentHashMap<>();

	private static String controllerId(int instanceId) {
		return "pad-" + instanceId;
	}

	/** An unassigned controller pressed A or B. Render thread. */
	void onUnassignedPress(int instanceId, PadButton button, String guid) {
		String controller = controllerId(instanceId);
		instanceOf.put(controller, instanceId);
		guidOf.put(controller, guid);
		HostInput currentInput = input;
		styleOfController.put(controller, currentInput == null ? PadStyle.GENERIC : currentInput.styleOfPad(instanceId));
		apply(flow.press(controller, button == PadButton.A ? Control.A : Control.B));
	}

	/** B on a seated controller before the world: that player gives up their seat (Spec A section 3.1). Render thread. */
	void onSeatedB(int player) {
		String controller = controllerOfSlot.get(player);
		if (controller != null) {
			apply(flow.press(controller, Control.B));
		}
	}

	// ---- Player 1 in the lobby ----

	/** Player 1's reduce-motion setting (the lobby's embers and card flips). */
	public boolean reduceMotion() {
		return hostReduceMotion;
	}

	public boolean hostHasController() {
		HostInput current = input;
		return current != null && current.hasPadFor(0);
	}

	/** Player 1 holds a controller and is playing on it: their mouse isn't driving the open screen. */
	public boolean hostOnController() {
		HostInput current = input;
		return current != null && current.hasPadFor(0) && !current.hostMouseDriving();
	}

	public boolean hostClaimArmed() {
		return hostClaimArmed && !hostHasController();
	}

	/** Player 1's card: gives their controller back (it can then join as a guest) and forgets it. Render thread. */
	public void releaseController() {
		HostInput current = input;
		if (current != null) {
			current.releaseHost();
		}
		hostClaimArmed = false;
		hostControllerGuid = null;
		try {
			ProfileStore store = profileStore();
			ProfileFile file = store.loadFile();
			store.saveFile(new ProfileFile(file.profiles(), file.host().withController(null)));
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not forget player 1's controller", e);
		}
	}

	/** Player 1's card: the next A on a free controller makes it theirs. Render thread. */
	public void useController() {
		HostInput current = input;
		if (current != null) {
			current.armClaim();
			hostClaimArmed = true;
		}
	}

	/** Player 1 took a controller: remember its model so the lobby gives it back next time. Render thread. */
	void onHostClaimed(String guid) {
		hostClaimArmed = false;
		if (guid == null || guid.equals(hostControllerGuid)) {
			return;
		}
		hostControllerGuid = guid;
		try {
			ProfileStore store = profileStore();
			ProfileFile file = store.loadFile();
			store.saveFile(new ProfileFile(file.profiles(), file.host().withController(guid)));
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not remember player 1's controller", e);
		}
	}

	String hostControllerGuid() {
		return hostControllerGuid;
	}

	/**
	 * The button style to draw for a player's controller; for -1, the first controller in the party (player 1's, then
	 * seats in order). Xbox when there is none.
	 */
	public PadStyle padStyleOf(int player) {
		HostInput current = input;
		if (current == null) {
			return PadStyle.XBOX;
		}
		Leaving gone = player > 0 ? gone(player) : null;
		if (gone != null) {
			return gone.who().style(); // their controller has gone already; their fading card still shows its buttons
		}
		if (player >= 0) {
			return current.hasPadFor(player) ? current.styleFor(player) : PadStyle.XBOX;
		}
		for (int p = 0; p < 4; p++) {
			if (current.hasPadFor(p)) {
				return current.styleFor(p);
			}
		}
		return PadStyle.XBOX;
	}

	/** Connected controllers nobody is using: one could take a seat now. */
	public int freeControllers() {
		HostInput current = input;
		return current == null ? 0 : current.freePadCount();
	}

	/**
	 * The buttons to show for joining: the free controllers' family (generic letters for a mix), as those are the
	 * controllers that would press it. Xbox when none is free.
	 */
	public PadStyle joinStyle() {
		HostInput current = input;
		PadStyle style = current == null ? null : current.freePadStyle();
		return style != null ? style : PadStyle.XBOX;
	}

	/**
	 * The buttons to show for the shared screen (Back, Start, Profile): the controller that last drove it, as with the
	 * pointer's colour; before anyone has, the first controller in the party.
	 */
	public PadStyle sharedStyle() {
		HostInput current = input;
		int driver = current == null ? -1 : current.lastSharedDriver();
		return driver >= 0 && current.hasPadFor(driver) ? current.styleFor(driver) : padStyleOf(-1);
	}

	/** How long a card says a game couldn't start before the view fades out. */
	private static final Duration FAILED_SHOWN = Duration.ofMillis(2500);

	/** Keeps showing a seat's last picture from its game (it crashed) until its restarted game is in the world. */
	private static void holdLastFrame(int seat, boolean hold) {
		Compositor compositor = Compositor.active();
		if (compositor != null) {
			compositor.holdLastFrame(seat, hold);
		}
	}

	/** A seat whose player has left and whose view is still fading out, or null. */
	private Leaving gone(int seat) {
		return slots.containsKey(seat) ? null : leaving.get(seat);
	}

	/** A guest's chosen profile, or null while they are choosing (or the seat is free). */
	public GuestProfile profileOf(int index) {
		Slot slot = slots.get(index);
		return slot == null ? null : slot.profile;
	}

	// ---- Lobby cards: each seat picks or makes its profile (Spec A sections 3.1, 5.2) ----

	/** The card of a seat still choosing its profile, or null. */
	public SeatCard seatCard(int player) {
		return cards.get(player);
	}

	/** The card drawn in a seat's view: theirs while they pick, or the one they left with while their view fades. */
	SeatCard drawnCard(int seat) {
		SeatCard card = cards.get(seat);
		Leaving gone = gone(seat);
		return card != null || gone == null ? card : gone.card();
	}

	boolean hasSeatCard(int player) {
		return cards.containsKey(player);
	}

	/** That seat's controller drives its card. Render thread. */
	void seatCardInput(int player, InputFrame frame, long nanos) {
		SeatCard card = cards.get(player);
		if (card == null) {
			return;
		}
		switch (card.accept(frame, nanos)) {
			case CONFIRMED -> confirmProfile(player, card.chosen());
			case LEAVE -> onSeatedB(player);
			case CONTROLS -> {
				if (card.moreProfile() != null && minecraft.gui.screen() instanceof LobbyScreen lobby) {
					minecraft.gui.setScreen(new ControlsScreen(lobby, this, player, card.moreProfile()));
				}
			}
			case MANAGE -> {
				if (minecraft.gui.screen() instanceof LobbyScreen lobby) {
					minecraft.gui.setScreen(new ManageProfilesScreen(lobby, this, player));
				}
			}
			case NONE -> { }
		}
	}

	/** Whether a seat is playing as this profile. */
	public boolean profileInUse(UUID profileId) {
		return slots.values().stream().anyMatch(s -> s.profile != null && s.profile.id().equals(profileId));
	}

	/** Deletes a saved profile nobody is playing as; open cards drop it. False if in use or it couldn't be saved. */
	public boolean deleteProfile(UUID profileId) {
		if (profileInUse(profileId)) {
			return false;
		}
		try {
			ProfileStore store = profileStore();
			List<GuestProfile> profiles = new ArrayList<>(store.load());
			if (!profiles.removeIf(p -> p.id().equals(profileId))) {
				return false;
			}
			store.save(profiles);
		} catch (IOException | IllegalArgumentException e) {
			HearthsideMod.LOGGER.error("Could not delete a profile", e);
			return false;
		}
		cards.values().forEach(card -> card.removeProfile(profileId));
		HearthsideMod.LOGGER.info("Hearthside deleted a profile");
		return true;
	}

	/** The saved guest profiles, least recently used first (empty if they can't be read). */
	public List<GuestProfile> savedProfiles() {
		try {
			return profileStore().load();
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not read guest profiles", e);
			return List.of();
		}
	}

	/** X on a ready seat's card in the lobby: its More (controls, profiles); B goes back to ready. Render thread. */
	void openMore(int player) {
		Slot slot = slots.get(player);
		String controller = controllerOfSlot.get(player);
		GuestProfile current = slot == null ? null : slot.profile;
		if (current == null || controller == null || world != null || cards.containsKey(player)) {
			return;
		}
		if (flow.changeProfile(controller)) {
			cards.put(player, newCard(player, controller, current, true));
		}
	}

	/** Back in the lobby from a screen a card opened: what is still held there doesn't press the card. */
	void backToCard(int player) {
		SeatCard card = cards.get(player);
		if (card != null) {
			card.ignoreHeld();
		}
	}

	/**
	 * Saves a profile's controls (the controls screen's Menu): the stored profile keeps its place, a seat playing it
	 * gets them at once, and open cards pick the new copy. False if the profile couldn't be saved.
	 */
	public boolean saveControls(UUID profileId, ControlScheme scheme) {
		GuestProfile updated;
		try {
			ProfileStore store = profileStore();
			List<GuestProfile> profiles = new ArrayList<>(store.load());
			int at = -1;
			for (int i = 0; i < profiles.size(); i++) {
				if (profiles.get(i).id().equals(profileId)) {
					at = i;
				}
			}
			if (at < 0) {
				return false;
			}
			updated = profiles.get(at).withControls(scheme);
			profiles.set(at, updated);
			store.save(profiles);
		} catch (IOException | IllegalArgumentException e) {
			HearthsideMod.LOGGER.error("Could not save controls", e);
			return false;
		}
		for (Slot slot : slots.values()) {
			if (slot.profile != null && slot.profile.id().equals(profileId)) {
				slot.profile = slot.profile.withControls(scheme);
				if (slot.connection != null) {
					send(slot.connection, new ControlMessage.SetControls(ControlSchemeJson.toJson(scheme)));
				}
			}
		}
		cards.values().forEach(card -> card.replaceProfile(updated));
		HearthsideMod.LOGGER.info("Hearthside saved {}'s controls", updated.name());
		return true;
	}

	/** Y on a ready seat's card in the lobby: choose its profile again (B keeps the current one). Render thread. */
	void changeProfile(int player) {
		Slot slot = slots.get(player);
		String controller = controllerOfSlot.get(player);
		GuestProfile current = slot == null ? null : slot.profile;
		if (current == null || controller == null || world != null || cards.containsKey(player)) {
			return;
		}
		if (flow.changeProfile(controller)) {
			cards.put(player, newCard(player, controller, current));
		}
	}

	private SeatCard newCard(int index, String controller, GuestProfile keep) {
		return newCard(index, controller, keep, false);
	}

	private SeatCard newCard(int index, String controller, GuestProfile keep, boolean more) {
		List<GuestProfile> profiles = List.of();
		try {
			profiles = profileStore().load();
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not read guest profiles", e);
		}
		String guid = guidOf.get(controller);
		// Checked live: another seat may confirm a profile, or start naming one, while this card is open.
		return new SeatCard(index, new ProfileCarousel(profiles, id -> heldByAnotherSeat(index, id), guid),
				() -> new QuickSetup(namesInUse(index), minecraft.getUser().getName(), takenColors(index), index), guid,
				keep, more);
	}

	private boolean heldByAnotherSeat(int index, UUID profileId) {
		for (Slot slot : slots.values()) {
			GuestProfile profile = slot.profile;
			if (slot.index != index && profile != null && profile.id().equals(profileId)) {
				return true;
			}
		}
		return false;
	}

	/** Names a new profile for this seat can't use: saved profiles, other seats' profiles, and other cards' names. */
	private List<String> namesInUse(int index) {
		List<String> names = new ArrayList<>();
		try {
			profileStore().load().forEach(p -> names.add(p.name()));
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not read guest profiles", e);
		}
		slots.values().forEach(s -> {
			if (s.index != index && s.profile != null) {
				names.add(s.profile.name());
			}
		});
		cards.forEach((seat, other) -> {
			if (seat != index) {
				if (other.keyboard() != null) {
					names.add(other.keyboard().text());
				}
				if (other.setup() != null) {
					names.add(other.setup().suggestedName());
				}
			}
		});
		return names;
	}

	/** Colours other players in the party are using. */
	private List<Integer> takenColors(int except) {
		List<Integer> taken = new ArrayList<>();
		taken.add(colorOf(0));
		slots.values().forEach(s -> {
			if (s.index != except && s.profile != null) {
				taken.add(s.profile.color());
			}
		});
		return taken;
	}

	/** A player's colour (0xRRGGBB): player 1's from the host profile, a guest's from their profile. */
	public int colorOf(int player) {
		if (player == 0) {
			return hostColor;
		}
		Leaving gone = gone(player);
		if (gone != null) {
			return gone.who().colour();
		}
		Slot slot = slots.get(player);
		GuestProfile profile = slot == null ? null : slot.profile;
		return displayColor(player,
				profile != null ? profile.color() : PlayerColors.DEFAULTS[player % PlayerColors.DEFAULTS.length]);
	}

	/**
	 * The colour a seat shows: the one it wants unless player 1 or a lower seat already shows it, then the first free
	 * one, so every player in the party is told apart (Spec A section 4.1). The profile itself is not changed.
	 */
	public int displayColor(int seat, int wanted) {
		List<Integer> taken = new ArrayList<>();
		taken.add(hostColor);
		for (int lower = 1; lower < seat; lower++) {
			if (slots.containsKey(lower)) {
				taken.add(colorOf(lower));
			}
		}
		return taken.contains(wanted) ? PlayerColors.firstFree(taken) : wanted;
	}

	/**
	 * A seat settles on its profile: remember it for this controller, then send the player in if a world is open.
	 * Refused (false, and the card says why) when another seat already plays that profile or a new profile's name is
	 * taken; the card stays open.
	 */
	boolean confirmProfile(int index, GuestProfile picked) {
		Slot slot = slots.get(index);
		if (slot == null || picked == null) {
			return false;
		}
		String problem = problemWith(index, picked);
		if (problem != null) {
			SeatCard card = cards.get(index);
			if (card != null) {
				card.reject(problem);
			}
			return false;
		}
		String controller = controllerOfSlot.get(index);
		String guid = controller == null ? null : guidOf.get(controller);
		GuestProfile profile = guid != null ? picked.withController(guid) : picked;
		if (!rememberProfile(profile)) {
			SeatCard card = cards.get(index);
			if (card != null) {
				card.reject("Couldn't save " + profile.name());
			}
			return false;
		}
		slot.profile = profile;
		cards.remove(index);
		if (world != null && phases.phase(index).orElse(null) == dev.hearthside.core.session.ViewPhase.PICKING) {
			phases.joining(index, System.nanoTime());
		}
		if (controller != null) {
			apply(flow.profileChosen(controller, profile.id()));
		}
		ControlConnection connection = slot.connection;
		if (connection != null) {
			send(connection, new ControlMessage.SetControls(ControlSchemeJson.toJson(profile.controls())));
			WorldLink link = world;
			if (link != null) {
				sendIn(link, slot, connection);
			}
		}
		HearthsideMod.LOGGER.info("Hearthside seat {} is {}", index + 1, profile.name());
		return true;
	}

	/** Why a seat can't take this profile now, or null. */
	private String problemWith(int index, GuestProfile picked) {
		if (heldByAnotherSeat(index, picked.id())) {
			return picked.name() + " is playing";
		}
		if (picked.name().equalsIgnoreCase(minecraft.getUser().getName())) {
			return picked.name() + " is taken";
		}
		boolean saved = false;
		try {
			for (GuestProfile p : profileStore().load()) {
				if (p.id().equals(picked.id())) {
					saved = true;
				} else if (p.name().equalsIgnoreCase(picked.name())) {
					return picked.name() + " is taken";
				}
			}
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not read guest profiles", e);
		}
		for (Slot slot : slots.values()) {
			GuestProfile profile = slot.profile;
			if (slot.index != index && profile != null && !saved && profile.name().equalsIgnoreCase(picked.name())) {
				return picked.name() + " is taken";
			}
		}
		return null;
	}

	/** The world opened: seats still choosing go in with what their card shows. Render thread. */
	private void confirmWaitingCards() {
		for (Map.Entry<Integer, SeatCard> entry : List.copyOf(cards.entrySet())) {
			try {
				SeatCard card = entry.getValue();
				// What the card shows; if another seat took that meanwhile, a fresh profile with its own name.
				if (!confirmProfile(entry.getKey(), card.autoConfirm())) {
					confirmProfile(entry.getKey(), card.autoConfirmFresh());
				}
			} catch (RuntimeException e) {
				HearthsideMod.LOGGER.error("Could not settle seat {}'s profile", entry.getKey() + 1, e);
			}
		}
	}

	/** Saves a profile as the most recently used (profiles are stored least recent first). */
	private boolean rememberProfile(GuestProfile profile) {
		try {
			ProfileStore store = profileStore();
			List<GuestProfile> profiles = new ArrayList<>(store.load());
			profiles.removeIf(p -> p.id().equals(profile.id()));
			profiles.add(profile);
			store.save(profiles);
			return true;
		} catch (IOException | IllegalArgumentException e) {
			HearthsideMod.LOGGER.error("Could not save {}'s profile", profile.name(), e);
			return false;
		}
	}

	/** A guest's controller dropped: their seat is kept. Render thread. */
	void onPadLost(int player) {
		String controller = controllerOfSlot.get(player);
		if (controller != null) {
			apply(flow.disconnected(controller));
		}
	}

	/** A guest's controller came back under a new SDL id. Render thread. */
	void onPadRestored(int player, int newInstanceId) {
		String old = controllerOfSlot.get(player);
		String now = controllerId(newInstanceId);
		if (old != null && !old.equals(now)) {
			apply(flow.reassign(old, now));
			controllerOfSlot.put(player, now);
			instanceOf.put(now, newInstanceId);
			guidOf.put(now, guidOf.remove(old));
		} else if (old != null) {
			apply(flow.reconnected(old));
		}
	}

	/** Guests already recorded in this world's co-op entry. */
	private final java.util.Set<UUID> recordedInWorld = new java.util.HashSet<>();

	/** This world was played in co-op, by player 1 and every guest with a profile (the world picker shows it). */
	private void recordCoopWorld(WorldLink link) {
		List<String> names = new ArrayList<>();
		names.add(minecraft.getUser().getName());
		slots.values().forEach(s -> {
			if (s.profile != null) {
				names.add(s.profile.name());
			}
		});
		try {
			WorldsScreen.coopStore(gameDir).played(link.levelId(), System.currentTimeMillis(), names, link.openedAt());
		} catch (IOException e) {
			HearthsideMod.LOGGER.warn("Could not record the co-op world", e);
		}
	}

	/** Once a second: expire join prompts, offer take-overs, and record co-op worlds guests reached. Render thread. */
	// ---- What a guest's view shows (for ViewOverlay) ----

	/** How much a seat's card covers their view: 1 = only the card, 0 = only their game. */
	float cardCover(int seat, long now, boolean reduceMotion) {
		return phases.cardCover(seat, now, reduceMotion);
	}

	// ---- The party panel (three players) ----

	/** Where the party panel is drawn now (the free quarter with three players, in window pixels) and how opaque. */
	java.util.Optional<Views.PanelDraw> panelAt(long now, boolean reduceMotion) {
		return views.panelAt(now, reduceMotion);
	}

	/** How much of the party map has ground on it. For the E2E. */
	public float mapFilled() {
		return mapTexture.filled();
	}

	/** What the party panel shows this frame (and asks the sampler for its area). Render thread. */
	dev.hearthside.fabric.client.ui.PartyPanel.State panelState() {
		MapSampler.Snapshot snap = mapSampler.snapshot;
		String host = minecraft.getUser().getName();
		List<dev.hearthside.core.map.PartyMapView.Player> players = new ArrayList<>();
		Map<String, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> elsewhere = new HashMap<>();
		if (snap != null) {
			for (MapSampler.PlayerAt p : snap.players()) {
				if (!p.dimension().equals(snap.hostDimension())) {
					elsewhere.put(p.name(), p.dimension());
					continue;
				}
				var player = new dev.hearthside.core.map.PartyMapView.Player(p.name(), colourOfPlayer(p.name()), p.x(), p.z(), p.yaw());
				if (p.name().equals(host)) {
					players.addFirst(player);
				} else {
					players.add(player);
				}
			}
		}
		var view = dev.hearthside.core.map.PartyMapView.frame(players);
		mapSampler.want(view, snap != null ? snap.hostDimension() : net.minecraft.world.level.Level.OVERWORLD);
		mapTexture.drain(mapSampler.rows);
		var markers = players.stream().map(view::marker).toList();
		if (markers.size() != lastMarkerCount) {
			lastMarkerCount = markers.size();
			HearthsideMod.LOGGER.info("Hearthside party map markers={}", markers.size());
		}
		List<dev.hearthside.fabric.client.ui.PartyPanel.Seat> seats = new ArrayList<>();
		seats.add(new dev.hearthside.fabric.client.ui.PartyPanel.Seat(host, colorOf(0), placeOf(elsewhere.get(host))));
		new java.util.TreeMap<>(slots).forEach((index, slot) -> {
			String name = slot.profile != null ? slot.profile.name() : slot.displayName();
			String status = switch (viewPhase(index)) {
				case PICKING -> "picking…";
				case JOINING -> "joining…";
				case RECONNECTING -> "reconnecting…";
				case FAILED -> "couldn't start";
				case LIVE -> placeOf(elsewhere.get(name));
			};
			seats.add(new dev.hearthside.fabric.client.ui.PartyPanel.Seat(name, colorOf(index), status));
		});
		var weather = snap == null || !snap.raining() ? dev.hearthside.fabric.client.ui.PartyPanel.Weather.CLEAR
				: snap.thundering() ? dev.hearthside.fabric.client.ui.PartyPanel.Weather.THUNDER
				: dev.hearthside.fabric.client.ui.PartyPanel.Weather.RAIN;
		return new dev.hearthside.fabric.client.ui.PartyPanel.State(mapTexture.ready() ? PartyMapTexture.ID : null, markers,
				seats, snap == null ? 0 : snap.dayTime(), weather, freeControllers() > 0, joinStyle());
	}

	/** "in the Nether" or "in the End" for a player away from player 1's dimension, else null. */
	private static String placeOf(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
		if (dimension == null) {
			return null;
		}
		return dimension.equals(net.minecraft.world.level.Level.NETHER) ? "in the Nether"
				: dimension.equals(net.minecraft.world.level.Level.END) ? "in the End" : "elsewhere";
	}

	private int colourOfPlayer(String name) {
		if (name.equals(minecraft.getUser().getName())) {
			return colorOf(0);
		}
		for (Slot slot : slots.values()) {
			if (slot.profile != null && slot.profile.name().equalsIgnoreCase(name)) {
				return colorOf(slot.index);
			}
		}
		return 0x8A7A6A;
	}

	/** When the views were last laid out anew. */
	long layoutChangedAt() {
		return views.changedAt();
	}

	/** When a guest's game came into the world (their current phase's start). */
	long liveSince(int seat) {
		return phases.since(seat);
	}

	/** A player's name as their tag shows it: player 1's account name, or a guest's profile. */
	String nameOf(int seat) {
		if (seat == 0) {
			return minecraft.getUser().getName();
		}
		return who(seat).name();
	}

	/** What a seat's view shows now (for the E2E and the overlay). */
	public dev.hearthside.core.session.ViewPhase viewPhaseOf(int seat) {
		return viewPhase(seat);
	}

	/** A picking newcomer's card colour: quick setup's pick, else the profile on their carousel, else their seat's. */
	int pickerColour(int seat) {
		SeatCard card = drawnCard(seat);
		if (card != null && card.setup() != null) {
			return card.setup().color();
		}
		if (card != null && card.carousel().current() instanceof ProfileCarousel.Entry.Profile(GuestProfile p)) {
			return displayColor(seat, p.color());
		}
		return colorOf(seat);
	}

	/** A picking newcomer's skin, as their card shows it. */
	net.minecraft.world.entity.player.PlayerSkin pickerSkin(int seat) {
		SeatCard card = drawnCard(seat);
		dev.hearthside.core.profile.SkinRef skin = null;
		if (card != null && card.setup() != null) {
			skin = card.setup().skin();
		} else if (card != null && card.carousel().current() instanceof ProfileCarousel.Entry.Profile(GuestProfile p)) {
			skin = p.skin();
		}
		return LobbyScreen.skin(skin);
	}

	dev.hearthside.core.session.ViewPhase viewPhase(int seat) {
		return phases.phase(seat).orElse(dev.hearthside.core.session.ViewPhase.JOINING);
	}

	boolean joiningStuck(int seat, long now) {
		return phases.stuck(seat, now);
	}

	/** Whether a seat's card sits over their last picture (dimmed) rather than on the plank wall. For the E2E. */
	public boolean cardOverLastFrame(int seat) {
		return cardStatus(seat, System.nanoTime()).overLastFrame();
	}

	/** What a seat's card says while their game joins, restarts or gives up. */
	dev.hearthside.fabric.client.ui.CardPainter.Status cardStatus(int seat, long now) {
		boolean stuck = phases.stuck(seat, now);
		return switch (viewPhase(seat)) {
			case RECONNECTING -> stuck ? dev.hearthside.fabric.client.ui.CardPainter.Status.STILL_RECONNECTING
					: dev.hearthside.fabric.client.ui.CardPainter.Status.RECONNECTING;
			case FAILED -> phases.overLastFrame(seat) ? dev.hearthside.fabric.client.ui.CardPainter.Status.COULDNT_START
					: dev.hearthside.fabric.client.ui.CardPainter.Status.COULDNT_START_JOINING;
			case PICKING, JOINING, LIVE -> stuck ? dev.hearthside.fabric.client.ui.CardPainter.Status.STILL_JOINING
					: dev.hearthside.fabric.client.ui.CardPainter.Status.JOINING;
		};
	}

	/** Who a seat is, for their card: name, skin, colour and controller family. */
	dev.hearthside.fabric.client.ui.CardPainter.Who who(int seat) {
		Leaving gone = gone(seat);
		return gone != null ? gone.who() : who(seat, slots.get(seat));
	}

	private dev.hearthside.fabric.client.ui.CardPainter.Who who(int seat, Slot slot) {
		GuestProfile profile = slot == null ? null : slot.profile;
		String name = profile != null ? profile.name() : slot != null ? slot.displayName() : "Player " + (seat + 1);
		return new dev.hearthside.fabric.client.ui.CardPainter.Who(name, LobbyScreen.skin(profile == null ? null : profile.skin()),
				colorOf(seat), padStyleOf(seat));
	}

	/** A guest's game says it is in the world (past its loading screens). Render thread. */
	private void guestInWorld(int index) {
		HearthsideMod.LOGGER.info("Hearthside seat {} in world", index);
		phases.inWorld(index, System.nanoTime());
		holdLastFrame(index, false); // a restarted game's own frames now (its card crossfades into them)
		String controller = controllerOfSlot.get(index);
		if (controller != null) {
			apply(flow.loaded(controller));
		}
	}

	void tickFlow() {
		WorldLink link = world;
		controllerOfSlot.forEach((index, controller) -> {
			Slot slot = slots.get(index);
			GuestProfile profile = slot == null ? null : slot.profile;
			if (link != null && profile != null && link.hasPlayer(profile.name())) {
				if (recordedInWorld.add(profile.id())) {
					recordCoopWorld(link);
				}
				if (!composite) {
					// With tiled windows no game reports being in the world (that comes with its frames): the server
					// having them is enough.
					apply(flow.loaded(controller));
				}
			}
		});
		apply(flow.tick());
	}

	private void apply(JoinFlow.Result result) {
		for (JoinFlow.Effect effect : result.effects()) {
			switch (effect) {
				case JoinFlow.Effect.ShowJoinPrompt prompt -> joinToast.show(prompt.controller(),
						styleOfController.getOrDefault(prompt.controller(), PadStyle.GENERIC), System.nanoTime(),
						JoinFlow.CONFIRM_WINDOW, hostReduceMotion);
				case JoinFlow.Effect.OpenProfilePicker pick -> {
					// The player picks on their card, which opens once their seat's game starts: on the lobby's card, or
					// (dropping in) inside their own new view while everyone else plays on.
					pendingCards.add(pick.controller());
				}
				case JoinFlow.Effect.StartGuestGame start -> startForController(start.controller());
				case JoinFlow.Effect.StopGuestGame stop -> {
					Integer index = slotOfController(stop.controller());
					Slot slot = index == null ? null : slots.get(index);
					controllerOfSlot.values().remove(stop.controller());
					if (slot != null) {
						endSlot(slot);
						removeSlot(slot);
					}
				}
				case JoinFlow.Effect.OfferTakeover offer -> chat(Component.literal(
						"A player's controller is still disconnected. Plug it back in to carry on.")
						.withStyle(ChatFormatting.YELLOW));
				case JoinFlow.Effect.HideJoinPrompt hide -> joinToast.hide(hide.controller(), System.nanoTime());
				// A crackle as the fire takes another player.
				case JoinFlow.Effect.ClaimSeat claim -> dev.hearthside.fabric.client.ui.HearthSounds.play(
						dev.hearthside.fabric.client.ui.HearthSounds.Ui.JOIN);
				// Not wired yet: take-over (plan 5c2a Task 4) and Add player (Task 5).
				case JoinFlow.Effect.TookOver took -> { }
				case JoinFlow.Effect.ShowAddPlayerPrompt show -> { }
				case JoinFlow.Effect.HideAddPlayerPrompt hide -> { }
			}
		}
	}

	private void startForController(String controller) {
		boolean card = pendingCards.remove(controller);
		GuestProfile profile = null;
		if (world != null && !composite) {
			// Tiled windows: nothing draws a card over a guest's own window, so they come in as their controller's
			// profile straight away (as before Plan 5c1).
			card = false;
			profile = profileFor(guidOf.get(controller));
		}
		Integer instanceId = instanceOf.get(controller);
		if ((profile == null && !card) || instanceId == null) {
			apply(flow.leave(controller));
			return;
		}
		try {
			int index = startGuest(profile);
			controllerOfSlot.put(index, controller);
			if (profile != null) {
				apply(flow.profileChosen(controller, profile.id()));
			}
			if (card) {
				SeatCard newCard = newCard(index, controller, null);
				cards.put(index, newCard);
				if (world != null) {
					// Mid-game, More's screens would take over player 1's view: the card offers just the profiles.
					newCard.noMore();
					// Their view shows this card (the layout may already have run, inside startGuest, before it existed).
					phases.picking(index, System.nanoTime());
				}
			}
			HostInput currentInput = input;
			if (currentInput != null) {
				currentInput.assignPad(instanceId, index);
			}
		} catch (Exception e) {
			HearthsideMod.LOGGER.error("Could not add a player for {}", controller, e);
			chat(Component.literal("Couldn't add a player: " + e.getMessage()).withStyle(ChatFormatting.RED));
			apply(flow.leave(controller));
		}
	}

	private Integer slotOfController(String controller) {
		return controllerOfSlot.entrySet().stream().filter(e -> e.getValue().equals(controller))
				.map(Map.Entry::getKey).findFirst().orElse(null);
	}

	/** The profile last used on this controller, else "Player N" (made if needed); remembers the controller. */
	private GuestProfile profileFor(String guid) {
		try {
			ProfileStore store = profileStore();
			List<GuestProfile> profiles = new ArrayList<>(store.load());
			java.util.Set<String> playing = new java.util.HashSet<>();
			slots.values().forEach(s -> {
				if (s.profile != null) {
					playing.add(s.profile.name().toLowerCase(Locale.ROOT));
				}
			});
			// The most recently used match: profiles are stored least recent first.
			for (GuestProfile p : profiles.reversed()) {
				if (guid != null && guid.equals(p.preferredControllerGuid()) && !playing.contains(p.name().toLowerCase(Locale.ROOT))) {
					return p;
				}
			}
			int index = nextFreeIndex();
			for (String prefix : new String[] {"Player", "Guest"}) {
				String name = prefix + (index + 1);
				if (!name.equalsIgnoreCase(minecraft.getUser().getName()) && !playing.contains(name.toLowerCase(Locale.ROOT))) {
					GuestProfile profile = loadOrCreateProfile(name);
					if (guid != null && !guid.equals(profile.preferredControllerGuid())) {
						List<GuestProfile> updated = new ArrayList<>(store.load());
						updated.replaceAll(p -> p.id().equals(profile.id()) ? p.withController(guid) : p);
						store.save(updated);
					}
					return profile;
				}
			}
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not read guest profiles", e);
		}
		return null;
	}

	private void chat(Component message) {
		minecraft.gui.chatListener().handleSystemMessage(message, false);
	}

	/** A guest's seat is free again. In the world their view fades out where it is, then the others glide. */
	private void removeSlot(Slot slot) {
		if (slots.get(slot.index) != slot) {
			return; // already gone
		}
		// Who they were, while their view fades: read while the seat still has its profile, colour and controller.
		dev.hearthside.fabric.client.ui.CardPainter.Who who = who(slot.index, slot);
		slots.remove(slot.index, slot);
		SeatCard card = cards.remove(slot.index);
		String controller = controllerOfSlot.remove(slot.index);
		if (controller != null) {
			minecraft.execute(() -> flow.leave(controller));
		}
		WorldLink link = world;
		if (link != null) {
			link.remove(slot.index);
		}
		minecraft.execute(() -> {
			// A snuff as their light goes out.
			dev.hearthside.fabric.client.ui.HearthSounds.play(dev.hearthside.fabric.client.ui.HearthSounds.Ui.LEAVE);
			if (input != null) {
				input.release(slot.index);
			}
			if (slots.containsKey(slot.index)) {
				afterGuestsChanged(); // someone new has the seat already
			} else if (!fadeOut(slot.index, new Leaving(who, card))) {
				phases.remove(slot.index);
				afterGuestsChanged();
			}
		});
	}

	/**
	 * Fades a leaving guest's view out where it is, looking just as it did, while everyone else plays on; the others
	 * glide into its space once it has gone ({@link #frame}). False when there is no view to fade (no world, tiled
	 * windows, reduce motion). Render thread.
	 */
	private boolean fadeOut(int seat, Leaving who) {
		if (!composite || world == null || hostReduceMotion || Compositor.active() == null || !views.seats().contains(seat)) {
			return false;
		}
		long now = System.nanoTime();
		leaving.put(seat, who);
		views.leave(seat, now);
		relayoutAfterFade = true;
		leaveAt = now;
		return true;
	}

	/**
	 * Each frame: once a leaving view has faded out, the others glide into its space (with nobody left, player 1's view
	 * glides back to the whole window); once they settle, split-screen ends if nobody is left. Render thread.
	 */
	void frame(long now) {
		if (relayoutAfterFade && !views.fading(now)) {
			relayoutAfterFade = false;
			for (Integer seat : List.copyOf(leaving.keySet())) {
				if (!slots.containsKey(seat)) {
					leaving.remove(seat);
					phases.remove(seat);
				}
			}
			settling = true;
			afterGuestsChanged();
		} else if (settling && !relayoutAfterFade && !views.moving(now)) {
			settling = false;
			HearthsideMod.LOGGER.info("Hearthside layout settled {} ms after a leave", (now - leaveAt) / 1_000_000L);
			if (slots.isEmpty()) {
				leaveSplitScreen();
			}
		}
	}

	/** B on a card stuck joining: that player gives up their seat. Render thread. */
	void cancelJoin(int seat) {
		Slot slot = slots.get(seat);
		if (slot != null) {
			HearthsideMod.LOGGER.info("Hearthside seat {} gave up joining", seat);
			endSlot(slot);
			removeSlot(slot);
		}
	}

	private void stopAllSatellites() {
		for (Slot slot : slots.values()) {
			slot.supervisor.stop();
		}
	}

	private HelloVerdict verifyHello(ControlMessage.Hello hello) {
		Slot slot = slots.get(hello.playerIndex());
		String expected = slot == null ? null : slot.secret;
		boolean valid = hello.protocolVersion() == ControlCodec.PROTOCOL_VERSION
				&& expected != null
				&& MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
						hello.launchSecret().getBytes(StandardCharsets.UTF_8));
		if (!valid) {
			HearthsideMod.LOGGER.warn("Rejected a control connection claiming player slot {}", hello.playerIndex());
			return new HelloVerdict(new ControlMessage.HelloAck(ControlCodec.PROTOCOL_VERSION, false, "rejected", ""), null);
		}
		// The login token travels in JoinWorld, once a world is open.
		return new HelloVerdict(new ControlMessage.HelloAck(ControlCodec.PROTOCOL_VERSION, true, "", ""),
				connection -> {
					slot.connection = connection;
					connection.onMessage(message -> {
						if (message instanceof ControlMessage.InWorld) {
							minecraft.execute(() -> {
								if (slots.get(slot.index) == slot) {
									guestInWorld(slot.index);
								}
							});
						} else if (message instanceof ControlMessage.LeftWorld) {
							minecraft.execute(() -> {
								HearthsideMod.LOGGER.info("Hearthside seat {} left its world", slot.index);
								// Left from their own game (its pause menu): the player leaves the party.
								if (slots.get(slot.index) == slot && world != null) {
									endSlot(slot);
									removeSlot(slot);
								}
							});
						} else if (message instanceof ControlMessage.FrameSlotsReady ready) {
							slot.announcedGeneration = ready.generation();
							minecraft.execute(() -> {
								Compositor compositor = Compositor.active();
								if (compositor != null && slots.get(slot.index) == slot) {
									compositor.onGuestSlots(slot.index, ready, slot.announcedGeneration);
								}
							});
						}
					});
					connection.onClose(() -> {
						if (slot.connection == connection) {
							slot.connection = null;
						}
					});
					GuestProfile profile = slot.profile;
					send(connection, new ControlMessage.SetControls(ControlSchemeJson.toJson(
							profile != null ? profile.controls() : ControlScheme.BEDROCK)));
					HostInput currentInput = input;
					send(connection, new ControlMessage.SetPadStyle(
							currentInput == null ? PadStyle.GENERIC : currentInput.styleFor(slot.index)));
					WorldLink link = world;
					if (link != null) {
						sendIn(link, slot, connection);
					}
					minecraft.execute(this::afterGuestsChanged);
				});
	}

	/** Remembers the host window and focus setting before the first guest arrives. Render thread. */
	private void enterSplitScreen() {
		if (splitScreen) {
			return;
		}
		splitScreen = true;
		hostWindowBefore = WindowPlacer.bounds(minecraft.getWindow());
		pauseOnLostFocusBefore = minecraft.options.pauseOnLostFocus;
		// Other players keep playing while this window is in the background; the host must not pause because of it.
		minecraft.options.pauseOnLostFocus = false;
		if (config.presetForPlayer1()) {
			// Player 1's game takes the preset too, for as long as the screen is shared (never its render scale: plan R4).
			SessionOptions.applyForPlayer1(minecraft.options, tuning());
		}
	}

	/** How each guest's view reaches the screen, for its launch command. */
	private SatelliteLaunch.Presentation presentation() {
		if (!composite) {
			return SatelliteLaunch.Presentation.TILED;
		}
		return Presentation.forceCopy() || hostTransport == Transport.COPY
				? SatelliteLaunch.Presentation.COMPOSITED_COPY
				: SatelliteLaunch.Presentation.COMPOSITED;
	}

	/** Puts the host window and focus setting back once no guests remain. Render thread. */
	private void leaveSplitScreen() {
		if (!splitScreen) {
			return;
		}
		splitScreen = false;
		views = new Views(config.layout());
		phases = new ViewPhases();
		leaving.clear();
		relayoutAfterFade = false;
		settling = false;
		mapTexture.close();
		OverlayPass.unregister(viewOverlay);
		if (Compositor.active() != null) {
			Compositor.deactivate(minecraft);
		} else {
			WindowPlacer.restore(minecraft.getWindow(), hostWindowBefore);
		}
		// Minecraft's own GUI scale again, even where no compositor was left to clear the party's.
		boolean guiScaleForced = GuiScaleOverride.forced() != 0;
		GuiScaleOverride.clear();
		if (guiScaleForced) {
			minecraft.resizeGui();
		}
		minecraft.options.pauseOnLostFocus = pauseOnLostFocusBefore;
		SessionOptions.restore(minecraft.options);
	}

	/**
	 * The party's GUI scale for the views now laid out (Spec D section 3.4): Minecraft's rule for each view's size at
	 * player 1's setting, the smallest of them. Player 1's game takes it from its next GUI layout. Render thread.
	 */
	private int partyGuiScale(List<Slot> guests) {
		int setting = minecraft.options.guiScale().get();
		List<Rect> rects = new ArrayList<>();
		views.target(0).ifPresent(rects::add);
		for (Slot guest : guests) {
			views.target(guest.index).ifPresent(rects::add);
		}
		guiScaleSetting = setting;
		if (rects.isEmpty()) {
			GuiScaleOverride.set(0);
			return 0;
		}
		// SetViewport carries at most 32 (a view over 10,000 pixels wide).
		int scale = Math.min(32, GuiScaleRule.partyScale(setting, rects));
		GuiScaleOverride.set(scale);
		return scale;
	}

	/**
	 * Lays player 1's GUI out at the party's scale, if it isn't already, and sends every guest its view, GUI scale and
	 * frame size. Render thread.
	 */
	private void sendViewports(List<Slot> guests, int partyScale, int refresh) {
		Window window = minecraft.getWindow();
		if (partyScale > 0 && window.getGuiScale() != partyScale) {
			minecraft.resizeGui();
		}
		views.target(0).ifPresent(host -> GuiScaleOverride.log(1, window, host.width(), host.height()));
		// Guests only, even when player 1 takes the preset (plan R4).
		boolean reducedResolution = tuning().reducedResolution();
		for (Slot guest : guests) {
			ControlConnection connection = guest.connection;
			Rect target = views.target(guest.index).orElse(null);
			if (connection == null || target == null) {
				continue;
			}
			ControlMessage.SetViewport viewport = partyScale < 1 ? new ControlMessage.SetViewport(target, refresh)
					: viewport(target, refresh, GuiScaleRule.forView(target, partyScale, reducedResolution));
			views.sentFrame(guest.index, new Rect(0, 0, viewport.frameWidth(), viewport.frameHeight()));
			send(connection, viewport);
		}
	}

	private static ControlMessage.SetViewport viewport(Rect target, int refresh, GuiScaleRule.ViewScale scale) {
		return new ControlMessage.SetViewport(target, refresh, scale.guiScale(), scale.frameWidth(), scale.frameHeight());
	}

	/**
	 * Player 1 changed their GUI-scale setting while compositing: every view follows (until then the forced party
	 * scale keeps player 1's GUI as it was). Polled each client tick. Render thread.
	 */
	private void followGuiScaleSetting() {
		if (!composite || world == null || views == null || Compositor.active() == null || guiScaleSetting < 0
				|| minecraft.options.guiScale().get() == guiScaleSetting) {
			return;
		}
		List<Slot> guests = new ArrayList<>(slots.values());
		sendViewports(guests, partyGuiScale(guests), WindowPlacer.refreshHz(minecraft.getWindow()));
	}

	/** Re-lays out the views, or leaves split-screen mode when the last guest has gone. Render thread. */
	private void afterGuestsChanged() {
		List<Slot> guests = new ArrayList<>(slots.values());
		if (world == null) {
			// In the lobby guests' games wait hidden; views are laid out once a world is open.
			if (Compositor.active() != null) {
				Compositor.deactivate(minecraft);
			}
			return;
		}
		if (guests.isEmpty() && !(composite && settling && Compositor.active() != null)) {
			leaveSplitScreen();
			return;
		}
		// (After the last guest's view has faded out, player 1's glides back to the whole window before split-screen ends.)
		int refresh = WindowPlacer.refreshHz(minecraft.getWindow());
		if (composite) {
			// One window: lay every player out inside this window's framebuffer; the window itself stays put.
			// Spec A's layouts; views glide to them (or jump, when the window itself changed size).
			Window.FramebufferSize size = minecraft.getWindow().queryFramebufferSize();
			long now = System.nanoTime();
			views.update(guests.stream().map(g -> g.index).toList(), new Rect(0, 0, size.width(), size.height()), now,
					hostReduceMotion);
			for (Slot guest : guests) {
				if (phases.phase(guest.index).isEmpty()) {
					// Their card shows until their game says it is in the world, never its loading screens.
					if (cards.containsKey(guest.index)) {
						phases.picking(guest.index, now);
					} else {
						phases.joining(guest.index, now);
					}
				}
			}
			// Player 1's GUI scale first, so their view is laid out once, at its new size and the party's scale.
			int partyScale = partyGuiScale(guests);
			Compositor.activate(minecraft, this::afterGuestsChanged,
					index -> sendToGuest(index, new ControlMessage.UseCopyTransport()), views, size.width(), size.height());
			OverlayPass.register(viewOverlay);
			sendViewports(guests, partyScale, refresh);
			return;
		}
		if (workArea == null) {
			workArea = WindowPlacer.primaryWorkArea();
		}
		// Real windows: two players side by side or stacked as configured; three always a wide top (no panel to draw).
		TwoPlayerSplit two = config.layout().two() == LayoutSettings.TwoPlayer.STACKED
				? TwoPlayerSplit.HORIZONTAL : TwoPlayerSplit.VERTICAL;
		List<Rect> rects = new ConsoleLayout(two).layout(1 + guests.size(), workArea);
		WindowPlacer.place(minecraft.getWindow(), rects.get(0));
		for (int i = 0; i < guests.size(); i++) {
			ControlConnection connection = guests.get(i).connection;
			if (connection != null) {
				send(connection, new ControlMessage.SetViewport(rects.get(i + 1), refresh));
			}
		}
		WindowPlacer.focus(minecraft.getWindow());
	}

	private GuestProfile loadOrCreateProfile(String name) throws IOException {
		ProfileStore store = profileStore();
		List<GuestProfile> profiles = new ArrayList<>(store.load());
		for (GuestProfile p : profiles) {
			if (p.name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
				return p;
			}
		}
		List<Integer> taken = new ArrayList<>(profiles.stream().map(GuestProfile::color).toList());
		taken.add(store.loadFile().host().color());
		GuestProfile created = new GuestProfile(UUID.randomUUID(), name, SkinRef.defaultSkin("steve"), null)
				.withColor(PlayerColors.firstFree(taken));
		profiles.add(created);
		store.save(profiles);
		return created;
	}

	private static void send(ControlConnection connection, ControlMessage message) {
		try {
			connection.send(message);
		} catch (IOException e) {
			HearthsideMod.LOGGER.warn("Could not send {} to a satellite: {}", message, e.toString());
		}
	}

	private static String newSecret() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	private final class SlotListener implements SatelliteSupervisor.Listener {
		private final Slot slot;

		SlotListener(Slot slot) {
			this.slot = slot;
		}

		@Override
		public void started(RunningProcess process) {
			HearthsideMod.LOGGER.info("Started satellite for {} (player {}, pid {})", slot.displayName(), slot.index + 1, process.pid());
		}

		@Override
		public void restarting(int consecutiveFailures, Duration delay) {
			HearthsideMod.LOGGER.warn("Satellite for {} stopped; restarting in {}s", slot.displayName(), delay.toSeconds());
			minecraft.execute(() -> {
				if (slots.get(slot.index) == slot) {
					// Their card comes back over the last picture from their game, which stays until the new one is in (a
					// player still joining keeps their card as it is).
					phases.reconnecting(slot.index, System.nanoTime());
					holdLastFrame(slot.index, true);
				}
			});
			chat(Component.literal(slot.displayName() + "'s game stopped; restarting in " + delay.toSeconds() + "s")
					.withStyle(ChatFormatting.YELLOW));
		}

		@Override
		public void gaveUp(int exitCode) {
			HearthsideMod.LOGGER.error("Satellite for {} keeps crashing (exit {}); giving up", slot.displayName(), exitCode);
			chat(Component.literal(slot.displayName() + "'s game keeps crashing. See hearthside/satellite-" + slot.index
					+ "/logs for details.").withStyle(ChatFormatting.RED));
			minecraft.execute(() -> {
				if (slots.get(slot.index) == slot) {
					HearthsideMod.LOGGER.info("Hearthside seat {} couldn't start", slot.index);
					phases.failed(slot.index, System.nanoTime());
					holdLastFrame(slot.index, true);
				}
			});
			// Their card says so for a moment; then their view fades out.
			scheduler.schedule(() -> minecraft.execute(() -> removeSlot(slot)), FAILED_SHOWN.toMillis(), TimeUnit.MILLISECONDS);
		}

		@Override
		public void ended() {
			HearthsideMod.LOGGER.info("{} closed their game window", slot.displayName());
			chat(Component.literal(slot.displayName() + " left."));
			minecraft.execute(() -> removeSlot(slot));
		}

		private void chat(Component message) {
			minecraft.execute(() -> minecraft.gui.chatListener().handleSystemMessage(message, false));
		}
	}
}
