package dev.hearthside.fabric.client.host;

import dev.hearthside.core.config.SplitScreenConfig;
import dev.hearthside.fabric.HearthsideMod;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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

	/**
	 * The settings as the file holds them, without creating or completing it (the title screen's style, read each time it
	 * opens); never fails, falling back to the defaults.
	 */
	public static SplitScreenConfig peek(Path gameDir) {
		Path file = path(gameDir);
		try {
			return Files.exists(file) ? SplitScreenConfig.read(new StringReader(Files.readString(file)))
					: SplitScreenConfig.DEFAULTS;
		} catch (IOException | RuntimeException e) {
			HearthsideMod.LOGGER.warn("Could not read {}; using the default split-screen settings", file, e);
			return SplitScreenConfig.DEFAULTS;
		}
	}

	/** Writes this game's split-screen settings, such as an answer to remember; the file is never half-written. */
	public static void save(Path gameDir, SplitScreenConfig config) throws IOException {
		Path file = path(gameDir);
		Files.createDirectories(file.getParent());
		SplitScreenConfig.replace(file, config);
	}

	/**
	 * Player 1 changed their Split-screen settings from {@code before} to {@code next} (Spec E section 3.5): saved, and
	 * each changed field logged as the file writes it ({@code Hearthside split-screen applied twoPlayerLayout=stacked}).
	 * A file that can't be written is logged; the change still applies for this session.
	 *
	 * @return the changed fields, empty if nothing changed (then nothing is written)
	 */
	public static Map<String, String> apply(Path gameDir, SplitScreenConfig before, SplitScreenConfig next) {
		Map<String, String> changes = next.changesFrom(before);
		if (changes.isEmpty()) {
			return changes;
		}
		try {
			save(gameDir, next);
		} catch (IOException e) {
			HearthsideMod.LOGGER.warn("Could not save {}; the change applies until the game closes", path(gameDir), e);
		}
		changes.forEach((key, value) -> HearthsideMod.LOGGER.info("Hearthside split-screen applied {}={}", key, value));
		return changes;
	}
}
