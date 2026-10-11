package dev.hearthside.core.ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Embers drifting up from the bottom of the lobby (Spec A section 4.1). Positions are fractions of the area: x from
 * the left, y from the bottom. Deterministic for a seed, so frames and tests repeat exactly.
 */
public final class EmberField {
	/** Rise speed, in area heights per second. */
	public static final double MIN_RISE = 0.20, MAX_RISE = 0.35;
	/** Sideways sway, in area widths. */
	static final double SWAY = 0.02;

	public record Ember(double x, double y, double size, double alpha) {}

	private static final class Live {
		double baseX, y, rise, phase, size;
	}

	private final Random random;
	private final int max;
	private final List<Live> live = new ArrayList<>();
	private double untilSpawn;
	private double time;

	public EmberField(long seed, int max) {
		this.random = new Random(seed);
		this.max = max;
	}

	public void step(double seconds, boolean reduceMotion) {
		if (reduceMotion) {
			live.clear();
			untilSpawn = 0;
			return;
		}
		time += seconds;
		for (Iterator<Live> it = live.iterator(); it.hasNext(); ) {
			Live ember = it.next();
			ember.y += ember.rise * seconds;
			if (ember.y >= 1) {
				it.remove();
			}
		}
		// Spawn evenly so the field settles at about `max` embers.
		double interval = 2 / (MIN_RISE + MAX_RISE) / Math.max(1, max);
		untilSpawn -= seconds;
		while (untilSpawn <= 0 && live.size() < max) {
			Live ember = new Live();
			ember.baseX = random.nextDouble();
			ember.rise = MIN_RISE + random.nextDouble() * (MAX_RISE - MIN_RISE);
			ember.phase = random.nextDouble() * Math.PI * 2;
			ember.size = random.nextDouble() < 0.3 ? 2 : 1;
			live.add(ember);
			untilSpawn += interval;
		}
		// While full, don't bank spawns for later.
		untilSpawn = Math.max(untilSpawn, 0);
	}

	public List<Ember> embers() {
		List<Ember> out = new ArrayList<>(live.size());
		for (Live ember : live) {
			double x = Math.clamp(ember.baseX + SWAY * Math.sin(ember.phase + time * 2.1), 0, 1);
			// Fade out over the top third.
			double alpha = ember.y < 2 / 3.0 ? 1 : Math.max(0, (1 - ember.y) * 3);
			out.add(new Ember(x, ember.y, ember.size, alpha));
		}
		return out;
	}
}
