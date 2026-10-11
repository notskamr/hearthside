package dev.hearthside.fabric.client.satellite;

import dev.hearthside.core.channel.ControlClient;
import dev.hearthside.core.channel.ControlConnection;
import dev.hearthside.core.input.ControlSchemeJson;
import dev.hearthside.core.launch.LaunchCommandBuilder;
import dev.hearthside.core.protocol.ControlCodec;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.core.protocol.LifecycleCommand;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.input.ButtonGuideRenderer;
import dev.hearthside.fabric.client.input.FrameHook;
import dev.hearthside.fabric.client.input.InputApplier;
import dev.hearthside.fabric.client.input.VirtualCursor;
import dev.hearthside.fabric.client.window.WindowPlacer;
import dev.hearthside.fabric.server.GuestLoginHooks;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.PauseScreen;
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
		HearthsideMod.LOGGER.info("Hearthside satellite ignores OS keyboard and mouse");
		// World audio plays once, from the host; per-player UI sounds return with Plan 5.
		minecraft.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);

		applier = new InputApplier(minecraft);
		FrameHook.register(applier::onFrame);
		VirtualCursor.enableDrawing();
		ButtonGuideRenderer.enable(applier::scheme);

		Thread.ofPlatform().daemon().name("hearthside-satellite-connect").start(() -> connect(minecraft, port, index, secret));
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
			case ControlMessage.SetViewport m -> minecraft.execute(() -> WindowPlacer.place(minecraft.getWindow(), m.rect()));
			case ControlMessage.JoinWorld m -> minecraft.execute(() -> ConnectScreen.startConnecting(new TitleScreen(), minecraft,
					new ServerAddress("127.0.0.1", m.port()),
					new ServerData("Hearthside", "127.0.0.1:" + m.port(), ServerData.Type.LAN), false, null));
			case ControlMessage.Lifecycle m when m.command() == LifecycleCommand.QUIT -> minecraft.execute(minecraft::stop);
			case ControlMessage.Lifecycle m when m.command() == LifecycleCommand.PAUSE -> minecraft.execute(() -> {
				HearthsideMod.LOGGER.info("Hearthside satellite paused (controller disconnected)");
				applier.release();
				minecraft.pauseGame(false);
			});
			case ControlMessage.Lifecycle m when m.command() == LifecycleCommand.RESUME -> minecraft.execute(() -> {
				HearthsideMod.LOGGER.info("Hearthside satellite resumed (controller reconnected)");
				if (minecraft.gui.screen() instanceof PauseScreen) {
					minecraft.gui.setScreen(null);
				}
			});
			case ControlMessage.Input m -> applier.accept(m.frame());
			case ControlMessage.SetControls m -> minecraft.execute(() -> applier.setScheme(ControlSchemeJson.fromJson(m.schemeJson())));
			case ControlMessage.SetPadStyle m -> {
				HearthsideMod.LOGGER.info("Hearthside SetPadStyle {}", m.style());
				ButtonGuideRenderer.setStyle(m.style());
			}
			default -> { }
		}
	}
}
