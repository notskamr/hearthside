package dev.hearthside.core.input;

/**
 * Text glyphs for controls, matching what is printed on each controller family. Face buttons are
 * named by position (A = bottom), so a Nintendo pad's bottom button reads "B".
 */
public final class ControlLabels {
	private ControlLabels() {}

	public static String glyph(Control control, PadStyle style) {
		return switch (control) {
			case A -> pick(style, "A", "✕", "B");
			case B -> pick(style, "B", "○", "A");
			case X -> pick(style, "X", "□", "Y");
			case Y -> pick(style, "Y", "△", "X");
			case LEFT_BUMPER -> pick(style, "LB", "L1", "L");
			case RIGHT_BUMPER -> pick(style, "RB", "R1", "R");
			case LEFT_TRIGGER -> pick(style, "LT", "L2", "ZL");
			case RIGHT_TRIGGER -> pick(style, "RT", "R2", "ZR");
			case LEFT_STICK -> pick(style, "LS", "L3", "LS");
			case RIGHT_STICK -> pick(style, "RS", "R3", "RS");
			case START -> pick(style, "START", "OPTIONS", "+");
			case BACK -> pick(style, "BACK", "SHARE", "−");
			case GUIDE -> pick(style, "HOME", "PS", "HOME");
			case DPAD_UP -> "↑";
			case DPAD_DOWN -> "↓";
			case DPAD_LEFT -> "←";
			case DPAD_RIGHT -> "→";
		};
	}

	private static String pick(PadStyle style, String xbox, String playstation, String nintendo) {
		return switch (style) {
			case PLAYSTATION -> playstation;
			case NINTENDO -> nintendo;
			case XBOX, GENERIC -> xbox;
		};
	}
}
