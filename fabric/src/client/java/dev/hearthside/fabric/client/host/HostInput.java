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
import dev.hearthside.fabric.client.menu.PadScreen;
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
	private volatile int freePads;
	/** Player 1's remembered controller is taken back only in the first moments after the party starts. */
	private final long autoClaimUntil = System.nanoTime() + 2_000_000_000L;
	/** Controllers guests have used this session: never handed to player 1 automatically. */
	private final java.util.Set<Integer> guestPads = new java.util.HashSet<>();
	/** Buttons each player still holds from closing their card; they don't press anything until released. */
	private final Map<Integer, Integer> heldFromCard = new HashMap<>();
	private final java.util.Set<Integer> hadCard = new java.util.HashSet<>();
	private volatile PadStyle freeStyle;
	private volatile int lastDriver = -1;
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
		takeTitlePad(pads);
		autoClaimHost(pads);
		int free = 0;
		PadStyle freeKind = null;
		for (PadSnapshot pad : pads) {
			if (router.playerFor(pad.instanceId()).isEmpty()) {
				free++;
				// One family's buttons if every free controller is that family; generic letters for a mix.
				freeKind = freeKind == null || freeKind == pad.style() ? pad.style() : PadStyle.GENERIC;
			}
		}
		freePads = free;
		freeStyle = freeKind;
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
		indicatePlayers(pads);
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
		if (lastGuestDrive != 0 && !router.hasPad(0)) {
			// A guest's pointer from the lobby has no place in the world.
			VirtualCursor.setDrawn(false);
			lastGuestDrive = 0;
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
		Map<Integer, InputFrame> shared = new HashMap<>();
		for (Map.Entry<Integer, InputFrame> entry : frames.entrySet()) {
			int player = entry.getKey();
			InputFrame frame = entry.getValue();
			if (player != 0 && session.hasSeatCard(player)) {
				// A card is only on screen in the lobby; elsewhere (world select) that controller waits.
				if (lobby != null) {
					session.seatCardInput(player, frame, now);
				}
				hadCard.add(player);
				sharedButtons.put(player, frame.buttons());
				continue;
			}
			if (hadCard.remove(player)) {
				// The press that closed the card is still held: it must not also click the shared screen.
				heldFromCard.put(player, frame.buttons());
			}
			int held = heldFromCard.getOrDefault(player, 0) & frame.buttons();
			if (held == 0) {
				heldFromCard.remove(player);
			} else {
				heldFromCard.put(player, held);
				frame = new InputFrame(frame.sequence(), frame.buttons() & ~held, frame.leftX(), frame.leftY(),
						frame.rightX(), frame.rightY(), frame.leftTrigger(), frame.rightTrigger());
			}
			shared.put(player, frame);
			int buttons = frame.buttons();
			int pressed = buttons & ~sharedButtons.getOrDefault(player, 0);
			sharedButtons.put(player, buttons);
			if (player == 0 || lobby == null) {
				continue;
			}
			if ((pressed & start) != 0) {
				lobby.continueToWorlds();
			} else if ((pressed & b) != 0) {
				session.onSeatedB(player);
			} else if ((pressed & InputFrame.buttons(PadButton.Y)) != 0) {
				// Y on a ready card: pick another profile (Spec A section 5.5).
				session.changeProfile(player);
			}
		}
		sharedButtons.keySet().retainAll(frames.keySet());
		if (screen instanceof PadScreen padScreen) {
			// Hearthside's own screens take every player's controller directly (focus moves, no pointer).
			if (sharedDriving) {
				hostApplier.accept(InputFrame.NEUTRAL);
				hostApplier.onFrame();
				hostApplier.release();
				sharedDriving = false;
			}
			VirtualCursor.setDrawn(false);
			for (Map.Entry<Integer, InputFrame> entry : new java.util.TreeMap<>(shared).entrySet()) {
				if (SharedScreenInput.isActive(entry.getValue())) {
					lastDriver = entry.getKey();
				}
				padScreen.padInput(entry.getKey(), entry.getValue(), now);
				if (minecraft.gui.screen() != screen) {
					break;
				}
			}
			return;
		}
		// Ending the party is player 1's call (Spec A section 7.1): guests don't drive its confirmation.
		Map<Integer, InputFrame> drivers = screen instanceof ConfirmScreen
				? (shared.containsKey(0) ? Map.of(0, shared.get(0)) : Map.of()) : shared;
		int lobbyButtons = start | b | InputFrame.buttons(PadButton.Y);
		SharedScreenInput.Driver driver = SharedScreenInput.drive(drivers, lobby != null ? lobbyButtons : start);
		if (driver != null && (driver.player() != 0 || drivers.containsKey(0))
				&& (SharedScreenInput.isActive(driver.frame()) || lastDriver < 0)) {
			VirtualCursor.setTint(session.colorOf(driver.player()));
			lastDriver = driver.player();
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
		// Controllers stop showing a player's colour and number once the party ends.
		for (Integer pad : marked.keySet()) {
			source.indicate(pad, -1, 0);
		}
		marked.clear();
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
			case RouterEvent.UnassignedPress press -> {
				// A press from a controller assigned this frame (player 1's from the title) is not a join.
				if (router.playerFor(press.instanceId()).isEmpty()) {
					session.onUnassignedPress(press.instanceId(), press.button(), guids.get(press.instanceId()));
				}
			}
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

	/**
	 * The controller that pressed Co-op on the title screen is player 1's (Spec A section 3.1); the A it is still
	 * holding doesn't also press anything in the lobby.
	 */
	private void takeTitlePad(List<PadSnapshot> pads) {
		Integer id = session.pendingHostPad;
		if (id == null) {
			return;
		}
		session.pendingHostPad = null;
		for (PadSnapshot pad : pads) {
			if (pad.instanceId() == id && router.playerFor(id).isEmpty()) {
				claimForHost(id);
				heldFromCard.put(0, pad.frame().buttons());
				return;
			}
		}
	}

	/** In the lobby, player 1's remembered controller is theirs again as soon as it is connected. */
	private void autoClaimHost(List<PadSnapshot> pads) {
		String guid = session.hostControllerGuid();
		// Only as the lobby opens: later, an idle controller of that model is far more likely a guest's.
		if (guid == null || router.hasPad(0) || session.inWorld() || System.nanoTime() > autoClaimUntil) {
			return;
		}
		for (PadSnapshot pad : pads) {
			if (guid.equals(pad.guid()) && router.playerFor(pad.instanceId()).isEmpty()
					&& !guestPads.contains(pad.instanceId())) {
				claimForHost(pad.instanceId());
				return;
			}
		}
	}

	/** What each controller was last marked with (player and colour), so the hardware is only told of changes. */
	private final Map<Integer, Long> marked = new HashMap<>();

	/** Lights each controller in its player's colour (Spec A section 4.1: a player's colour marks them). */
	private void indicatePlayers(List<PadSnapshot> pads) {
		for (PadSnapshot pad : pads) {
			int player = router.playerFor(pad.instanceId()).orElse(-1);
			int rgb = player < 0 ? 0 : session.colorOf(player);
			long mark = (long) player << 32 | (rgb & 0xFFFFFFFFL);
			Long before = marked.put(pad.instanceId(), mark);
			// A controller nobody has taken keeps its own light until it becomes someone's.
			if (before == null ? player >= 0 : before != mark) {
				source.indicate(pad.instanceId(), player, rgb);
			}
		}
		marked.keySet().retainAll(pads.stream().map(PadSnapshot::instanceId).toList());
	}

	/** Controllers connected but not assigned to anyone, as of the last poll. */
	int freePadCount() {
		return freePads;
	}

	/** The button family of the free controllers (generic for a mix), or null when there are none. */
	PadStyle freePadStyle() {
		return freeStyle;
	}

	/** The player who last moved the shared screen with a controller, or -1. */
	int lastSharedDriver() {
		return lastDriver;
	}

	boolean hasPadFor(int player) {
		return router.hasPad(player);
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
		session.onHostClaimed(guids.get(instanceId));
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
	/** Player 1 gives their controller back; it is free to join as a guest. */
	void releaseHost() {
		if (router.hasPad(0)) {
			hostApplier.release();
			router.release(0);
			restoreHostOptions();
			if (!sharedDriving) {
				VirtualCursor.setDrawn(false);
			}
		}
	}

	void assignPad(int instanceId, int player) {
		if (player > 0) {
			guestPads.add(instanceId);
		}
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
