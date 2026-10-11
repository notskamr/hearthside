package dev.hearthside.core.input;

import java.util.EnumSet;
import java.util.Set;

/**
 * Hides controls that were already held when a menu closed, until they are released. Otherwise
 * closing the inventory with B would leave the player sneaking, or pressing A on a closing widget
 * would jump. Sticks are never hidden.
 */
public final class HeldSuppression {
	private final Set<Control> suppressed = EnumSet.noneOf(Control.class);

	/** Starts hiding every control that is down in {@code frame}. */
	public void suppressHeld(InputFrame frame) {
		suppressed.clear();
		for (Control control : Control.values()) {
			if (control.isDown(frame)) {
				suppressed.add(control);
			}
		}
	}

	/** {@code frame} without suppressed controls; a control stops being suppressed once released. */
	public InputFrame filter(InputFrame frame) {
		if (suppressed.isEmpty()) {
			return frame;
		}
		suppressed.removeIf(control -> !control.isDown(frame));
		int buttons = frame.buttons();
		float leftTrigger = frame.leftTrigger();
		float rightTrigger = frame.rightTrigger();
		for (Control control : suppressed) {
			switch (control) {
				case LEFT_TRIGGER -> leftTrigger = 0;
				case RIGHT_TRIGGER -> rightTrigger = 0;
				default -> buttons &= ~PadButton.valueOf(control.name()).mask();
			}
		}
		return new InputFrame(frame.sequence(), buttons, frame.leftX(), frame.leftY(), frame.rightX(), frame.rightY(),
				leftTrigger, rightTrigger);
	}
}
