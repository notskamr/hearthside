package dev.hearthside.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What {@link Preset#AUTO} becomes when a party session opens (Spec D §3.3): Performance for an integrated or unknown
 * GPU or a small memory, Balanced for a discrete GPU driving 3 or 4 players or a window above 1440p, Quality otherwise.
 * The reasons say why, for the log.
 */
public final class PresetChooser {
	/**
	 * Under this much memory is "less than 12 GB" (plan R7): Windows reports a 12 GB machine as about 11.8 GiB, an 8 GB
	 * one as under 8.
	 */
	static final long LITTLE_MEMORY_BYTES = 23L << 29; // 11.5 GiB
	/** A window with more pixels than 2560x1440 is big. */
	static final long BIG_WINDOW_PIXELS = 2560L * 1440;

	private PresetChooser() {}

	public enum GpuKind { INTEGRATED, DISCRETE, UNKNOWN }

	/**
	 * What the choice depends on.
	 *
	 * @param ramBytes total physical memory
	 * @param players how many play, player 1 included
	 * @param windowWidth player 1's window, in pixels
	 */
	public record Machine(GpuKind gpu, long ramBytes, int players, int windowWidth, int windowHeight) {
		public Machine {
			Objects.requireNonNull(gpu, "gpu");
		}
	}

	/** The preset Auto became, and every reason that led to it ("integrated GPU", "8 GB RAM"...). */
	public record Choice(Preset preset, List<String> reasons) {
		public Choice {
			Objects.requireNonNull(preset, "preset");
			reasons = List.copyOf(reasons);
		}
	}

	public static Choice choose(Machine m) {
		List<String> small = new ArrayList<>();
		switch (m.gpu()) {
			case INTEGRATED -> small.add("integrated GPU");
			case UNKNOWN -> small.add("unknown GPU");
			case DISCRETE -> {}
		}
		if (m.ramBytes() < LITTLE_MEMORY_BYTES) {
			small.add(memory(m));
		}
		if (!small.isEmpty()) {
			return new Choice(Preset.PERFORMANCE, small);
		}
		List<String> busy = new ArrayList<>();
		if (m.players() >= 3) {
			busy.add(m.players() + " players");
		}
		if ((long) m.windowWidth() * m.windowHeight() > BIG_WINDOW_PIXELS) {
			busy.add("window " + m.windowWidth() + "x" + m.windowHeight());
		}
		if (!busy.isEmpty()) {
			return new Choice(Preset.BALANCED, busy);
		}
		return new Choice(Preset.QUALITY, List.of("discrete GPU", memory(m)));
	}

	/** The memory in whole GiB, rounded, as players know it: 11.8 GiB is "12 GB RAM". */
	private static String memory(Machine m) {
		return Math.round((double) m.ramBytes() / (1L << 30)) + " GB RAM";
	}
}
