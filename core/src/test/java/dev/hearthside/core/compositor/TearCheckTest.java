package dev.hearthside.core.compositor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Judging one composited guest frame for the E2E's torn-frame check (Spec D section 3.2): the guest paints whole frames
 * in colour A or colour B, alternately. A frame showing both is torn; any other colour is garbage or see-through.
 */
class TearCheckTest {
	static final int A = 0x33CC33, B = 0x3333CC, DIVIDER = 0x120C08, POISON = 0x7F1010;

	static int[] filled(int count, int rgb) {
		int[] samples = new int[count];
		Arrays.fill(samples, rgb);
		return samples;
	}

	/** {@code top} blended over {@code bottom} at {@code alpha} (0–1), per channel, rounded. */
	static int blend(int top, int bottom, double alpha) {
		int rgb = 0;
		for (int shift = 16; shift >= 0; shift -= 8) {
			int t = (top >> shift) & 0xFF, b = (bottom >> shift) & 0xFF;
			rgb |= (int) Math.round(t * alpha + b * (1 - alpha)) << shift;
		}
		return rgb;
	}

	@Test
	void aWholeFrameIsNeitherMixedNorBad() {
		TearCheck.Frame frame = TearCheck.judge(filled(13, A), A, B);
		assertThat(frame.mixed()).isFalse();
		assertThat(frame.bad()).isFalse();
		assertThat(frame.firstBad()).isEqualTo(-1);
		assertThat(TearCheck.judge(filled(13, B), A, B).mixed()).isFalse();
	}

	@Test
	void bothColoursInOneFrameIsMixed() {
		int[] samples = filled(13, A);
		Arrays.fill(samples, 6, 13, B); // the bottom rows from the next frame
		TearCheck.Frame frame = TearCheck.judge(samples, A, B);
		assertThat(frame.mixed()).isTrue();
		assertThat(frame.bad()).isFalse();
	}

	@Test
	void anyOtherColourIsBadAndRemembered() {
		int[] samples = filled(13, A);
		samples[4] = POISON;
		samples[7] = 0x000000;
		TearCheck.Frame frame = TearCheck.judge(samples, A, B);
		assertThat(frame.bad()).isTrue();
		assertThat(frame.firstBad()).isEqualTo(POISON);
		assertThat(frame.mixed()).isFalse();
	}

	@Test
	void channelsWithinTwoStillMatch() {
		assertThat(TearCheck.classify(0x35CA31, A, B)).isEqualTo(TearCheck.Sample.A);
		assertThat(TearCheck.classify(0x3131CE, A, B)).isEqualTo(TearCheck.Sample.B);
		assertThat(TearCheck.classify(0x36CC33, A, B)).isEqualTo(TearCheck.Sample.OTHER);
	}

	@Test
	void aHalfAlphaBlendOverTheDividerIsBad() {
		assertThat(TearCheck.classify(blend(A, DIVIDER, 128 / 255.0), A, B)).isEqualTo(TearCheck.Sample.OTHER);
		assertThat(TearCheck.judge(filled(13, blend(B, DIVIDER, 128 / 255.0)), A, B).bad()).isTrue();
	}
}
