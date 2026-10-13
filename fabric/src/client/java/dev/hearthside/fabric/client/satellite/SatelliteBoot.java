package dev.hearthside.fabric.client.satellite;

import dev.hearthside.core.channel.ControlClient;
import dev.hearthside.core.channel.ControlConnection;
import dev.hearthside.core.input.ControlSchemeJson;
import dev.hearthside.core.launch.LaunchCommandBuilder;
import dev.hearthside.core.protocol.ControlCodec;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.core.protocol.LifecycleCommand;
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
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.core.UUIDUtil;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.PauseScreen;
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

	private SatelliteBoot() {}

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
				minecraft.pauseGame(false);
			});
			case ControlMessage.Lifecycle m when m.command() == LifecycleCommand.RESUME -> minecraft.execute(() -> {
				HearthsideMod.LOGGER.info("Hearthside satellite resumed (controller reconnected)");
				if (minecraft.gui.screen() instanceof PauseScreen || PauseMenuScreen.isOpen(minecraft)) {
					minecraft.gui.setScreen(null);
				}
			});
			case ControlMessage.Input m -> applier.accept(m.frame());
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
			case ControlMessage.SetPadStyle m -> {
				HearthsideMod.LOGGER.info("Hearthside SetPadStyle {}", m.style());
				ButtonGuideRenderer.setStyle(m.style());
			}
			default -> { }
		}
	}
}
