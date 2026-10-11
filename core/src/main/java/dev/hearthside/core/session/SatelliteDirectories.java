package dev.hearthside.core.session;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Prepares a satellite's own game folder. Fabric Loader always reads config from
 * {@code <gameDir>/config}, so the host's config is mirrored in before every launch; options are
 * seeded once and then belong to the satellite.
 */
public final class SatelliteDirectories {
	private SatelliteDirectories() {}

	public static Path prepare(Path hostGameDir, int playerIndex) throws IOException {
		Path satellite = hostGameDir.resolve("hearthside").resolve("satellite-" + playerIndex);
		Files.createDirectories(satellite);

		Path hostConfig = hostGameDir.resolve("config");
		if (Files.isDirectory(hostConfig)) {
			copyTree(hostConfig, satellite.resolve("config"));
		}

		Path hostOptions = hostGameDir.resolve("options.txt");
		Path satelliteOptions = satellite.resolve("options.txt");
		if (Files.isRegularFile(hostOptions) && !Files.exists(satelliteOptions)) {
			Files.copy(hostOptions, satelliteOptions);
		}
		return satellite;
	}

	private static void copyTree(Path source, Path target) throws IOException {
		Files.walkFileTree(source, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(target.resolve(source.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
