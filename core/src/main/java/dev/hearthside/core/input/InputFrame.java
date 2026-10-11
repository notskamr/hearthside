package dev.hearthside.core.input;

/**
 * One snapshot of a controller's state. Sticks are clamped to [-1, 1], triggers to [0, 1], and NaN
 * becomes 0, so a bad reading can never push the game out of range.
 *
 * @param sequence increases with every frame from the same controller
 * @param buttons bit set of {@link PadButton#mask()} values
 */
public record InputFrame(long sequence, int buttons, float leftX, float leftY, float rightX, float rightY,
		float leftTrigger, float rightTrigger) {
	public static final InputFrame NEUTRAL = new InputFrame(0, 0, 0, 0, 0, 0, 0, 0);

	public InputFrame {
		leftX = clamp(leftX, -1);
		leftY = clamp(leftY, -1);
		rightX = clamp(rightX, -1);
		rightY = clamp(rightY, -1);
		leftTrigger = clamp(leftTrigger, 0);
		rightTrigger = clamp(rightTrigger, 0);
	}

	public boolean pressed(PadButton button) {
		return (buttons & button.mask()) != 0;
	}

	public static int buttons(PadButton... pressed) {
		int bits = 0;
		for (PadButton b : pressed) {
			bits |= b.mask();
		}
		return bits;
	}

	private static float clamp(float value, float min) {
		return Float.isNaN(value) ? 0f : Math.clamp(value, min, 1f);
	}
}
