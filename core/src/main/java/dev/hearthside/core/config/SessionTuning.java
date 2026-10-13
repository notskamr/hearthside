package dev.hearthside.core.config;

import java.util.Objects;

/**
 * What a preset sets for one party session (Spec D §3.3). None of it is written to anyone's own settings.
 *
 * @param renderDistanceCap the most chunks a guest renders; {@code 0} means the guest's own setting
 * @param lightGraphics guests use light graphics: fast leaves, no clouds, fewer particles, entities at 75% distance,
 *     no biome blend
 * @param fpsCap guests' frame-rate cap; {@code 0} means the host's refresh rate
 * @param simulationDistance player 1's world's simulation distance; {@code 0} means its own setting
 * @param reducedResolution guests render at a reduced scale, which the compositor stretches back
 */
public record SessionTuning(int renderDistanceCap, boolean lightGraphics, int fpsCap, int simulationDistance,
		boolean reducedResolution) {
	private static final SessionTuning QUALITY = new SessionTuning(0, false, 0, 0, false);
	private static final SessionTuning BALANCED = new SessionTuning(8, false, 0, 0, false);
	private static final SessionTuning PERFORMANCE = new SessionTuning(6, true, 45, 6, true);

	/**
	 * The session's tuning under {@code resolved}, which Auto must already have become ({@link PresetChooser}). A
	 * number in {@code config}'s {@code guestRenderDistance}, 0 (no cap) included, wins over the preset's cap.
	 *
	 * @throws IllegalArgumentException for {@link Preset#AUTO}
	 */
	public static SessionTuning of(Preset resolved, SplitScreenConfig config) {
		Objects.requireNonNull(config, "config");
		SessionTuning preset = switch (resolved) {
			case QUALITY -> QUALITY;
			case BALANCED -> BALANCED;
			case PERFORMANCE -> PERFORMANCE;
			case AUTO -> throw new IllegalArgumentException("Auto is resolved to a preset first, by PresetChooser");
		};
		if (config.guestRenderDistance() == SplitScreenConfig.RENDER_DISTANCE_FROM_PRESET) {
			return preset;
		}
		return new SessionTuning(config.guestRenderDistance(), preset.lightGraphics, preset.fpsCap,
				preset.simulationDistance, preset.reducedResolution);
	}
}
