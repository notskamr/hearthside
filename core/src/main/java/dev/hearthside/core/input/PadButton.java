package dev.hearthside.core.input;

/**
 * Gamepad buttons, in SDL/Xbox naming.
 *
 * <p>Append-only: the ordinal is the bit position on the wire.
 */
public enum PadButton {
	A, B, X, Y,
	LEFT_BUMPER, RIGHT_BUMPER,
	BACK, START, GUIDE,
	LEFT_STICK, RIGHT_STICK,
	DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT;

	public int mask() {
		return 1 << ordinal();
	}
}
