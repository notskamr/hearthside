package dev.hearthside.fabric.client.host;

import dev.hearthside.core.config.SplitScreenConfig;
import dev.hearthside.fabric.HearthsideMod;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The host's split-screen settings on disk, {@code <gameDir>/config/hearthside.json}. Read when each party session
 * opens, so an edit applies to the next session (the next world opened, or the next co-op lobby). A missing file is
 * created with every setting at its default, and a file from before a setting existed gains it (keeping its own
 * values), so players can find them all; an unreadable one gives the defaults and is left as it is
 * ({@link SplitScreenConfig#loadOrCreate}).
 */
public final class SplitScreenConfigFile {
	private SplitScreenConfigFile() {}

	public static Path path(Path gameDir) {
		return gameDir.resolve("config").resolve("hearthside.json");
	}

	/** This game's split-screen settings; never fails, falling back to the defaults. */
	public static SplitScreenConfig load(Path gameDir) {
		Path file = path(gameDir);
		try {
			return SplitScreenConfig.loadOrCreate(file);
		} catch (IOException e) {
			HearthsideMod.LOGGER.warn("Could not read or write {}; the party uses the default split-screen settings", file, e);
			return SplitScreenConfig.DEFAULTS;
		}
	}
}
