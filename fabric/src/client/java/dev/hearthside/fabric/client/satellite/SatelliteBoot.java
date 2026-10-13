package dev.hearthside.fabric.client.satellite;

import dev.hearthside.core.channel.ControlClient;
import dev.hearthside.core.channel.ControlConnection;
import dev.hearthside.core.input.ControlSchemeJson;
import dev.hearthside.core.launch.LaunchCommandBuilder;
import dev.hearthside.core.protocol.ControlCodec;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.core.protocol.LifecycleCommand;
import dev.hearthside.core.session.PauseLine;
import dev.hearthside.core.session.PausePolicy;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.compositor.FrameExporter;
import dev.hearthside.fabric.client.compositor.Presentation;
import dev.hearthside.fabric.client.input.ButtonGuideRenderer;
import dev.hearthside.fabric.client.mixin.MinecraftUserAccessor;
import dev.hearthside.fabric.client.input.FrameHook;
import dev.hearthside.fabric.client.input.InputApplier;
import dev.hearthside.fabric.client.input.VirtualCursor;
import dev.hearthside.fabric.client.menu.MenuNavigator;
import dev.hearthside.fabric.client.menu.PauseMenuScreen;
import dev.hearthside.fabric.client.window.WindowPlacer;
import dev.hearthside.fabric.server.GuestLoginHooks;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.core.UUIDUtil;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundSource;

/**
 * A satellite's whole life: connect to the host's control channel, answer the guest-token login
 * query, take the window position it is given, join the host's world, and quit when the host says
 * so or disappears.
 */
public final class SatelliteBoot {
	private static volatile String loginToken;
	private static InputApplier applier;
	/** The host's control channel, once connected. */
	private static volatile ControlConnection control;
	/** The pause state last told to the host (null: not yet). Render thread. */
	private static Boolean toldPaused;
	/** The pause menu's status line from the host's last PauseStatus in this world ("" for none). Render thread. */
	private static String pauseLine = "";
	private static boolean wasInWorld;
	/** Telling the host failed once; not tried again. Render thread. */
	private static boolean pauseFailed;
	/**
	 * The pause menu the host's PAUSE opened (the controller dropped), which its RESUME closes if it's still showing;
	 * null once resumed, or when the drop found a screen already open and opened none. Render thread.
	 */
	private static Screen pausedFor;

	private static final boolean E2E = "1".equals(System.getenv("HEARTHSIDE_E2E_PATTERN"));
	/** E2E: the buttons of the last input frame from the host. The control channel's reading thread. */
	private static int lastInputButtons;

	private SatelliteBoot() {}

	/** E2E: logs each press of A that reaches this game (the E2E checks a take-over's A never does). */
	private static void logAPress(dev.hearthside.core.input.InputFrame frame) {
		int a = dev.hearthside.core.input.InputFrame.buttons(dev.hearthside.core.input.PadButton.A);
		if ((frame.buttons() & ~lastInputButtons & a) != 0) {
			HearthsideMod.LOGGER.info("Hearthside input A down");
		}
		lastInputButtons = frame.buttons();
	}

	public static void register() {
		ClientLoginNetworking.registerGlobalReceiver(GuestLoginHooks.GUEST_TOKEN, (client, handler, buf, listenerAdder) -> {
			FriendlyByteBuf answer = new FriendlyByteBuf(Unpooled.buffer());
			answer.writeUtf(loginToken == null ? "" : loginToken);
			return CompletableFuture.completedFuture(answer);
		});
		ClientLifecycleEvents.CLIENT_STARTED.register(SatelliteBoot::start);
	}

