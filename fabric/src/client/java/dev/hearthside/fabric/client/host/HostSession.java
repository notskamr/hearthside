package dev.hearthside.fabric.client.host;

import dev.hearthside.core.auth.GuestTokenAuthority;
import dev.hearthside.core.channel.ControlConnection;
import dev.hearthside.core.channel.ControlServer;
import dev.hearthside.core.channel.HelloVerdict;
import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.ControlSchemeJson;
import dev.hearthside.core.input.GamepadSource;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.launch.HostJvm;
import dev.hearthside.core.launch.LaunchCommandBuilder;
import dev.hearthside.core.launch.SatelliteLaunch;
import dev.hearthside.core.launch.UnsupportedLauncherException;
import dev.hearthside.core.layout.ConsoleLayout;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.TwoPlayerSplit;
import dev.hearthside.core.process.JdkProcessLauncher;
import dev.hearthside.core.process.RunningProcess;
import dev.hearthside.core.process.SatelliteSupervisor;
import dev.hearthside.core.profile.GuestNames;
import dev.hearthside.core.profile.GuestProfile;
import dev.hearthside.core.profile.GuestUuids;
import dev.hearthside.core.profile.PlayerColors;
import dev.hearthside.core.profile.ProfileStore;
import dev.hearthside.core.profile.SkinRef;
import dev.hearthside.core.protocol.ControlCodec;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.core.protocol.LifecycleCommand;
import dev.hearthside.core.session.GuestRoster;
import dev.hearthside.core.session.SatelliteDirectories;
import dev.hearthside.core.supervise.RestartPolicy;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.dev.FakeGamepadSource;
import dev.hearthside.fabric.client.input.FrameHook;
import dev.hearthside.fabric.client.input.SdlGamepadSource;
import dev.hearthside.core.compositor.Transport;
import dev.hearthside.core.compositor.TransportChoice;
import dev.hearthside.fabric.client.compositor.Compositor;
import dev.hearthside.fabric.client.compositor.GlInterop;
import dev.hearthside.fabric.client.compositor.Presentation;
import dev.hearthside.fabric.client.window.WindowPlacer;
import dev.hearthside.fabric.server.HostServerBridge;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
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
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.HttpUtil;

/**
 * One split-screen session, alive while the host has a singleplayer world open: the guest roster,
 * the control channel, one supervisor per satellite and, once the first guest joins, a loopback-only
 * game listener for guests. The host's window and pause-on-focus-loss setting are changed only while
 * at least one guest is present.
 */
public final class HostSession {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static volatile HostSession current;

