package dev.hearthside.fabric.client.host;

import dev.hearthside.core.config.SplitScreenConfig;
import dev.hearthside.fabric.HearthsideMod;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The host's split-screen settings on disk, {@code <gameDir>/config/hearthside.json}. Read when each party session
 * opens, so an edit applies to the next session. A missing file is created with the defaults; an unreadable one gives
 * the defaults and is left as it is.
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
			if (Files.notExists(file)) {
				Files.createDirectories(file.getParent());
				try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
						StandardOpenOption.WRITE)) {
					SplitScreenConfig.DEFAULTS.write(out);
				}
				return SplitScreenConfig.DEFAULTS;
			}
			try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				return SplitScreenConfig.read(in);
			}
		} catch (FileAlreadyExistsException e) {
			return load(gameDir); // created by someone else meanwhile: read theirs
		} catch (IOException e) {
			HearthsideMod.LOGGER.warn("Could not read or create {}; guests get the default memory and render distance",
					file, e);
			return SplitScreenConfig.DEFAULTS;
		}
	}
}