	private static void start(Minecraft minecraft) {
		Integer port = Integer.getInteger("hearthside.controlPort");
		Integer index = Integer.getInteger("hearthside.player");
		String secret = System.getenv(LaunchCommandBuilder.SECRET_ENV);
		if (port == null || index == null || secret == null) {
			HearthsideMod.LOGGER.error("Satellite started without its control port, player index or launch secret; quitting");
			minecraft.stop();
			return;
		}
		minecraft.options.pauseOnLostFocus = false;
		// Vanilla's keyboard tutorial toasts are filtered (ToastFilterMixin), never the tutorial step: setting it saves
		// options, which would change the guest's own tutorial progress.
		HearthsideMod.LOGGER.info("Hearthside satellite ignores OS keyboard and mouse");
		// World audio plays once, from the host; per-player UI sounds return with Plan 5.
		minecraft.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
		// The host's preset (render-distance cap, light graphics, frame-rate cap), for this session only: a party has to
		// fit on a laptop.
		SessionOptions.applyForGuest(minecraft);

		applier = new InputApplier(minecraft);
		FrameHook.register(applier::onFrame);
		VirtualCursor.setDrawn(true);
		ButtonGuideRenderer.enable(applier::scheme);
		// Before any screen opens, so the guest's first pause menu is driven exactly like later ones.
		MenuNavigator.register();
		ClientTickEvents.END_CLIENT_TICK.register(SatelliteBoot::tickPause);
		if ("1".equals(System.getenv("HEARTHSIDE_E2E_PATTERN"))) {
			logScreens(minecraft);
		}

		Thread.ofPlatform().daemon().name("hearthside-satellite-connect").start(() -> connect(minecraft, port, index, secret));
	}

	/** E2E only: each screen this game shows, as it changes (the E2E reads a guest's screens from its log). */
	private static void logScreens(Minecraft minecraft) {
		Screen[] last = {null};
		FrameHook.register(() -> {
			Screen screen = minecraft.gui.screen();
			if (screen != last[0]) {
				last[0] = screen;
				if (screen != null) {
					HearthsideMod.LOGGER.info("Hearthside screen {}", screen.getClass().getSimpleName());
				}
			}
		});
	}

	/**
	 * Pausing together (Spec E section 3.2): tells the host whenever a pausing screen opens or closes in this game (any
	 * screen, Hearthside's, vanilla's or a mod's), and forgets the host's status line once the world closes.
	 */
	private static void tickPause(Minecraft minecraft) {
		if (pauseFailed) {
			return;
		}
		try {
			tellPause(minecraft);
		} catch (RuntimeException e) {
			// Never break Minecraft: the host just stops hearing this game's pause state.
			pauseFailed = true;
			HearthsideMod.LOGGER.error("Hearthside could not tell the host this game's pause state; no longer trying", e);
		}
	}

	private static void tellPause(Minecraft minecraft) {
		boolean inWorld = minecraft.level != null;
		if (wasInWorld && !inWorld) {
			pauseLine = "";
			PauseMenuScreen.setStatus("");
			PauseMenuScreen.setControllerAway(false);
		}
		wasInWorld = inWorld;
		ControlConnection connection = control;
		boolean paused = minecraft.gui.isPausing();
		if (connection == null || toldPaused != null && toldPaused == paused) {
			return;
		}
		toldPaused = paused;
		HearthsideMod.LOGGER.info("Hearthside pause state {}", paused ? "paused" : "playing");
		try {
			connection.send(new ControlMessage.PauseState(paused));
		} catch (IOException e) {
			HearthsideMod.LOGGER.warn("Could not tell the Hearthside host this game's pause state: {}", e.toString());
		}
	}

	/** The status line the host last sent for this world, for a pause menu as it opens ("" for none). Render thread. */
	public static String pauseLine() {
		return pauseLine;
	}

	private static void connect(Minecraft minecraft, int port, int index, String secret) {
		ControlConnection connection;
		try {
			connection = ControlClient.connect(port, new ControlMessage.Hello(ControlCodec.PROTOCOL_VERSION, index, secret),
					ack -> loginToken = ack.accepted() ? ack.loginToken() : null);
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not reach the Hearthside host; quitting", e);
			minecraft.execute(minecraft::stop);
			return;
		}
		if (!connection.isOpen()) {
			HearthsideMod.LOGGER.error("The Hearthside host rejected this satellite; quitting");
			minecraft.execute(minecraft::stop);
			return;
		}
		if (!Presentation.satelliteTiled()) {
			FrameExporter.install(minecraft, connection, index + 1);
		}
		connection.onMessage(message -> handle(minecraft, message));
		control = connection;
		connection.onClose(() -> {
			HearthsideMod.LOGGER.info("Hearthside host closed the control channel; quitting");
			minecraft.execute(minecraft::stop);
		});
		connection.startReading();
		HearthsideMod.LOGGER.info("Hearthside satellite connected as player {}", index + 1);
	}