	private final Minecraft minecraft;
	private final MinecraftServer server;
	private final Path gameDir;
	private final GuestTokenAuthority tokens = GuestTokenAuthority.withRandomSecret(Clock.systemUTC());
	private final GuestRoster roster;
	private final ControlServer controlServer;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
			Thread.ofPlatform().daemon().name("hearthside-restarts").factory());
	private final Map<Integer, Slot> slots = new ConcurrentSkipListMap<>();
	private final Thread shutdownHook = new Thread(this::stopAllSatellites, "hearthside-shutdown");
	/** Loopback port guests join on; 0 until the first guest needs it. */
	private volatile int gamePort;
	/** The controller loop; created and used on the render thread. */
	private volatile HostInput input;

	// Render-thread state for split-screen mode.
	private boolean splitScreen;
	private Rect hostWindowBefore;
	private boolean pauseOnLostFocusBefore;
	private Rect workArea;
	/** Whether guests render hidden and this window composites them (decided on the render thread at session start). */
	private volatile boolean composite;
	/** GPU sharing or copying, as this machine's probe decided at session start. */
	private volatile Transport hostTransport = Transport.COPY;

	private static final class Slot {
		final int index;
		final GuestProfile profile;
		volatile String secret;
		volatile SatelliteSupervisor supervisor;
		volatile ControlConnection connection;

		Slot(int index, GuestProfile profile) {
			this.index = index;
			this.profile = profile;
		}
	}

	private HostSession(Minecraft minecraft, MinecraftServer server) throws IOException {
		this.minecraft = minecraft;
		this.server = server;
		this.gameDir = minecraft.gameDirectory.toPath().toAbsolutePath();
		this.roster = new GuestRoster(server.getSingleplayerProfile().name());
		this.controlServer = ControlServer.start(this::verifyHello);
		HostServerBridge.activate(new HostServerBridge.Active(roster, tokens, this::reopenGuestListener));
		Runtime.getRuntime().addShutdownHook(shutdownHook);
		minecraft.execute(this::choosePresentation);
		minecraft.execute(this::startInput);
		HearthsideMod.LOGGER.info("Hearthside session open: control channel on {}", controlServer.port());
	}

	/** Composite into this window when the GL context supports it; otherwise tile real windows. Render thread. */
	private void choosePresentation() {
		try {
			composite = Presentation.glSupportsCompositing();
			if (composite) {
				// Decided once per session here, where the window is; guests follow it (Spec B section 3.5).
				hostTransport = TransportChoice.choose(GlInterop.probe(false));
			}
		} catch (RuntimeException | LinkageError e) {
			composite = false; // no GL context (for example Minecraft's Vulkan backend)
		}
		HearthsideMod.LOGGER.info("Hearthside split-screen presentation: {}", composite ? "one window" : "tiled windows");
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
			hostScheme = profileStore().loadFile().hostControls();
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

	/** Lowest free guest slot, or -1. */
	public int nextFreeIndex() {
		return roster.freeIndex().orElse(-1);
	}

	/** {@code /hearthside pad}: the next controller to press A becomes the host's. Render thread. */
	public void armHostClaim() {
		if (input == null) {
			throw new IllegalStateException("Controllers are unavailable");
		}
		input.armClaim();
	}

	void sendToGuest(int index, ControlMessage message) {
		Slot slot = slots.get(index);
		ControlConnection connection = slot == null ? null : slot.connection;
		if (connection != null) {
			send(connection, message);
		}
	}

	private ProfileStore profileStore() {
		return new ProfileStore(gameDir.resolve("hearthside").resolve("profiles.json"));
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (server instanceof IntegratedServer && current == null) {
				try {
					current = new HostSession(Minecraft.getInstance(), server);
				} catch (IOException e) {
					HearthsideMod.LOGGER.error("Could not open a Hearthside session", e);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HostSession session = current;
			current = null;
			if (session != null) {
				session.close();
			}
		});
	}

	public static HostSession current() {
		return current;
	}

	/** Adds a guest and starts their satellite; returns their player index. Runs on the render thread. */
	public int join(String name) throws IOException, UnsupportedLauncherException {
		GuestNames.requireValid(name);
		HostJvm host = HostJvm.capture(HostLaunchFacts.read());
		ensureGuestListener();
		GuestProfile profile = loadOrCreateProfile(name);
		int index = roster.freeIndex().orElseThrow(() -> new IllegalStateException("All 3 guest slots are taken"));
		roster.register(index, profile);

		Slot slot = new Slot(index, profile);
		Path satelliteDir = gameDir.resolve("hearthside").resolve("satellite-" + index);
		slot.supervisor = new SatelliteSupervisor(new JdkProcessLauncher(), () -> {
			slot.secret = newSecret();
			try {
				Path dir = SatelliteDirectories.prepare(gameDir, index);
				return LaunchCommandBuilder.build(host,
						new SatelliteLaunch(index, controlServer.port(), dir, profile.name(), slot.secret, presentation()));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}, satelliteDir.resolve("logs").resolve("hearthside-launch.log"), RestartPolicy.DEFAULT,
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

	/** Removes a guest and closes their satellite. Runs on the render thread. */
	public void leave(String name) {
		Slot slot = slots.values().stream()
				.filter(s -> s.profile.name().equalsIgnoreCase(name))
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
		HostServerBridge.deactivate();
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
			leaveSplitScreen();
		});
		HearthsideMod.LOGGER.info("Hearthside session closed");
	}

	private void ensureGuestListener() throws IOException {
		if (gamePort == 0) {
			int port = HttpUtil.getAvailablePort();
			server.getConnection().startTcpServerListener(InetAddress.getLoopbackAddress(), port);
			gamePort = port;
			HearthsideMod.LOGGER.info("Hearthside guests join on 127.0.0.1:{}", port);
		}
	}

	/** Vanilla's Stop Sharing closed every TCP listener, including the guests'; open it again. */
	private void reopenGuestListener() {
		int port = gamePort;
		if (port == 0) {
			return;
		}
		try {
			server.getConnection().startTcpServerListener(InetAddress.getLoopbackAddress(), port);
			HearthsideMod.LOGGER.info("Reopened the Hearthside guest listener on 127.0.0.1:{}", port);
		} catch (IOException e) {
			gamePort = 0;
			HearthsideMod.LOGGER.error("Could not reopen the Hearthside guest listener", e);
		}
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

	private void removeSlot(Slot slot) {
		slots.remove(slot.index, slot);
		roster.remove(slot.index);
		minecraft.execute(() -> {
			if (input != null) {
				input.release(slot.index);
			}
			afterGuestsChanged();
		});
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
		UUID guest = GuestUuids.forProfile(slot.profile.id());
		String token = tokens.issue(guest);
		return new HelloVerdict(new ControlMessage.HelloAck(ControlCodec.PROTOCOL_VERSION, true, "", token),
				connection -> {
					slot.connection = connection;
					connection.onMessage(message -> {
						if (message instanceof ControlMessage.FrameSlotsReady ready) {
							minecraft.execute(() -> {
								Compositor compositor = Compositor.active();
								if (compositor != null) {
									compositor.onGuestSlots(slot.index, ready);
								}
							});
						}
					});
					connection.onClose(() -> {
						if (slot.connection == connection) {
							slot.connection = null;
						}
					});
					send(connection, new ControlMessage.SetControls(ControlSchemeJson.toJson(slot.profile.controls())));
					HostInput currentInput = input;
					send(connection, new ControlMessage.SetPadStyle(
							currentInput == null ? PadStyle.GENERIC : currentInput.styleFor(slot.index)));
					send(connection, new ControlMessage.JoinWorld(gamePort, token, slot.profile.name()));
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
		if (Compositor.active() != null) {
			Compositor.deactivate(minecraft);
		} else {
			WindowPlacer.restore(minecraft.getWindow(), hostWindowBefore);
		}
		minecraft.options.pauseOnLostFocus = pauseOnLostFocusBefore;
	}

	/** Re-lays out the views, or leaves split-screen mode when the last guest has gone. Render thread. */
	private void afterGuestsChanged() {
		List<Slot> guests = new ArrayList<>(slots.values());
		if (guests.isEmpty()) {
			leaveSplitScreen();
			return;
		}
		int refresh = WindowPlacer.refreshHz(minecraft.getWindow());
		if (composite) {
			// One window: lay every player out inside this window's framebuffer; the window itself stays put.
			Window.FramebufferSize size = minecraft.getWindow().queryFramebufferSize();
			List<Rect> rects = new ConsoleLayout(TwoPlayerSplit.VERTICAL)
					.layout(1 + guests.size(), new Rect(0, 0, size.width(), size.height()));
			Map<Integer, Rect> guestViews = new HashMap<>();
			for (int i = 0; i < guests.size(); i++) {
				guestViews.put(guests.get(i).index, rects.get(i + 1));
			}
			Compositor.activate(minecraft, this::afterGuestsChanged,
					index -> sendToGuest(index, new ControlMessage.UseCopyTransport()), rects.get(0), guestViews, size.width(),
					size.height());
			for (int i = 0; i < guests.size(); i++) {
				ControlConnection connection = guests.get(i).connection;
				if (connection != null) {
					send(connection, new ControlMessage.SetViewport(rects.get(i + 1), refresh));
				}
			}
			return;
		}
		if (workArea == null) {
			workArea = WindowPlacer.primaryWorkArea();
		}
		List<Rect> rects = new ConsoleLayout(TwoPlayerSplit.VERTICAL).layout(1 + guests.size(), workArea);
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
			HearthsideMod.LOGGER.info("Started satellite for {} (player {}, pid {})", slot.profile.name(), slot.index + 1, process.pid());
		}

		@Override
		public void restarting(int consecutiveFailures, Duration delay) {
			HearthsideMod.LOGGER.warn("Satellite for {} stopped; restarting in {}s", slot.profile.name(), delay.toSeconds());
			chat(Component.literal(slot.profile.name() + "'s game stopped; restarting in " + delay.toSeconds() + "s")
					.withStyle(ChatFormatting.YELLOW));
		}

		@Override
		public void gaveUp(int exitCode) {
			HearthsideMod.LOGGER.error("Satellite for {} keeps crashing (exit {}); giving up", slot.profile.name(), exitCode);
			chat(Component.literal(slot.profile.name() + "'s game keeps crashing. See hearthside/satellite-" + slot.index
					+ "/logs for details.").withStyle(ChatFormatting.RED));
		}

		@Override
		public void ended() {
			HearthsideMod.LOGGER.info("{} closed their game window", slot.profile.name());
			chat(Component.literal(slot.profile.name() + " left."));
			removeSlot(slot);
		}

		private void chat(Component message) {
			minecraft.execute(() -> minecraft.gui.chatListener().handleSystemMessage(message, false));
		}
	}
}
