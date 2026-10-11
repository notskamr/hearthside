package dev.hearthside.fabric.client.host;

import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.GamepadSource;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.InputRouter;
import dev.hearthside.core.input.PadButton;
import dev.hearthside.core.input.PadSnapshot;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.input.RouterEvent;
import dev.hearthside.core.input.SharedScreenInput;
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
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
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
	/** Each controller's buttons last frame on shared screens, for press edges. */
	private final Map<Integer, Integer> sharedButtons = new HashMap<>();
	/** Whether a guest's controller is driving the host's shared screen (the host has no pad of its own). */
	private boolean sharedDriving;
	/** When a guest last moved the shared pointer; it hides a while after, for the mouse. */
	private long lastGuestDrive;
	private static final long POINTER_LINGER_NANOS = 3_000_000_000L;

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
			try {
				session.tickFlow();
			} catch (RuntimeException e) {
				HearthsideMod.LOGGER.error("Hearthside join-flow tick failed", e);
			}
		}
		for (RouterEvent event : update.events()) {
			try {
				handle(event);
			} catch (RuntimeException e) {
				HearthsideMod.LOGGER.error("Hearthside could not handle controller event {}", event, e);
			}
		}
		refreshStyles();
		if (!session.inWorld()) {
			// No world yet (lobby, world select): guests' games are idle on their title screens and must not be
			// driven. Every controller drives the host's shared screen instead (Spec A sections 7.1, 8.2).
			driveSharedScreen(update.frames());
			return;
		}
		if (sharedDriving) {
			sharedDriving = false;
			if (!update.frames().containsKey(0)) {
				hostApplier.release();
			}
		}
		sharedButtons.clear();
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

	/**
	 * Shared screens: a seat still choosing its profile drives only its own lobby card; Menu on any other seated
	 * controller continues from the lobby, B gives up its seat, and otherwise whoever is using a controller moves the
	 * host's cursor, which takes their colour.
	 */
	private void driveSharedScreen(Map<Integer, InputFrame> frames) {
		Screen screen = minecraft.gui.screen();
		LobbyScreen lobby = screen instanceof LobbyScreen l ? l : null;
		int start = InputFrame.buttons(PadButton.START);
		int b = InputFrame.buttons(PadButton.B);
		long now = System.nanoTime();
		Map<Integer, InputFrame> shared = new HashMap<>(frames);
		for (Map.Entry<Integer, InputFrame> entry : frames.entrySet()) {
			int player = entry.getKey();
			int buttons = entry.getValue().buttons();
			int pressed = buttons & ~sharedButtons.getOrDefault(player, 0);
			sharedButtons.put(player, buttons);
			if (player != 0 && session.hasSeatCard(player)) {
				session.seatCardInput(player, entry.getValue(), now);
				shared.remove(player);
				continue;
			}
			if (player == 0 || lobby == null) {
				continue;
			}
			if ((pressed & start) != 0) {
				lobby.continueToWorlds();
			} else if ((pressed & b) != 0) {
				session.onSeatedB(player);
			}
		}
		sharedButtons.keySet().retainAll(frames.keySet());
		// Ending the party is player 1's call (Spec A section 7.1): guests don't drive its confirmation.
		Map<Integer, InputFrame> drivers = screen instanceof ConfirmScreen
				? (shared.containsKey(0) ? Map.of(0, shared.get(0)) : Map.of()) : shared;
		SharedScreenInput.Driver driver = SharedScreenInput.drive(drivers, lobby != null ? start | b : start);
		if (driver != null && (driver.player() != 0 || drivers.containsKey(0))) {
			VirtualCursor.setTint(session.colorOf(driver.player()));
		}
		if (drivers.containsKey(0)) {
			hostApplier.accept(driver.frame());
			hostApplier.onFrame();
		} else if (screen != null && driver != null && SharedScreenInput.isActive(driver.frame())) {
			if (!sharedDriving) {
				VirtualCursor.setDrawn(true);
			}
			hostApplier.accept(driver.frame());
			hostApplier.onFrame();
			sharedDriving = true;
			lastGuestDrive = now;
		} else if (sharedDriving) {
			// Let go (releasing anything held). The pointer stays where the guest left it until the mouse moves.
			hostApplier.accept(InputFrame.NEUTRAL);
			hostApplier.onFrame();
			hostApplier.release();
			sharedDriving = false;
		} else if (lastGuestDrive != 0 && now - lastGuestDrive > POINTER_LINGER_NANOS && !router.hasPad(0)) {
			// Nobody has used a controller for a while: the screen is the mouse's again.
			VirtualCursor.setDrawn(false);
			lastGuestDrive = 0;
		}
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