	private static void handle(Minecraft minecraft, ControlMessage message) {
		switch (message) {
			case ControlMessage.SetViewport m -> minecraft.execute(() -> {
				FrameExporter exporter = FrameExporter.instance();
				if (exporter != null) {
					exporter.onViewport(m);
				} else {
					WindowPlacer.place(minecraft.getWindow(), m.rect());
				}
			});
			case ControlMessage.JoinWorld m -> minecraft.execute(() -> {
				loginToken = m.loginToken();
				FrameExporter exporter = FrameExporter.instance();
				if (exporter != null) {
					exporter.joinRequested();
				}
				if (!minecraft.getUser().getName().equals(m.playerName())) {
					// Started under a placeholder (pre-warmed before a profile was chosen): join as the player.
					((MinecraftUserAccessor) minecraft).hearthside$setUser(new User(m.playerName(),
							UUIDUtil.createOfflinePlayerUUID(m.playerName()), "0", Optional.empty(), Optional.empty()));
					HearthsideMod.LOGGER.info("Hearthside satellite joins as {}", m.playerName());
				}
				ConnectScreen.startConnecting(new TitleScreen(), minecraft, new ServerAddress("127.0.0.1", m.port()),
						new ServerData("Hearthside", "127.0.0.1:" + m.port(), ServerData.Type.LAN), false, null);
			});
			case ControlMessage.Lifecycle m when m.command() == LifecycleCommand.QUIT -> minecraft.execute(minecraft::stop);
			case ControlMessage.Lifecycle m when m.command() == LifecycleCommand.DISCONNECT -> minecraft.execute(() -> {
				// As its own pause menu's Disconnect does; the host hears it left and lets the player go.
				if (minecraft.level != null) {
					minecraft.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
				}
			});
			case ControlMessage.Lifecycle m when m.command() == LifecycleCommand.PAUSE -> minecraft.execute(() -> {
				HearthsideMod.LOGGER.info("Hearthside satellite paused (controller disconnected)");
				applier.release();
				PauseMenuScreen.setControllerAway(true);
				Screen before = minecraft.gui.screen();
				minecraft.pauseGame(false);
				Screen opened = minecraft.gui.screen();
				if (opened != before) {
					pausedFor = opened;
				}
			});
			case ControlMessage.Lifecycle m when m.command() == LifecycleCommand.RESUME -> minecraft.execute(() -> {
				HearthsideMod.LOGGER.info("Hearthside satellite resumed (controller reconnected)");
				// Only the menu the drop opened, if it's still the one showing: one the player opened is theirs to close.
				PauseMenuScreen.setControllerAway(false);
				Screen opened = pausedFor;
				pausedFor = null;
				if (opened != null && minecraft.gui.screen() == opened) {
					minecraft.gui.setScreen(null);
				}
			});
			case ControlMessage.Input m -> {
				if (E2E) {
					logAPress(m.frame());
				}
				applier.accept(m.frame());
			}
			case ControlMessage.UseCopyTransport m -> minecraft.execute(() -> {
				FrameExporter exporter = FrameExporter.instance();
				if (exporter != null) {
					HearthsideMod.LOGGER.warn("Hearthside host could not use shared GPU frames; copying them instead");
					exporter.useCopy();
				}
			});
			case ControlMessage.SetControls m -> minecraft.execute(() -> {
				var scheme = ControlSchemeJson.fromJson(m.schemeJson());
				HearthsideMod.LOGGER.info("Hearthside SetControls jump={}",
						scheme.controlFor(dev.hearthside.core.input.Action.JUMP).map(Enum::name).orElse("none"));
				applier.setScheme(scheme);
				applier.applyOptions();
			});
			case ControlMessage.PauseStatus m -> minecraft.execute(() -> {
				HearthsideMod.LOGGER.info("Hearthside world {} still playing={}", m.frozen() ? "frozen" : "running",
						String.join(",", m.stillPlaying()));
				pauseLine = PauseLine.of(new PausePolicy.Decision(m.frozen(), m.stillPlaying()));
				PauseMenuScreen.setStatus(pauseLine);
			});
			case ControlMessage.SetPadStyle m -> {
				HearthsideMod.LOGGER.info("Hearthside SetPadStyle {}", m.style());
				ButtonGuideRenderer.setStyle(m.style());
			}
			default -> { }
		}
	}
}
