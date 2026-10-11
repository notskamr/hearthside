package dev.hearthside.core.compositor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.layout.Rect;
import org.junit.jupiter.api.Test;

class ViewportMathTest {
	@Test
	void virtualSizeOfHalfWindow() {
		assertThat(ViewportMath.virtualSize(new Rect(0, 0, 960, 1080), 1.0))
				.isEqualTo(new VirtualSize(960, 1080, 960, 1080));
	}

	@Test
	void virtualSizeHiDpi() {
		// A 2x display: the framebuffer has twice the pixels of window coordinates.
		assertThat(ViewportMath.virtualSize(new Rect(0, 0, 960, 1080), 0.5))
				.isEqualTo(new VirtualSize(960, 1080, 480, 540));
	}

	@Test
	void mouseInsideMapsToLocal() {
		assertThat(ViewportMath.windowToViewport(new ViewportMath.Point(1000, 50), new Rect(960, 0, 960, 1080), 1.0))
				.isEqualTo(new ViewportMath.Point(40, 50));
	}

	@Test
	void mouseInsideMapsToLocalHiDpi() {
		// Viewport starts at framebuffer x 960 = window x 480.
		assertThat(ViewportMath.windowToViewport(new ViewportMath.Point(500, 25), new Rect(960, 0, 960, 1080), 0.5))
				.isEqualTo(new ViewportMath.Point(20, 25));
	}

	@Test
	void mouseOutsideClamps() {
		assertThat(ViewportMath.windowToViewport(new ViewportMath.Point(10, 2000), new Rect(960, 0, 960, 1080), 1.0))
				.isEqualTo(new ViewportMath.Point(0, 1080));
	}

	@Test
	void containsIsHalfOpen() {
		Rect right = new Rect(960, 0, 960, 1080);
		assertThat(ViewportMath.contains(new ViewportMath.Point(960, 0), right, 1.0)).isTrue();
		assertThat(ViewportMath.contains(new ViewportMath.Point(959.5, 0), right, 1.0)).isFalse();
		assertThat(ViewportMath.contains(new ViewportMath.Point(1920, 0), right, 1.0)).isFalse();
	}

	@Test
	void letterboxWide() {
		// A 16:9 frame into a square target: full width, centred vertically.
		assertThat(ViewportMath.letterbox(1600, 900, new Rect(0, 0, 800, 800))).isEqualTo(new Rect(0, 175, 800, 450));
	}

	@Test
	void letterboxTall() {
		assertThat(ViewportMath.letterbox(900, 1600, new Rect(100, 0, 800, 800))).isEqualTo(new Rect(275, 0, 450, 800));
	}

	@Test
	void letterboxExactFit() {
		assertThat(ViewportMath.letterbox(960, 1080, new Rect(960, 0, 960, 1080))).isEqualTo(new Rect(960, 0, 960, 1080));
	}

	@Test
	void glFlipBottomRow() {
		assertThat(ViewportMath.glFlip(new Rect(0, 540, 960, 540), 1080)).isEqualTo(new Rect(0, 0, 960, 540));
		assertThat(ViewportMath.glFlip(new Rect(960, 0, 960, 540), 1080)).isEqualTo(new Rect(960, 540, 960, 540));
	}
}
