package dev.hearthside.core;

import java.util.Locale;

/** Which part a running game instance plays in a Hearthside session. */
public enum Role {
	/** The player-1 game: owns the world, the controllers and the satellites. */
	HOST,
	/** A game launched by the host to show one additional player's view. */
	SATELLITE;

	/** JVM system property the host sets when launching a satellite. */
	public static final String PROPERTY = "hearthside.role";

	/** Parses {@link #PROPERTY}; a missing or blank value means {@link #HOST}. */
	public static Role fromProperty(String raw) {
		if (raw == null || raw.isBlank()) {
			return HOST;
		}
		try {
			return valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(
					"Unknown value for " + PROPERTY + ": '" + raw + "' (expected host or satellite)", e);
		}
	}
}
