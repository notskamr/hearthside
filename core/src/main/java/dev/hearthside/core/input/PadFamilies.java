package dev.hearthside.core.input;

/** Works out which family a controller belongs to, so prompts can use its own button names and icons. */
public final class PadFamilies {
	private static final int VENDOR_MICROSOFT = 0x045E, VENDOR_SONY = 0x054C, VENDOR_NINTENDO = 0x057E;

	private PadFamilies() {}

	/**
	 * @param sdlGamepadType SDL3's {@code SDL_GamepadType} for the controller
	 * @param vendorId the controller's USB vendor id, used when SDL doesn't know the type
	 */
	public static PadStyle fromSdl(int sdlGamepadType, int vendorId) {
		return switch (sdlGamepadType) {
			case 2, 3 -> PadStyle.XBOX; // Xbox 360, Xbox One / Series
			case 4, 5, 6 -> PadStyle.PLAYSTATION; // PS3, PS4, PS5
			case 7, 8, 9, 10 -> PadStyle.NINTENDO; // Switch Pro, Joy-Con left / right / pair
			case 0, 1 -> switch (vendorId) { // unknown or standard: fall back to the maker
				case VENDOR_MICROSOFT -> PadStyle.XBOX;
				case VENDOR_SONY -> PadStyle.PLAYSTATION;
				case VENDOR_NINTENDO -> PadStyle.NINTENDO;
				default -> PadStyle.GENERIC;
			};
			default -> PadStyle.GENERIC;
		};
	}
}
