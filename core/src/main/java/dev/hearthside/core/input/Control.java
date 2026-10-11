package dev.hearthside.core.input;

/** Anything on a controller an action can be bound to. Names are stored in profile files. */
public enum Control {
	A(PadButton.A), B(PadButton.B), X(PadButton.X), Y(PadButton.Y),
	LEFT_BUMPER(PadButton.LEFT_BUMPER), RIGHT_BUMPER(PadButton.RIGHT_BUMPER),
	BACK(PadButton.BACK), START(PadButton.START), GUIDE(PadButton.GUIDE),
	LEFT_STICK(PadButton.LEFT_STICK), RIGHT_STICK(PadButton.RIGHT_STICK),
	DPAD_UP(PadButton.DPAD_UP), DPAD_DOWN(PadButton.DPAD_DOWN), DPAD_LEFT(PadButton.DPAD_LEFT), DPAD_RIGHT(PadButton.DPAD_RIGHT),
	LEFT_TRIGGER(null), RIGHT_TRIGGER(null);

	public static final float TRIGGER_THRESHOLD = 0.5f;

	private final PadButton button;

	Control(PadButton button) {
		this.button = button;
	}

	public boolean isDown(InputFrame frame) {
		return switch (this) {
			case LEFT_TRIGGER -> frame.leftTrigger() > TRIGGER_THRESHOLD;
			case RIGHT_TRIGGER -> frame.rightTrigger() > TRIGGER_THRESHOLD;
			default -> frame.pressed(button);
		};
	}
}
