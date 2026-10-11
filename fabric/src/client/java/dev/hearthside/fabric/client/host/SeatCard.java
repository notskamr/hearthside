package dev.hearthside.fabric.client.host;

import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.NavRepeater;
import dev.hearthside.core.input.PadButton;
import dev.hearthside.core.profile.GuestProfile;
import dev.hearthside.core.profile.ProfileCarousel;
import dev.hearthside.core.profile.QuickSetup;
import dev.hearthside.core.ui.KeyboardModel;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One seat's lobby card while its player picks or creates a profile (Spec A sections 3.1, 5.2): the profile carousel,
 * then for New, quick setup with the on-screen keyboard. That seat's controller drives only its card. Render thread.
 */
public final class SeatCard {
	public enum Outcome { NONE, CONFIRMED, LEAVE }

	public enum Stage { CAROUSEL, SETUP }

	private static final float STICK = 0.5f;

	private final int seat;
	private final ProfileCarousel carousel;
	private final Supplier<QuickSetup> newSetup;
	private final String controllerGuid;
	private final NavRepeater horizontal = new NavRepeater(Duration.ofMillis(300), Duration.ofMillis(80));
	private final NavRepeater vertical = new NavRepeater(Duration.ofMillis(300), Duration.ofMillis(80));
	private Stage stage = Stage.CAROUSEL;
	private QuickSetup setup;
	private KeyboardModel keyboard;
	/** Whether the player has moved over the keyboard: until then A keeps the suggested name ("A, A, A"). */
	private boolean keyboardMoved;
	private String nameProblem;
	private GuestProfile chosen;
	/** Everything counts as already held when the card opens: the A that took the seat must not also confirm. */
	private int lastButtons = -1;

	SeatCard(int seat, ProfileCarousel carousel, Supplier<QuickSetup> newSetup, String controllerGuid) {
		this.seat = seat;
		this.carousel = carousel;
		this.newSetup = newSetup;
		this.controllerGuid = controllerGuid;
	}

	/** This seat's controller, this frame. */
	public Outcome accept(InputFrame frame, long nanos) {
		int pressed = frame.buttons() & ~lastButtons;
		lastButtons = frame.buttons();
		int dx = horizontal.step(direction(frame, PadButton.DPAD_LEFT, PadButton.DPAD_RIGHT, frame.leftX()), nanos);
		int dy = vertical.step(direction(frame, PadButton.DPAD_UP, PadButton.DPAD_DOWN, frame.leftY()), nanos);
		int sx = Integer.signum(direction(frame, PadButton.DPAD_LEFT, PadButton.DPAD_RIGHT, frame.leftX())) * dx;
		int sy = Integer.signum(direction(frame, PadButton.DPAD_UP, PadButton.DPAD_DOWN, frame.leftY())) * dy;
		return stage == Stage.CAROUSEL ? carousel(pressed, sx) : setup(pressed, sx, sy);
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
		if (down(pressed, PadButton.B)) {
			return Outcome.LEAVE;
		}
		if (down(pressed, PadButton.A)) {
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
				if (dx != 0 || dy != 0) {
					keyboard.move(dx, dy);
					keyboardMoved = true;
				}
				if (down(pressed, PadButton.B)) {
					// Back out of quick setup to the carousel.
					stage = Stage.CAROUSEL;
					setup = null;
					keyboard = null;
					return Outcome.NONE;
				}
				if (down(pressed, PadButton.A)) {
					if (keyboard.suggested() && !keyboardMoved) {
						acceptName();
					} else {
						keyboard.type();
					}
				}
				if (down(pressed, PadButton.X)) {
					keyboard.delete();
				}
				if (down(pressed, PadButton.Y)) {
					keyboard.space();
				}
				if (down(pressed, PadButton.LEFT_BUMPER)) {
					keyboard.previousLayer();
				}
				if (down(pressed, PadButton.RIGHT_BUMPER)) {
					keyboard.nextLayer();
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
				if (down(pressed, PadButton.A) || down(pressed, PadButton.START)) {
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
				if (down(pressed, PadButton.A) || down(pressed, PadButton.START)) {
					setup.next();
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
			setup.next();
		}
	}

	private void startSetup() {
		setup = newSetup.get();
		keyboard = KeyboardModel.forName(setup.suggestedName());
		keyboardMoved = false;
		nameProblem = null;
		stage = Stage.SETUP;
	}

	/**
	 * What this card shows, as a profile, for when the world opens before the player confirms: the carousel's profile,
	 * or a new one from quick setup as it stands (its suggested name if the typed one can't be used).
	 */
	public GuestProfile autoConfirm() {
		if (stage == Stage.CAROUSEL && carousel.current() instanceof ProfileCarousel.Entry.Profile(GuestProfile profile)) {
			chosen = profile;
			return profile;
		}
		QuickSetup s = setup != null ? setup : newSetup.get();
		String name = keyboard != null && keyboard.canConfirm() && s.nameAvailable(keyboard.text()) ? keyboard.text()
				: s.suggestedName();
		chosen = s.build(UUID.randomUUID(), name, controllerGuid);
		return chosen;
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
