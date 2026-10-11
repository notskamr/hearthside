package dev.hearthside.fabric.client.host;

import dev.hearthside.core.auth.GuestTokenAuthority;
import dev.hearthside.core.channel.ControlConnection;
import dev.hearthside.core.channel.ControlServer;
import dev.hearthside.core.channel.HelloVerdict;
import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.ControlSchemeJson;
import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.GamepadSource;
import dev.hearthside.core.input.PadButton;
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
import java.io.IOException;
import java.io.UncheckedIOException;
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

	private static final class Slot {
		final int index;
		/** Null while the seat is still choosing on its lobby card. */
		volatile GuestProfile profile;
		volatile String secret;
		volatile SatelliteSupervisor supervisor;
		volatile ControlConnection connection;

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
		this.controlServer = ControlServer.start(this::verifyHello);
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
			ProfileFile file = profileStore().loadFile();
			hostScheme = file.hostControls();
			hostColor = file.host().color();
			hostReduceMotion = file.host().settings().reduceMotion();
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
		// The lobby is over: seats stay, and from now on new controllers join with A, A. (The flow is render-thread
		// only; this runs on the server thread.)
		minecraft.execute(flow::enterGame);
		// Seats still choosing go in with what their card shows (an in-view picker is Plan 5c).
		minecraft.execute(this::confirmWaitingCards);
		minecraft.execute(this::afterGuestsChanged);
		for (Slot slot : slots.values()) {
			ControlConnection connection = slot.connection;
			if (connection != null) {
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

	public static void register() {
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
		if (current == null) {
			current = new Party(minecraft, JoinFlow.Mode.LOBBY);
		}
		return current;
	}

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

	/** Starts a guest's game in the lowest free slot, as {@code profile}; returns their player index. Render thread. */
	private int startGuest(GuestProfile profile) throws IOException, UnsupportedLauncherException {
		HostJvm host = HostJvm.capture(HostLaunchFacts.read());
		int index = nextFreeIndex();
		if (index < 0) {
			throw new IllegalStateException("All 3 guest slots are taken");
		}

		Slot slot = new Slot(index, profile);
		Path satelliteDir = gameDir.resolve("hearthside").resolve("satellite-" + index);
		slot.supervisor = new SatelliteSupervisor(new JdkProcessLauncher(), () -> {
			slot.secret = newSecret();
			try {
				Path dir = SatelliteDirectories.prepare(gameDir, index);
				return LaunchCommandBuilder.build(host,
						// A placeholder: the seat's game takes its player's name when sent into the world.
						new SatelliteLaunch(index, controlServer.port(), dir, "Seat" + index, slot.secret, presentation()));
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
	private final Map<String, GuestProfile> chosen = new HashMap<>();
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

	public boolean hostClaimArmed() {
		return hostClaimArmed && !hostHasController();
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
			case NONE -> { }
		}
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
		List<GuestProfile> profiles = List.of();
		try {
			profiles = profileStore().load();
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not read guest profiles", e);
		}
		java.util.Set<UUID> held = new java.util.HashSet<>();
		slots.values().forEach(s -> {
			if (s.index != index && s.profile != null) {
				held.add(s.profile.id());
			}
		});
		List<String> names = profiles.stream().map(GuestProfile::name).toList();
		String guid = guidOf.get(controller);
		return new SeatCard(index, new ProfileCarousel(profiles, held, guid),
				() -> new QuickSetup(names, minecraft.getUser().getName(), takenColors(index), index), guid, keep);
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

	/** A seat settles on its profile: remember it for this controller, then send the player in if a world is open. */
	void confirmProfile(int index, GuestProfile picked) {
		Slot slot = slots.get(index);
		if (slot == null || picked == null) {
			return;
		}
		String controller = controllerOfSlot.get(index);
		String guid = controller == null ? null : guidOf.get(controller);
		GuestProfile profile = guid != null ? picked.withController(guid) : picked;
		rememberProfile(profile);
		slot.profile = profile;
		cards.remove(index);
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
	}

	/** The world opened: seats still choosing go in with what their card shows. Render thread. */
	private void confirmWaitingCards() {
		for (Map.Entry<Integer, SeatCard> entry : List.copyOf(cards.entrySet())) {
			confirmProfile(entry.getKey(), entry.getValue().autoConfirm());
		}
	}

	/** Saves a profile as the most recently used (profiles are stored least recent first). */
	private void rememberProfile(GuestProfile profile) {
		try {
			ProfileStore store = profileStore();
			List<GuestProfile> profiles = new ArrayList<>(store.load());
			profiles.removeIf(p -> p.id().equals(profile.id()));
			profiles.add(profile);
			store.save(profiles);
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not save {}'s profile", profile.name(), e);
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

	/** Once a second: expire join prompts, offer take-overs, and mark guests who reached the world. Render thread. */
	void tickFlow() {
		WorldLink link = world;
		controllerOfSlot.forEach((index, controller) -> {
			Slot slot = slots.get(index);
			GuestProfile profile = slot == null ? null : slot.profile;
			if (link != null && profile != null && link.hasPlayer(profile.name())) {
				flow.loaded(controller);
			}
		});
		apply(flow.tick());
	}

	private void apply(JoinFlow.Result result) {
		for (JoinFlow.Effect effect : result.effects()) {
			switch (effect) {
				case JoinFlow.Effect.ShowJoinPrompt prompt -> minecraft.gui.toastManager().addToast(new SystemToast(
						SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal("New controller"),
						Component.literal("Press A again to join")));
				case JoinFlow.Effect.OpenProfilePicker pick -> {
					if (world == null) {
						// In the lobby the player picks on their card, which opens once their seat's game starts.
						pendingCards.add(pick.controller());
					} else {
						// In a world (drop-in), the profile last used on this controller (the in-view picker is 5c).
						GuestProfile profile = profileFor(guidOf.get(pick.controller()));
						if (profile != null) {
							chosen.put(pick.controller(), profile);
							flow.profileChosen(pick.controller(), profile.id());
						}
					}
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
				case JoinFlow.Effect.HideJoinPrompt hide -> { }
				case JoinFlow.Effect.ClaimSeat claim -> { }
			}
		}
	}

	private void startForController(String controller) {
		GuestProfile profile = chosen.remove(controller);
		boolean card = pendingCards.remove(controller);
		Integer instanceId = instanceOf.get(controller);
		if ((profile == null && !card) || instanceId == null) {
			apply(flow.leave(controller));
			return;
		}
		try {
			int index = startGuest(profile);
			controllerOfSlot.put(index, controller);
			if (card) {
				cards.put(index, newCard(index, controller, null));
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
			for (GuestProfile p : profiles) {
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

	private void removeSlot(Slot slot) {
		slots.remove(slot.index, slot);
		cards.remove(slot.index);
		String controller = controllerOfSlot.remove(slot.index);
		if (controller != null) {
			minecraft.execute(() -> flow.leave(controller));
		}
		WorldLink link = world;
		if (link != null) {
			link.remove(slot.index);
		}
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
		// The login token travels in JoinWorld, once a world is open.
		return new HelloVerdict(new ControlMessage.HelloAck(ControlCodec.PROTOCOL_VERSION, true, "", ""),
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
		if (world == null) {
			// In the lobby guests' games wait hidden; views are laid out once a world is open.
			if (Compositor.active() != null) {
				Compositor.deactivate(minecraft);
			}
			return;
		}
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
			HearthsideMod.LOGGER.info("Started satellite for {} (player {}, pid {})", slot.displayName(), slot.index + 1, process.pid());
		}

		@Override
		public void restarting(int consecutiveFailures, Duration delay) {
			HearthsideMod.LOGGER.warn("Satellite for {} stopped; restarting in {}s", slot.displayName(), delay.toSeconds());
			chat(Component.literal(slot.displayName() + "'s game stopped; restarting in " + delay.toSeconds() + "s")
					.withStyle(ChatFormatting.YELLOW));
		}

		@Override
		public void gaveUp(int exitCode) {
			HearthsideMod.LOGGER.error("Satellite for {} keeps crashing (exit {}); giving up", slot.displayName(), exitCode);
			chat(Component.literal(slot.displayName() + "'s game keeps crashing. See hearthside/satellite-" + slot.index
					+ "/logs for details.").withStyle(ChatFormatting.RED));
		}

		@Override
		public void ended() {
			HearthsideMod.LOGGER.info("{} closed their game window", slot.displayName());
			chat(Component.literal(slot.displayName() + " left."));
			removeSlot(slot);
		}

		private void chat(Component message) {
			minecraft.execute(() -> minecraft.gui.chatListener().handleSystemMessage(message, false));
		}
	}
}
