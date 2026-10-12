package dev.hearthside.core.config;

/**
 * How much of the machine a party asks for (Spec D §3.3), in config/hearthside.json as {@code "preset"}. {@link #AUTO}
 * is not a tuning of its own: each party session resolves it to one of the others ({@link PresetChooser}).
 */
public enum Preset {
	AUTO,
	/** Guests keep their own settings and the host's refresh rate. */
	QUALITY,
	/** As Quality, with guests' render distance capped at 8 (what every party got before presets). */
	BALANCED,
	/** For integrated GPUs and small machines: guests render smaller, lighter, nearer and at 45 fps. */
	PERFORMANCE
}
