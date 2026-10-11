package dev.hearthside.core.input;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A controller in a menu (Spec A section 7.1): the D-pad or left stick moves focus, A presses, B goes back, X opens
 * More. Directions repeat while held. Buttons already held on the first frame are ignored, so the press that opened a
 * screen doesn't also press something on it. Not thread-safe.
 */
public final class MenuNav {
	public enum Action { UP, DOWN, LEFT, RIGHT, ACCEPT, BACK, MORE }

	private static final float STICK = 0.5f;
	private final NavRepeater horizontal = new NavRepeater(Duration.ofMillis(300), Duration.ofMillis(80));
	private final NavRepeater vertical = new NavRepeater(Duration.ofMillis(300), Duration.ofMillis(80));
	private int lastButtons = -1;

	public List<Action> step(InputFrame frame, long nanos) {
		List<Action> actions = new ArrayList<>();
		int pressed = lastButtons == -1 ? 0 : frame.buttons() & ~lastButtons;
		lastButtons = frame.buttons();
		int x = direction(frame, PadButton.DPAD_LEFT, PadButton.DPAD_RIGHT, frame.leftX());
		int y = direction(frame, PadButton.DPAD_UP, PadButton.DPAD_DOWN, frame.leftY());
		if (horizontal.step(x, nanos) > 0) {
			actions.add(x < 0 ? Action.LEFT : Action.RIGHT);
		}
		if (vertical.step(y, nanos) > 0) {
			actions.add(y < 0 ? Action.UP : Action.DOWN);
		}
		if ((pressed & InputFrame.buttons(PadButton.A)) != 0) {
			actions.add(Action.ACCEPT);
		}
		if ((pressed & InputFrame.buttons(PadButton.B)) != 0) {
			actions.add(Action.BACK);
		}
		if ((pressed & InputFrame.buttons(PadButton.X)) != 0) {
			actions.add(Action.MORE);
		}
		return actions;
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
}
