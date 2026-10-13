package dev.hearthside.core.layout;

/**
 * How views are edged (Spec E §3.5, plan R2), in config/hearthside.json as {@code "borders"}: {@code "colour"},
 * {@code "thin"} or {@code "off"}.
 */
public enum BorderStyle {
	/** Each view edged in its player's colour. */
	COLOUR,
	/** A thin divider between views (the default). */
	THIN,
	/** No border at all. */
	OFF
}
