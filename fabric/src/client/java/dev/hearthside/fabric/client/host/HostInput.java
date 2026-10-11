package dev.hearthside.fabric.client.host;

import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.GamepadSource;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.InputRouter;
import dev.hearthside.core.input.PadButton;
import dev.hearthside.core.input.PadSnapshot;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.input.RouterEvent;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.core.protocol.LifecycleCommand;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.input.ButtonGuideRenderer;
import dev.hearthside.fabric.client.input.InputApplier;
import dev.hearthside.fabric.client.input.VirtualCursor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The host's controller loop, run once per rendered frame: read every pad, route each to its
 * player, forward guests' input to their satellites, drive the host's own pad, and react to
 * A/B presses from unused controllers (the join flow), claims, unplugs and replugs. Render thread only, except {@link #styleFor}.
 */
final class HostInput implements Runnable {
	private static final long RESEND_NANOS = 100_000_000L;

	private final Minecraft minecraft;
	private final Party session;
	private final GamepadSource source;
	private final InputRouter router = new InputRouter();
	private final InputApplier hostApplier;
	private final Map<Integer, InputFrame> lastSent = new HashMap<>();
	private final Map<Integer, Long> lastSentAt = new HashMap<>();
	/** Pad style per player, readable from the control-channel threads. */
	private final Map<Integer, PadStyle> styles = new ConcurrentHashMap<>();
	/** The host's own options before its controller scheme changed them; null while it has no pad. */
	private Boolean savedToggleCrouch;
	private Boolean savedAutoJump;
	private boolean closed;
	// Each controller's model id, for remembering which controller a profile last used.
	private final Map<Integer, String> guids = new HashMap<>();
	private long lastFlowTick;
	private final Map<Integer, Integer> lobbyButtons = new HashMap<>();

	HostInput(Minecraft minecraft, Party session, GamepadSource source, ControlScheme hostScheme) {
		this.minecraft = minecraft;
		this.session = session;
		this.source = source;
		this.hostApplier = new InputApplier(minecraft);
		hostApplier.setScheme(hostScheme);
	}

	@Override
	public void run() {
		if (closed) {
			return;
		}
		List<PadSnapshot> pads = source.poll();
		for (PadSnapshot pad : pads) {
			guids.put(pad.instanceId(), pad.guid());
		}
		InputRouter.Update update = router.update(pads);
		long tickNow = System.nanoTime();
		if (tickNow - lastFlowTick >= 1_000_000_000L) {
			lastFlowTick = tickNow;
			session.tickFlow();
		}
		for (RouterEvent event : update.events()) {
			try {
				handle(event);
			} catch (RuntimeException e) {
				HearthsideMod.LOGGER.error("Hearthside could not handle controller event {}", event, e);
			}
		}
		refreshStyles();
		if (minecraft.gui.screen() instanceof LobbyScreen lobby) {
			// Shared screen: Menu on any seated controller moves the party on (Spec A section 7.1).
			for (Map.Entry<Integer, InputFrame> entry : update.frames().entrySet()) {
				int buttons = entry.getValue().buttons();
				int before = lobbyButtons.getOrDefault(entry.getKey(), 0);
				int start = InputFrame.buttons(PadButton.START);
				if ((buttons & start) != 0 && (before & start) == 0) {
					lobby.continueToWorlds();
				}
				lobbyButtons.put(entry.getKey(), buttons);
			}
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
		styles.remove(player);
	}

	/** Any thread. */
	PadStyle styleFor(int player) {
		return styles.getOrDefault(player, PadStyle.GENERIC);
	}

	void close() {
		closed = true;
		hostApplier.release();
		ButtonGuideRenderer.disable();
		VirtualCursor.setDrawn(false);
		restoreHostOptions();
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
			case RouterEvent.UnassignedPress press -> session.onUnassignedPress(press.instanceId(), press.button(),
					guids.get(press.instanceId()));
			case RouterEvent.ClaimRequest claim -> claimForHost(claim.instanceId());
			case RouterEvent.PadLost lost -> {
				if (lost.player() == 0) {
					hostApplier.release();
					minecraft.pauseGame(false);
				} else {
					session.sendToGuest(lost.player(), new ControlMessage.Lifecycle(LifecycleCommand.PAUSE));
					session.onPadLost(lost.player());
				}
				chat(Component.literal("Player " + (lost.player() + 1) + "'s controller disconnected.").withStyle(ChatFormatting.YELLOW));
			}
			case RouterEvent.PadRestored restored -> {
				if (restored.player() != 0) {
					session.onPadRestored(restored.player(), restored.instanceId());
					session.sendToGuest(restored.player(), new ControlMessage.SetPadStyle(router.styleOf(restored.player()).orElse(PadStyle.GENERIC)));
					session.sendToGuest(restored.player(), new ControlMessage.Lifecycle(LifecycleCommand.RESUME));
				}
				chat(Component.literal("Player " + (restored.player() + 1) + "'s controller reconnected."));
			}
		}
	}

	/** {@code /hearthside pad}: the host takes this controller, replacing any it had. */
	private void claimForHost(int instanceId) {
		if (router.hasPad(0)) {
			hostApplier.release();
			router.release(0);
		}
		router.assign(instanceId, 0);
		if (savedAutoJump == null) {
			savedToggleCrouch = minecraft.options.toggleCrouch().get();
			savedAutoJump = minecraft.options.autoJump().get();
		}
		hostApplier.applyOptions();
		ButtonGuideRenderer.setStyle(router.styleOf(0).orElse(PadStyle.GENERIC));
		ButtonGuideRenderer.enable(hostApplier::scheme);
		VirtualCursor.setDrawn(true);
		chat(Component.literal("Controller assigned to you."));
	}

	private void restoreHostOptions() {
		if (savedAutoJump != null) {
			minecraft.options.toggleCrouch().set(savedToggleCrouch);
			minecraft.options.autoJump().set(savedAutoJump);
			savedToggleCrouch = null;
			savedAutoJump = null;
		}
	}

	/** Gives a controller to a guest who just joined. Render thread. */
	void assignPad(int instanceId, int player) {
		// A guest who just left may still own this slot in the router until their release runs.
		if (router.hasPad(player)) {
			router.release(player);
		}
		router.assign(instanceId, player);
	}

	private void refreshStyles() {
		for (int player = 0; player < InputRouter.MAX_PLAYERS; player++) {
			int p = player;
			router.styleOf(p).ifPresentOrElse(style -> styles.put(p, style), () -> styles.remove(p));
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
