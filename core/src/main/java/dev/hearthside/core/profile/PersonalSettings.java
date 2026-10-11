package dev.hearthside.core.profile;

/**
 * A player's own settings, applied to their game whichever controller they use.
 *
 * @param fov field of view in degrees (30–110)
 * @param guiScale GUI scale, 0 = automatic (0–6)
 * @param buttonHints show on-screen button hints
 * @param reduceMotion leave out non-essential animation
 * @param audioDevice the output device this player hears through, or {@code null} for the shared speakers
 * @param volume this player's volume (0–1)
 */
public record PersonalSettings(int fov, int guiScale, boolean buttonHints, boolean reduceMotion, boolean subtitles,
		String audioDevice, float volume) {
	public static final PersonalSettings DEFAULTS = new PersonalSettings(70, 0, true, false, false, null, 1f);

	public PersonalSettings {
		fov = Math.clamp(fov, 30, 110);
		guiScale = Math.clamp(guiScale, 0, 6);
		volume = Float.isNaN(volume) ? 1f : Math.clamp(volume, 0f, 1f);
	}
}
