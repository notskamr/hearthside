package dev.hearthside.core.session;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Prepares a satellite's own game folder. Fabric Loader always reads config from {@code <gameDir>/config}, so the
 * host's config is mirrored in before every launch, file by file; options are seeded once and then belong to the
 * satellite.
 *
 * <p>A guest's own mod settings survive: a mirrored file the guest's game has changed since it was copied (its Sodium
 * settings, say) is kept, and from then on is the guest's. Everything else follows the host, and new host files
 * arrive. What was last copied is remembered as hashes in {@value #MIRROR_FILE} in the satellite's folder. A file the
 * guest has that differs from the host's and was never copied (written by the guest's own game, or from before that
 * record existed) is the guest's too. Hearthside's own {@value #OWN_CONFIG} always follows the host: it is the host's
 * to decide.
 */
public final class SatelliteDirectories {
	/** The record of what was last mirrored, beside (not inside) the satellite's {@code config} folder. */
	static final String MIRROR_FILE = "config-mirror.txt";
	/** Hearthside's own settings, in {@code config}. */
	static final String OWN_CONFIG = "hearthside.json";

	private SatelliteDirectories() {}

	public static Path prepare(Path hostGameDir, int playerIndex) throws IOException {
		Path satellite = hostGameDir.resolve("hearthside").resolve("satellite-" + playerIndex);
		Files.createDirectories(satellite);

		Path hostConfig = hostGameDir.resolve("config");
		if (Files.isDirectory(hostConfig)) {
			mirror(hostConfig, satellite.resolve("config"), satellite.resolve(MIRROR_FILE));
		}

		Path hostOptions = hostGameDir.resolve("options.txt");
		Path satelliteOptions = satellite.resolve("options.txt");
		if (Files.isRegularFile(hostOptions) && !Files.exists(satelliteOptions)) {
			Files.copy(hostOptions, satelliteOptions);
		}
		return satellite;
	}

	/** Copies each host file the guest hasn't made its own, then records what the guest's copies now come from. */
	private static void mirror(Path source, Path target, Path record) throws IOException {
		Map<String, String> mirrored = readRecord(record);
		Map<String, String> next = new TreeMap<>();
		Files.walkFileTree(source, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String name = source.relativize(file).toString().replace('\\', '/');
				Path copy = target.resolve(name);
				String host = hash(file);
				String guest = Files.isRegularFile(copy) ? hash(copy) : null;
				String last = mirrored.get(name);
				if (host.equals(guest)) {
					next.put(name, host);
				} else if (guest == null || guest.equals(last) || name.equals(OWN_CONFIG)) {
					// Missing, an untouched copy of an older host file, or Hearthside's own: the host's.
					Files.createDirectories(copy.getParent());
					Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
					next.put(name, host);
				} else if (last != null) {
					// The guest changed it: theirs stays, and stays theirs however often the host's changes.
					next.put(name, last);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		writeRecord(record, next);
	}

	private static Map<String, String> readRecord(Path record) throws IOException {
		Map<String, String> hashes = new HashMap<>();
		if (!Files.isRegularFile(record)) {
			return hashes;
		}
		for (String line : Files.readAllLines(record, StandardCharsets.UTF_8)) {
			int space = line.indexOf(' ');
			if (!line.startsWith("#") && space > 0) {
				hashes.put(line.substring(space + 1), line.substring(0, space));
			}
		}
		return hashes;
	}

	private static void writeRecord(Path record, Map<String, String> hashes) throws IOException {
		Path temporary = record.resolveSibling(record.getFileName() + ".tmp");
		try (BufferedWriter out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
			for (String line : List.of(
					"# Hearthside: the host's config files as last copied into this guest's config folder (SHA-256).",
					"# A file the guest has changed since is the guest's own and is no longer copied over.")) {
				out.write(line);
				out.newLine();
			}
			for (Map.Entry<String, String> entry : hashes.entrySet()) {
				out.write(entry.getValue() + " " + entry.getKey());
				out.newLine();
			}
		}
		try {
			Files.move(temporary, record, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temporary, record, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static String hash(Path file) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Every Java has SHA-256", e);
		}
		try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
			in.transferTo(OutputStream.nullOutputStream());
		}
		return HexFormat.of().formatHex(digest.digest());
	}
}
