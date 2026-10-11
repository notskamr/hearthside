package dev.hearthside.core.compositor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.layout.Rect;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

/** Push-constant values for one subframe on the canvas (Spec C sections 3.3 and 3.6). */
class ViewQuadTest {
	static ViewQuad quad(Rect dst, boolean flipRows, float alpha, boolean stale) {
		return ViewQuad.of(dst, 1920, 1080, 960, 1080, flipRows, alpha, stale).orElseThrow();
	}

	@Test
	void aViewIsPlacedInNormalisedCanvasSpace() {
		assertThat(quad(new Rect(960, 0, 960, 1080), false, 1f, false).dst()).containsExactly(0.5f, 0f, 1f, 1f);
	}

	@Test
	void aQuarterViewLandsInItsQuarter() {
		assertThat(quad(new Rect(0, 540, 960, 540), false, 1f, false).dst()).containsExactly(0f, 0.5f, 0.5f, 1f);
	}

	@Test
	void aViewHangingOffTheCanvasKeepsItsTrueSize() {
		// Mid-glide a view can start left of the window; the GPU clips it.
		assertThat(quad(new Rect(-480, 0, 960, 1080), false, 1f, false).dst()).containsExactly(-0.25f, 0f, 0.25f, 1f);
	}

	@Test
	void rowsInTheSameOrderSampleTheFrameUpright() {
		assertThat(quad(new Rect(0, 0, 960, 1080), false, 1f, false).src()).containsExactly(0f, 0f, 1f, 1f);
	}

	@Test
	void rowsInTheOtherOrderAreFlipped() {
		assertThat(quad(new Rect(0, 0, 960, 1080), true, 1f, false).src()).containsExactly(0f, 1f, 1f, 0f);
	}

	@Test
	void aStaleFrameIsBlurredAndAFreshOneIsNot() {
		assertThat(ViewQuad.STALE_BLUR_TEXELS).isEqualTo(8f);
		assertThat(quad(new Rect(0, 0, 960, 1080), false, 1f, true).blur()).isEqualTo(8f);
		assertThat(quad(new Rect(0, 0, 960, 1080), false, 1f, false).blur()).isZero();
	}

	@Test
	void alphaIsClampedToZeroToOne() {
		assertThat(quad(new Rect(0, 0, 960, 1080), false, 1.3f, false).alpha()).isEqualTo(1f);
		assertThat(quad(new Rect(0, 0, 960, 1080), false, -0.2f, false).alpha()).isZero();
		assertThat(quad(new Rect(0, 0, 960, 1080), false, 0.4f, false).alpha()).isEqualTo(0.4f);
	}

	@Test
	void texelSizeIsOneOverTheSource() {
		ViewQuad q = quad(new Rect(0, 0, 960, 1080), false, 1f, false);
		assertThat(q.texelW()).isEqualTo(1f / 960);
		assertThat(q.texelH()).isEqualTo(1f / 1080);
	}

	@Test
	void aZeroSizedViewDrawsNothing() {
		assertThat(ViewQuad.of(new Rect(0, 0, 0, 1080), 1920, 1080, 960, 1080, false, 1f, false)).isEmpty();
		assertThat(ViewQuad.of(new Rect(0, 0, 960, 0), 1920, 1080, 960, 1080, false, 1f, false)).isEmpty();
	}

	@Test
	void aZeroSizedCanvasOrSourceDrawsNothing() {
		assertThat(ViewQuad.of(new Rect(0, 0, 960, 1080), 0, 1080, 960, 1080, false, 1f, false)).isEmpty();
		assertThat(ViewQuad.of(new Rect(0, 0, 960, 1080), 1920, 0, 960, 1080, false, 1f, false)).isEmpty();
		assertThat(ViewQuad.of(new Rect(0, 0, 960, 1080), 1920, 1080, 0, 1080, false, 1f, false)).isEmpty();
		assertThat(ViewQuad.of(new Rect(0, 0, 960, 1080), 1920, 1080, 960, 0, false, 1f, false)).isEmpty();
	}

	@Test
	void writesTwelveFloatsInShaderOrder() {
		ByteBuffer buffer = ByteBuffer.allocate(64).order(ByteOrder.nativeOrder());
		quad(new Rect(960, 0, 960, 1080), true, 0.4f, true).write(buffer);
		assertThat(buffer.position()).isEqualTo(48);
		buffer.flip();
		float[] all = new float[12];
		for (int i = 0; i < 12; i++) {
			all[i] = buffer.getFloat();
		}
		assertThat(all).containsExactly(0.5f, 0f, 1f, 1f, 0f, 1f, 1f, 0f, 1f / 960, 1f / 1080, 0.4f, 8f);
	}
}
