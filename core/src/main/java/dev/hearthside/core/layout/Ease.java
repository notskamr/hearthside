package dev.hearthside.core.layout;

/** Easing curves for motion, over progress {@code t} in 0..1 (clamped). */
public final class Ease {
	private Ease() {}

	/** Slow in, slow out: views gliding between layouts. */
	public static double inOutCubic(double t) {
		double x = Math.clamp(t, 0, 1);
		if (x < 0.5) {
			return 4 * x * x * x;
		}
		double u = -2 * x + 2;
		return 1 - u * u * u / 2;
	}

	/** Fast in, slow out: things arriving (toasts, card flips). */
	public static double outCubic(double t) {
		double u = 1 - Math.clamp(t, 0, 1);
		return 1 - u * u * u;
	}
}
