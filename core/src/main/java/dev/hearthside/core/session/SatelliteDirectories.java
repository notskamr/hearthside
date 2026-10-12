package dev.hearthside.core.session;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger.Level;
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
 *
 * <p>Mirroring never stops a guest joining over its record (a record that can't be read or written is logged), never
 * copies a temporary {@code *.tmp} file, and never leaves a half-copied file: each copy is moved into place whole.
 */
public final class SatelliteDirectories {
	/** The record of what was last mirrored, beside (not inside) the satellite's {@code config} folder. */
	static final String MIRROR_FILE = "config-mirror.txt";
	/** Hearthside's own settings, in {@code config}. */
	static final String OWN_CONFIG = "hearthside.json";
	/** A file being written: a copy or a record before it is moved into place, or another program's save. */
	private static final String TEMPORARY = ".tmp";
	private static final System.Logger LOG = System.getLogger(SatelliteDirectories.class.getName());

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

	/** The record of what was last mirrored into this satellite's {@code config} folder. */
	public static Path mirrorRecord(Path satellite) {
		return satellite.resolve(MIRROR_FILE);
	}

	/**
	 * Copies each host file the guest hasn't made its own, then records what the guest's copies now come from. A
	 * temporary file ({@code *.tmp}: another program's save in progress, or one it left behind) is never copied.
	 */
	private static void mirror(Path source, Path target, Path record) throws IOException {
		Map<String, String> mirrored = readRecord(record);
		Map<String, String> next = new TreeMap<>();
		Files.walkFileTree(source, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String name = source.relativize(file).toString().replace('\\', '/');
				if (name.endsWith(TEMPORARY)) {
					return FileVisitResult.CONTINUE;
				}
				Path copy = target.resolve(name);
				String host = hash(file);
				String guest = Files.isRegularFile(copy) ? hash(copy) : null;
				String last = mirrored.get(name);
				if (host.equals(guest)) {
					next.put(name, host);
				} else if (guest == null || guest.equals(last) || name.equals(OWN_CONFIG)) {
					// Missing, an untouched copy of an older host file, or Hearthside's own: the host's.
					Files.createDirectories(copy.getParent());
					copyIntoPlace(file, copy);
					next.put(name, host);
				} else if (last != null) {
					// The guest changed it: theirs stays, and stays theirs however often the host's changes.
					next.put(name, last);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		try {
			writeRecord(record, next);
		} catch (IOException e) {
			// Every copy is in place, so the guest can start. Without the record, its next launch keeps (as the guest's)
			// every file that differs from the host's.
			LOG.log(Level.WARNING, "Could not record what was copied into " + target + "; the guest starts anyway", e);
		}
	}

	/**
	 * Copies {@code file} to a temporary file beside {@code copy}, then moves it into place: a copy cut short (a crash,
	 * a full disk) never leaves a half-written file that would look like the guest's own change.
	 */
	private static void copyIntoPlace(Path file, Path copy) throws IOException {
		Path temporary = copy.resolveSibling(copy.getFileName() + TEMPORARY);
		try {
			Files.copy(file, temporary, StandardCopyOption.REPLACE_EXISTING);
			moveIntoPlace(temporary, copy);
		} catch (IOException | RuntimeException e) {
			deleteAfterFailure(temporary, e);
			throw e;
		}
	}

	/** The hashes in a record; none if there is no record or it can't be read (as on a first launch). */
	private static Map<String, String> readRecord(Path record) {
		Map<String, String> hashes = new HashMap<>();
		if (!Files.isRegularFile(record)) {
			return hashes;
		}
		try {
			for (String line : Files.readAllLines(record, StandardCharsets.UTF_8)) {
				int space = line.indexOf(' ');
				if (!line.startsWith("#") && space > 0) {
					hashes.put(line.substring(space + 1), line.substring(0, space));
				}
			}
		} catch (IOException e) {
			LOG.log(Level.WARNING, "Could not read " + record + "; files that differ from the host's stay the guest's", e);
			hashes.clear();
		}
		return hashes;
	}

	private static void writeRecord(Path record, Map<String, String> hashes) throws IOException {
		Path temporary = record.resolveSibling(record.getFileName() + TEMPORARY);
		try {
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
			moveIntoPlace(temporary, record);
		} catch (IOException | RuntimeException e) {
			deleteAfterFailure(temporary, e);
			throw e;
		}
	}

	private static void moveIntoPlace(Path temporary, Path target) throws IOException {
		try {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Removes the temporary file a failed write or move left (only a file: never a folder someone else put there). */
	private static void deleteAfterFailure(Path temporary, Exception failure) {
		try {
			if (Files.isRegularFile(temporary)) {
				Files.delete(temporary);
			}
		} catch (IOException e) {
			failure.addSuppressed(e);
		}
	}

	/**
	 * A file's content as SHA-256. In a {@code .properties} file comments don't count: Iris, for one, rewrites its
	 * settings with the date in a comment each time a game starts, which would otherwise look like the guest's change.
	 */
	private static String hash(Path file) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Every Java has SHA-256", e);
		}
		if (file.getFileName().toString().endsWith(".properties")) {
			boolean continued = false;
			for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
				String text = line.strip();
				boolean comment = !continued && (text.isEmpty() || text.startsWith("#") || text.startsWith("!"));
				if (!comment) {
					digest.update((line + "\n").getBytes(StandardCharsets.ISO_8859_1));
				}
				continued = !comment && endsWithOddBackslashes(line);
			}
		} else {
			try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
				in.transferTo(OutputStream.nullOutputStream());
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	/** Whether a properties line goes on to the next: it ends in an odd number of backslashes. */
	private static boolean endsWithOddBackslashes(String line) {
		int n = 0;
		for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
			n++;
		}
		return n % 2 == 1;
	}
}
