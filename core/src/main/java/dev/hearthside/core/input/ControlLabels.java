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
			case START -> pick(style, "≡", "OPTIONS", "+");
			case BACK -> pick(style, "⧉", "CREATE", "−");
			case GUIDE -> pick(style, "HOME", "PS", "HOME");
			case DPAD_UP -> "↑";
			case DPAD_DOWN -> "↓";
			case DPAD_LEFT -> "←";
			case DPAD_RIGHT -> "→";
		};
	}

	/** The word for a control in prompts ("press Menu"), as the player's controller calls it. Never "START". */
	public static String name(Control control, PadStyle style) {
		return switch (control) {
			case A -> pick(style, "A", "Cross", "B");
			case B -> pick(style, "B", "Circle", "A");
			case X -> pick(style, "X", "Square", "Y");
			case Y -> pick(style, "Y", "Triangle", "X");
			case START -> pick(style, "Menu", "Options", "+");
			case BACK -> pick(style, "View", "Create", "−");
			case GUIDE -> switch (style) {
				case XBOX -> "Xbox";
				case PLAYSTATION -> "PS";
				case NINTENDO, GENERIC -> "Home";
			};
			case DPAD_UP -> "Up";
			case DPAD_DOWN -> "Down";
			case DPAD_LEFT -> "Left";
			case DPAD_RIGHT -> "Right";
			default -> glyph(control, style);
		};
	}

	/** The button-icon sprite for a control: {@code "<family>/<control>"}, lowercase, e.g. {@code "xbox/a"}. */
	public static String glyphId(Control control, PadStyle style) {
		return style.name().toLowerCase(java.util.Locale.ROOT) + "/" + control.name().toLowerCase(java.util.Locale.ROOT);
	}

	private static String pick(PadStyle style, String xbox, String playstation, String nintendo) {
		return switch (style) {
			case PLAYSTATION -> playstation;
			case NINTENDO -> nintendo;
			case XBOX, GENERIC -> xbox;
		};
	}
}
