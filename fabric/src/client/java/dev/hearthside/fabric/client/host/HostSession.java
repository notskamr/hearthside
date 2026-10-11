package dev.hearthside.fabric.client.host;

import dev.hearthside.core.auth.GuestTokenAuthority;
import dev.hearthside.core.channel.ControlConnection;
import dev.hearthside.core.channel.ControlServer;
import dev.hearthside.core.channel.HelloVerdict;
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
import dev.hearthside.core.profile.ProfileStore;
import dev.hearthside.core.profile.SkinRef;
import dev.hearthside.core.protocol.ControlCodec;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.core.protocol.LifecycleCommand;
import dev.hearthside.core.session.GuestRoster;
import dev.hearthside.core.session.SatelliteDirectories;
import dev.hearthside.core.supervise.RestartPolicy;
import dev.hearthside.fabric.HearthsideMod;
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

	// Render-thread state for split-screen mode.
	private boolean splitScreen;
	private Rect hostWindowBefore;
	private boolean pauseOnLostFocusBefore;
	private Rect workArea;

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
		HearthsideMod.LOGGER.info("Hearthside session open: control channel on {}", controlServer.port());
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

	/** Adds a guest and starts their satellite. Runs on the render thread. */
	public void join(String name) throws IOException, UnsupportedLauncherException {
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
						new SatelliteLaunch(index, controlServer.port(), dir, profile.name(), slot.secret));
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
		minecraft.execute(this::leaveSplitScreen);
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
		minecraft.execute(this::afterGuestsChanged);
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
		return new HelloVerdict(new ControlMessage.HelloAck(ControlCodec.PROTOCOL_VERSION, true, "", tokens.issue(guest)),
				connection -> {
					slot.connection = connection;
					connection.onClose(() -> {
						if (slot.connection == connection) {
							slot.connection = null;
						}
					});
					send(connection, new ControlMessage.JoinWorld(gamePort));
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
		// Satellite windows take focus as they appear; the host must not pause because of it.
		minecraft.options.pauseOnLostFocus = false;
	}

	/** Puts the host window and focus setting back once no guests remain. Render thread. */
	private void leaveSplitScreen() {
		if (!splitScreen) {
			return;
		}
		splitScreen = false;
		WindowPlacer.restore(minecraft.getWindow(), hostWindowBefore);
		minecraft.options.pauseOnLostFocus = pauseOnLostFocusBefore;
	}

	/** Re-tiles, or leaves split-screen mode when the last guest has gone. Render thread. */
	private void afterGuestsChanged() {
		List<Slot> guests = new ArrayList<>(slots.values());
		if (guests.isEmpty()) {
			leaveSplitScreen();
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
				send(connection, new ControlMessage.SetViewport(rects.get(i + 1)));
			}
		}
		WindowPlacer.focus(minecraft.getWindow());
	}

	private GuestProfile loadOrCreateProfile(String name) throws IOException {
		ProfileStore store = new ProfileStore(gameDir.resolve("hearthside").resolve("profiles.json"));
		List<GuestProfile> profiles = new ArrayList<>(store.load());
		for (GuestProfile p : profiles) {
			if (p.name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
				return p;
			}
		}
		GuestProfile created = new GuestProfile(UUID.randomUUID(), name, SkinRef.defaultSkin("steve"), null);
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
