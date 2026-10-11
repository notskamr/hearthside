package dev.hearthside.core.ui;

import java.time.Duration;

/** A one-shot eased animation (Spec A section 4.4), done at once when motion is reduced or before it starts. */
public final class Tween {
	private final long lengthNanos;
	private long startNanos;
	private boolean started;

	public Tween(Duration length) {
		this.lengthNanos = length.toNanos();
	}

	public void start(long nanos) {
		startNanos = nanos;
		started = true;
	}

	/** 0 at the start, 1 when done, eased out. */
	public double progress(long nanos, boolean reduceMotion) {
		if (!started || reduceMotion || lengthNanos <= 0) {
			return 1;
		}
		return easeOutCubic((nanos - startNanos) / (double) lengthNanos);
	}

	public static double easeOutCubic(double t) {
		double u = 1 - Math.clamp(t, 0, 1);
		return 1 - u * u * u;
	}
}
