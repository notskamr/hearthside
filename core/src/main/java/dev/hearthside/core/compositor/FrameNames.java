package dev.hearthside.core.compositor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Names for the shared GPU memory behind frame slots. Guest and host both derive them from the slot table's path and
 * the generation in {@code FrameSlotsReady}, so nothing else has to travel. A restarted guest starts from a fresh
 * random generation: the host may still hold memory under its predecessor's names.
 */
public final class FrameNames {
	private FrameNames() {}

	/** The Windows kernel object name for slot {@code slot}'s memory. */
	public static String gpuMemory(String controlFile, int generation, int slot) {
		return "Local\\Hearthside-" + shortHash(controlFile.toLowerCase(Locale.ROOT)) + "-" + generation + "-" + slot;
	}

	/**
	 * The Windows kernel object name for the copy transport's pixel memory. Each generation has its own slot table, so
	 * its path alone tells generations apart.
	 */
	public static String pixels(String controlFile) {
		return "Local\\Hearthside-" + shortHash(controlFile.toLowerCase(Locale.ROOT)) + "-pixels";
	}

	/** A positive starting generation, different for each guest process. */
	public static int firstGeneration() {
		return ThreadLocalRandom.current().nextInt(1, 1 << 30);
	}

	private static String shortHash(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 8);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is always available", e);
		}
	}
}
