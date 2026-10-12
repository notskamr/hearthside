package dev.hearthside.core.config;

/**
 * The frame rate a guest's game paces to: never faster than the host's display shows (Spec C), nor than the session's
 * frame-rate cap (Spec D section 3.3). A player who changes Max Framerate themselves mid-session keeps their choice
 * (plan Review Focus 2), still never above the display's rate.
 */
public final class FrameRatePacing {
	/** The player hasn't chosen a frame rate of their own this session. */
	public static final int NO_CHOICE = 0;

	private FrameRatePacing() {}

	/**
	 * @param refreshHz the host's display refresh rate
	 * @param presetCap the session's frame-rate cap; 0 for none
	 * @param playersChoice the Max Framerate the player set themselves this session, or {@link #NO_CHOICE}
	 */
	public static int limit(int refreshHz, int presetCap, int playersChoice) {
		if (playersChoice != NO_CHOICE) {
			return Math.min(refreshHz, playersChoice);
		}
		return presetCap > 0 ? Math.min(refreshHz, presetCap) : refreshHz;
	}
}
