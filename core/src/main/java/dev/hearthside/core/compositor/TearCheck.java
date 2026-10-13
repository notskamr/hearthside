package dev.hearthside.core.compositor;

/**
 * Judges one composited guest frame for the E2E's torn-frame check (Spec D section 3.2). The guest paints each frame
 * whole in colour A or colour B, alternately; the host samples points across the guest's view. Both colours in one
 * frame means the frame was torn (read while it was being written); any other colour is uncleared memory, the divider
 * showing through, or something drawn over the view.
 */
public final class TearCheck {
	/** How far each channel may stray from A or B and still match: the readback and blending round. */
	static final int TOLERANCE = 2;

	/** What one sampled pixel shows. */
	public enum Sample {A, B, OTHER}

	/**
	 * The verdict on one frame.
	 *
	 * @param mixed it shows both A and B
	 * @param bad it shows anything else
	 * @param firstBad the first sample that was neither, as {@code 0xRRGGBB}, or -1
	 */
	public record Frame(boolean mixed, boolean bad, int firstBad) {}

	private TearCheck() {}

	/** Classifies {@code rgb} as A, B (each channel within ±2) or OTHER. */
	public static Sample classify(int rgb, int a, int b) {
		if (near(rgb, a)) {
			return Sample.A;
		}
		return near(rgb, b) ? Sample.B : Sample.OTHER;
	}

	/** Judges one frame's samples, each {@code 0xRRGGBB}. */
	public static Frame judge(int[] samples, int a, int b) {
		boolean sawA = false, sawB = false;
		int firstBad = -1;
		for (int rgb : samples) {
			switch (classify(rgb, a, b)) {
				case A -> sawA = true;
				case B -> sawB = true;
				case OTHER -> {
					if (firstBad < 0) {
						firstBad = rgb & 0xFFFFFF;
					}
				}
			}
		}
		return new Frame(sawA && sawB, firstBad >= 0, firstBad);
	}

	private static boolean near(int rgb, int target) {
		for (int shift = 16; shift >= 0; shift -= 8) {
			if (Math.abs(((rgb >> shift) & 0xFF) - ((target >> shift) & 0xFF)) > TOLERANCE) {
				return false;
			}
		}
		return true;
	}
}
