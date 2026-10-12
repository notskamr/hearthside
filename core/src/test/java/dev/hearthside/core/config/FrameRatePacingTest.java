package dev.hearthside.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The frame rate a guest's game paces to (Spec C: never faster than the host's display shows; Spec D section 3.3: the
 * preset's cap). A guest who changes Max Framerate themselves mid-session keeps their choice at every new layout (plan
 * Review Focus 2), still never above the host's refresh rate.
 */
class FrameRatePacingTest {
	@Test
	void thePresetCapBelowTheRefreshRate() {
		assertThat(FrameRatePacing.limit(144, 45, FrameRatePacing.NO_CHOICE)).isEqualTo(45);
	}

	@Test
	void noPresetCapIsTheRefreshRate() {
		assertThat(FrameRatePacing.limit(144, 0, FrameRatePacing.NO_CHOICE)).isEqualTo(144);
		assertThat(FrameRatePacing.limit(60, 0, FrameRatePacing.NO_CHOICE)).isEqualTo(60);
	}

	@Test
	void thePresetCapNeverRaisesAboveTheRefreshRate() {
		assertThat(FrameRatePacing.limit(30, 45, FrameRatePacing.NO_CHOICE)).isEqualTo(30);
	}

	@Test
	void aPlayerWhoRaisedItKeepsTheirs() {
		assertThat(FrameRatePacing.limit(144, 45, 90)).isEqualTo(90);
	}

	@Test
	void aPlayerWhoLoweredItKeepsTheirs() {
		assertThat(FrameRatePacing.limit(144, 45, 30)).isEqualTo(30);
	}

	@Test
	void aPlayersChoiceStillNeverPassesTheRefreshRate() {
		assertThat(FrameRatePacing.limit(144, 45, 200)).isEqualTo(144);
		assertThat(FrameRatePacing.limit(144, 0, 260)).isEqualTo(144);
	}

	@Test
	void aPlayersChoiceWithoutAPresetCap() {
		assertThat(FrameRatePacing.limit(144, 0, 30)).isEqualTo(30);
	}
}
