package dev.hearthside.fabric.client.host;

import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.NavRepeater;
import dev.hearthside.core.input.PadButton;
import dev.hearthside.core.profile.GuestProfile;
import dev.hearthside.core.profile.ProfileCarousel;
import dev.hearthside.core.profile.QuickSetup;
import dev.hearthside.core.ui.KeyboardModel;
import dev.hearthside.fabric.client.input.KeyboardFocus;
import dev.hearthside.fabric.client.ui.HearthSounds;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One seat's lobby card while its player picks or creates a profile (Spec A sections 3.1, 5.2): the profile carousel,
 * then for New, quick setup with the on-screen keyboard; X opens More (the profile's controls, managing profiles).
 * That seat's controller drives only its card; player 1's PC keyboard can type on its name keyboard. Render thread.
 */
public final class SeatCard implements KeyboardFocus.Target {
	public enum Outcome { NONE, CONFIRMED, LEAVE, CONTROLS, MANAGE }

	public enum Stage { CAROUSEL, SETUP, MORE }

	/** What More offers. */
	public enum MoreEntry {
		CONTROLS("Controls"), MANAGE("Profiles");

		public final String label;

		MoreEntry(String label) {
			this.label = label;
		}
	}

	private static final float STICK = 0.5f;

	private final int seat;
	private final ProfileCarousel carousel;
	private final Supplier<QuickSetup> newSetup;
	private final String controllerGuid;
	/** The profile the seat had before reopening its card (Y on a ready card), kept if the player backs out; else null. */
	private GuestProfile keep;
	/** Opened with X on a ready card: B in More goes straight back to the ready seat. */
	private final boolean moreOnly;
	/** False mid-game: X does nothing (More's screens would take over player 1's view). */
	private boolean moreAllowed = true;
	private int moreFocus;
	private final NavRepeater horizontal = new NavRepeater(Duration.ofMillis(300), Duration.ofMillis(80));
	private final NavRepeater vertical = new NavRepeater(Duration.ofMillis(300), Duration.ofMillis(80));
	private Stage stage = Stage.CAROUSEL;
	private QuickSetup setup;
	private KeyboardModel keyboard;
	/** Whether the player has moved over the keyboard: until then A keeps the suggested name ("A, A, A"). */
	private boolean keyboardMoved;
	private String nameProblem;
	private String problem;
	private GuestProfile chosen;
	/** Everything counts as already held when the card opens: the A that took the seat must not also confirm. */
	private int lastButtons = -1;

	SeatCard(int seat, ProfileCarousel carousel, Supplier<QuickSetup> newSetup, String controllerGuid, GuestProfile keep) {
		this(seat, carousel, newSetup, controllerGuid, keep, false);
	}

	/** @param more open on More, for a ready seat (whose profile is {@code keep}) */
	SeatCard(int seat, ProfileCarousel carousel, Supplier<QuickSetup> newSetup, String controllerGuid, GuestProfile keep,
			boolean more) {
		this.seat = seat;
		this.carousel = carousel;
		this.newSetup = newSetup;
		this.controllerGuid = controllerGuid;
		this.keep = keep;
		this.moreOnly = more && keep != null;
		if (moreOnly) {
			stage = Stage.MORE;
		}
	}

	/** This seat's controller, this frame. */
	public Outcome accept(InputFrame frame, long nanos) {
		int pressed = frame.buttons() & ~lastButtons;
		lastButtons = frame.buttons();
		if (pressed != 0) {
			problem = null;
		}
		int dx = horizontal.step(direction(frame, PadButton.DPAD_LEFT, PadButton.DPAD_RIGHT, frame.leftX()), nanos);
		int dy = vertical.step(direction(frame, PadButton.DPAD_UP, PadButton.DPAD_DOWN, frame.leftY()), nanos);
		int sx = Integer.signum(direction(frame, PadButton.DPAD_LEFT, PadButton.DPAD_RIGHT, frame.leftX())) * dx;
		int sy = Integer.signum(direction(frame, PadButton.DPAD_UP, PadButton.DPAD_DOWN, frame.leftY())) * dy;
		return switch (stage) {
			case CAROUSEL -> carousel(pressed, sx);
			case SETUP -> setup(pressed, sx, sy);
			case MORE -> more(pressed, sy);
		};
	}

	private Outcome more(int pressed, int dy) {
		List<MoreEntry> entries = moreEntries();
		int before = moreFocus;
		moreFocus = Math.floorMod(moreFocus + dy, entries.size());
		if (moreFocus != before) {
			HearthSounds.play(HearthSounds.Ui.TICK);
		}
		if (down(pressed, PadButton.B)) {
			if (moreOnly) {
				chosen = keep;
				return Outcome.CONFIRMED;
			}
			stage = Stage.CAROUSEL;
			return Outcome.NONE;
		}
		if (down(pressed, PadButton.A)) {
			HearthSounds.play(HearthSounds.Ui.KNOCK);
			return entries.get(moreFocus) == MoreEntry.CONTROLS ? Outcome.CONTROLS : Outcome.MANAGE;
		}
		return Outcome.NONE;
	}

	private Outcome carousel(int pressed, int dx) {
		for (int i = 0; i < Math.abs(dx); i++) {
			if (dx > 0) {
				carousel.next();
			} else {
				carousel.previous();
			}
		}
		if (down(pressed, PadButton.Y)) {
			carousel.next();
		}
		if (dx != 0 || down(pressed, PadButton.Y)) {
			HearthSounds.play(HearthSounds.Ui.PAGE);
		}
		if (down(pressed, PadButton.X) && moreAllowed) {
			stage = Stage.MORE;
			moreFocus = 0;
			HearthSounds.play(HearthSounds.Ui.TICK);
			return Outcome.NONE;
		}
		if (down(pressed, PadButton.B)) {
			if (keep != null) {
				// Changing profile: B backs out and keeps the one the player had.
				chosen = keep;
				return Outcome.CONFIRMED;
			}
			return Outcome.LEAVE;
		}
		if (down(pressed, PadButton.A)) {
			HearthSounds.play(HearthSounds.Ui.KNOCK);
			if (carousel.current() instanceof ProfileCarousel.Entry.Profile(GuestProfile profile)) {
				chosen = profile;
				return Outcome.CONFIRMED;
			}
			startSetup();
		}
		return Outcome.NONE;
	}

	private Outcome setup(int pressed, int dx, int dy) {
		switch (setup.step()) {
			case NAME -> {
				if (pressed != 0 || dx != 0 || dy != 0) {
					KeyboardFocus.used(this);
				}
				if (dx != 0 || dy != 0) {
					keyboard.move(dx, dy);
					keyboardMoved = true;
					HearthSounds.play(HearthSounds.Ui.TICK);
				}
				if (down(pressed, PadButton.B)) {
					backOutOfSetup();
					return Outcome.NONE;
				}
				if (down(pressed, PadButton.A)) {
					if (keyboard.suggested() && !keyboardMoved) {
						acceptName();
					} else {
						keyboard.type();
						HearthSounds.play(HearthSounds.Ui.KEY);
					}
				}
				if (down(pressed, PadButton.X)) {
					keyboard.delete();
					HearthSounds.play(HearthSounds.Ui.KEY_DELETE);
				}
				if (down(pressed, PadButton.Y)) {
					keyboard.space();
					HearthSounds.play(HearthSounds.Ui.KEY);
				}
				if (down(pressed, PadButton.LEFT_BUMPER)) {
					keyboard.previousLayer();
					HearthSounds.play(HearthSounds.Ui.PAGE);
				}
				if (down(pressed, PadButton.RIGHT_BUMPER)) {
					keyboard.nextLayer();
					HearthSounds.play(HearthSounds.Ui.PAGE);
				}
				if (down(pressed, PadButton.START)) {
					acceptName();
				}
			}
			case SKIN -> {
				for (int i = 0; i < Math.abs(dx); i++) {
					if (dx > 0) {
						setup.nextSkin();
					} else {
						setup.previousSkin();
					}
				}
				if (dx != 0) {
					HearthSounds.play(HearthSounds.Ui.PAGE);
				}
				if (down(pressed, PadButton.A) || down(pressed, PadButton.START)) {
					HearthSounds.play(HearthSounds.Ui.KNOCK);
					setup.next();
				} else if (down(pressed, PadButton.B)) {
					setup.back();
				}
			}
			case COLOR -> {
				for (int i = 0; i < Math.abs(dx); i++) {
					if (dx > 0) {
						setup.nextColor();
					} else {
						setup.previousColor();
					}
				}
				if (dx != 0) {
					HearthSounds.play(HearthSounds.Ui.PAGE);
				}
				if (down(pressed, PadButton.A) || down(pressed, PadButton.START)) {
					HearthSounds.play(HearthSounds.Ui.KNOCK);
					// Stays on this step: if the party refuses the profile, the card goes back to the name.
					chosen = setup.build(UUID.randomUUID(), keyboard.text(), controllerGuid);
					return Outcome.CONFIRMED;
				} else if (down(pressed, PadButton.B)) {
					setup.back();
				}
			}
			case DONE -> {
			}
		}
		return Outcome.NONE;
	}

	private void acceptName() {
		String name = keyboard.text();
		if (!keyboard.canConfirm()) {
			nameProblem = "3 to 16 letters, numbers or _";
		} else if (!setup.nameAvailable(name)) {
			nameProblem = name + " is taken";
		} else {
			nameProblem = null;
			HearthSounds.play(HearthSounds.Ui.KNOCK);
			setup.next();
		}
	}

	private void startSetup() {
		setup = newSetup.get();
		keyboard = KeyboardModel.forName(setup.suggestedName());
		keyboardMoved = false;
		nameProblem = null;
		stage = Stage.SETUP;
		KeyboardFocus.used(this);
	}

	/** Back out of quick setup to the carousel. */
	private void backOutOfSetup() {
		stage = Stage.CAROUSEL;
		setup = null;
		keyboard = null;
	}

	// ---- Player 1's PC keyboard on the name keyboard (Spec E section 3.6) ----

	@Override
	public boolean keyboardOpen() {
		return stage == Stage.SETUP && setup != null && setup.step() == QuickSetup.Step.NAME;
	}

	@Override
	public boolean typeChar(char c) {
		return keyboard.typeChar(c);
	}

	@Override
	public void backspace() {
		keyboard.delete();
	}

	@Override
	public void enter() {
		acceptName();
	}

	@Override
	public void escape() {
		backOutOfSetup();
	}

	/**
	 * What this card shows, as a profile, for when the world opens before the player confirms: the carousel's profile,
	 * or a new one from quick setup as it stands (its suggested name if the typed one can't be used).
	 */
	public GuestProfile autoConfirm() {
		if (keep != null) {
			// Changing profile when the world opened: keep the one the player had.
			chosen = keep;
			return keep;
		}
		if (stage != Stage.SETUP && carousel.current() instanceof ProfileCarousel.Entry.Profile(GuestProfile profile)) {
			chosen = profile;
			return profile;
		}
		QuickSetup s = setup != null ? setup : newSetup.get();
		String name = keyboard != null && keyboard.canConfirm() && s.nameAvailable(keyboard.text()) ? keyboard.text()
				: s.suggestedName();
		chosen = s.build(UUID.randomUUID(), name, controllerGuid);
		return chosen;
	}

	/** A fresh profile with quick setup's suggested name (names are checked live when the setup is made). */
	public GuestProfile autoConfirmFresh() {
		QuickSetup s = newSetup.get();
		chosen = s.build(UUID.randomUUID(), s.suggestedName(), controllerGuid);
		return chosen;
	}

	/**
	 * The party refused the profile (another seat plays it, or its name was just taken): stay open and say why. A new
	 * profile goes back to its name.
	 */
	public void reject(String reason) {
		chosen = null;
		if (stage == Stage.SETUP && setup != null) {
			while (setup.back()) {
				// back to NAME
			}
			keyboardMoved = true;
			nameProblem = reason;
		} else {
			problem = reason;
		}
	}

	/** Why the carousel's pick was refused, or null. */
	public String problem() {
		return problem;
	}

	private static int direction(InputFrame frame, PadButton negative, PadButton positive, float axis) {
		if (frame.pressed(negative) || axis < -STICK) {
			return -1;
		}
		if (frame.pressed(positive) || axis > STICK) {
			return 1;
		}
		return 0;
	}

	private static boolean down(int pressed, PadButton button) {
		return (pressed & InputFrame.buttons(button)) != 0;
	}

	/** Mid-game: no More on this card. */
	public void noMore() {
		moreAllowed = false;
	}

	/** More's entries: Controls for a profile (not for New), then Manage profiles. */
	public List<MoreEntry> moreEntries() {
		return moreProfile() != null ? List.of(MoreEntry.CONTROLS, MoreEntry.MANAGE) : List.of(MoreEntry.MANAGE);
	}

	public MoreEntry moreFocus() {
		List<MoreEntry> entries = moreEntries();
		return entries.get(Math.min(moreFocus, entries.size() - 1));
	}

	/** The profile More is about: the ready seat's, or the one the carousel shows (null on New). */
	public GuestProfile moreProfile() {
		if (moreOnly) {
			return keep;
		}
		return carousel.current() instanceof ProfileCarousel.Entry.Profile(GuestProfile profile) ? profile : null;
	}

	/** Buttons held now count as already pressed (back from a screen the card opened). */
	public void ignoreHeld() {
		lastButtons = -1;
	}

	/** A profile changed elsewhere (its controls saved): picking or keeping it uses the new copy. */
	public void replaceProfile(GuestProfile updated) {
		carousel.replace(updated);
		if (keep != null && keep.id().equals(updated.id())) {
			keep = updated;
		}
	}

	/** A profile was deleted: the carousel drops it. */
	public void removeProfile(UUID profileId) {
		carousel.remove(profileId);
	}

	/** Whether this card is changing a ready seat's profile (B keeps the old one) rather than a new seat's. */
	public boolean changing() {
		return keep != null;
	}

	public int seat() {
		return seat;
	}

	public Stage stage() {
		return stage;
	}

	public ProfileCarousel carousel() {
		return carousel;
	}

	/** Quick setup, while making a new profile; otherwise null. */
	public QuickSetup setup() {
		return setup;
	}

	/** The name keyboard, while making a new profile; otherwise null. */
	public KeyboardModel keyboard() {
		return keyboard;
	}

	/** Why the typed name can't be used, or null. */
	public String nameProblem() {
		return nameProblem;
	}

	/** The profile picked or made, once {@link Outcome#CONFIRMED}. */
	public GuestProfile chosen() {
		return chosen;
	}
}
