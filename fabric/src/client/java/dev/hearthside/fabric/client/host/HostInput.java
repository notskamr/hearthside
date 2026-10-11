package dev.hearthside.fabric.client.host;

import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.GamepadSource;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.InputRouter;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.input.RouterEvent;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.core.protocol.LifecycleCommand;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.input.ButtonGuideRenderer;
import dev.hearthside.fabric.client.input.InputApplier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The host's controller loop, run once per rendered frame: read every pad, route each to its
 * player, forward guests' input to their satellites, drive the host's own pad, and react to
 * START-to-join, claims, unplugs and replugs.
 */
final class HostInput implements Runnable {
	private static final long RESEND_NANOS = 100_000_000L;

	private final Minecraft minecraft;
	private final HostSession session;
	private final GamepadSource source;
	private final InputRouter router = new InputRouter();
	private final InputApplier hostApplier;
	private final Map<Integer, InputFrame> lastSent = new HashMap<>();
	private final Map<Integer, Long> lastSentAt = new HashMap<>();

	HostInput(Minecraft minecraft, HostSession session, GamepadSource source, ControlScheme hostScheme) {
		this.minecraft = minecraft;
		this.session = session;
		this.source = source;
		this.hostApplier = new InputApplier(minecraft);
		hostApplier.setScheme(hostScheme);
	}

	@Override
	public void run() {
		InputRouter.Update update = router.update(source.poll());
		for (RouterEvent event : update.events()) {
			handle(event);
		}
		long now = System.nanoTime();
		update.frames().forEach((player, frame) -> {
			if (player == 0) {
				hostApplier.accept(frame);
			} else if (!Objects.equals(lastSent.get(player), withoutSequence(frame))
					|| now - lastSentAt.getOrDefault(player, 0L) >= RESEND_NANOS) {
				session.sendToGuest(player, new ControlMessage.Input(frame));
				lastSent.put(player, withoutSequence(frame));
				lastSentAt.put(player, now);
			}
		});
		hostApplier.onFrame();
	}

	void armClaim() {
		router.armClaim();
	}

	/** A guest left: their pad becomes free and is never restored to them. */
	void release(int player) {
		router.release(player);
		lastSent.remove(player);
		lastSentAt.remove(player);
	}

	PadStyle styleFor(int player) {
		return router.styleOf(player).orElse(PadStyle.GENERIC);
	}

	void close() {
		hostApplier.release();
		ButtonGuideRenderer.disable();
		if (source instanceof AutoCloseable closeable) {
			try {
				closeable.close();
			} catch (Exception e) {
				HearthsideMod.LOGGER.warn("Could not close the controller source", e);
			}
		}
	}

	private void handle(RouterEvent event) {
		switch (event) {
			case RouterEvent.JoinRequest join -> addGuest(join.instanceId());
			case RouterEvent.ClaimRequest claim -> {
				router.assign(claim.instanceId(), 0);
				ButtonGuideRenderer.setStyle(styleFor(0));
				ButtonGuideRenderer.enable(hostApplier::scheme);
				chat(Component.literal("Controller assigned to you."));
			}
			case RouterEvent.PadLost lost -> {
				if (lost.player() == 0) {
					hostApplier.release();
					minecraft.pauseGame(false);
				} else {
					session.sendToGuest(lost.player(), new ControlMessage.Lifecycle(LifecycleCommand.PAUSE));
				}
				chat(Component.literal("Player " + (lost.player() + 1) + "'s controller disconnected.").withStyle(ChatFormatting.YELLOW));
			}
			case RouterEvent.PadRestored restored -> {
				if (restored.player() != 0) {
					session.sendToGuest(restored.player(), new ControlMessage.SetPadStyle(styleFor(restored.player())));
					session.sendToGuest(restored.player(), new ControlMessage.Lifecycle(LifecycleCommand.RESUME));
				}
				chat(Component.literal("Player " + (restored.player() + 1) + "'s controller reconnected."));
			}
		}
	}

	private void addGuest(int instanceId) {
		for (String prefix : new String[] {"Player", "Guest"}) {
			int index = session.nextFreeIndex();
			if (index < 0) {
				chat(Component.literal("All 3 guest slots are taken.").withStyle(ChatFormatting.RED));
				return;
			}
			try {
				int joined = session.join(prefix + (index + 1));
				router.assign(instanceId, joined);
				return;
			} catch (IllegalArgumentException refusedName) {
				// e.g. the host is called Player2; try the next naming scheme
			} catch (Exception e) {
				HearthsideMod.LOGGER.error("Could not add a player for controller {}", instanceId, e);
				chat(Component.literal("Couldn't add a player: " + e.getMessage()).withStyle(ChatFormatting.RED));
				return;
			}
		}
	}

	private void chat(Component message) {
		minecraft.gui.chatListener().handleSystemMessage(message, false);
	}

	/** Frames differ in sequence every poll; compare the controller state only. */
	private static InputFrame withoutSequence(InputFrame f) {
		return new InputFrame(0, f.buttons(), f.leftX(), f.leftY(), f.rightX(), f.rightY(), f.leftTrigger(), f.rightTrigger());
	}
}
