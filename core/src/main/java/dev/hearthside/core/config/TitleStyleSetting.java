package dev.hearthside.core.config;

/**
 * Which title screen player 1 sees (Spec E §3.5), in config/hearthside.json as {@code "titleStyle"}:
 * {@code "hearthside"} or {@code "vanilla"}. Applies the next time the title screen opens.
 */
public enum TitleStyleSetting {
	/** Hearthside's Cozy-hearth title screen (the default). */
	HEARTHSIDE,
	/** Minecraft's own title screen, untouched. */
	VANILLA
}
