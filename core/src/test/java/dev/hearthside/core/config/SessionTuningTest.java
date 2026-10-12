package dev.hearthside.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hearthside.core.layout.LayoutSettings;
import org.junit.jupiter.api.Test;

/** What each preset sets for a session (Spec D section 3.3); 0 means "the player's own setting" or "host refresh". */
class SessionTuningTest {
	static SplitScreenConfig distance(int guestRenderDistance) {
		return new SplitScreenConfig(1536, guestRenderDistance, LayoutSettings.DEFAULTS, Preset.AUTO, false, VulkanOffer.PENDING);
	}

	@Test
	void performanceSetsTheTable() {
		assertThat(SessionTuning.of(Preset.PERFORMANCE, SplitScreenConfig.DEFAULTS))
				.isEqualTo(new SessionTuning(6, true, 45, 6, true));
	}

	@Test
	void balancedCapsAtEight() {
		assertThat(SessionTuning.of(Preset.BALANCED, SplitScreenConfig.DEFAULTS))
				.isEqualTo(new SessionTuning(8, false, 0, 0, false));
	}

	@Test
	void qualityChangesNothing() {
		assertThat(SessionTuning.of(Preset.QUALITY, SplitScreenConfig.DEFAULTS))
				.isEqualTo(new SessionTuning(0, false, 0, 0, false));
	}

	@Test
	void anExplicitDistanceBeatsThePreset() {
		assertThat(SessionTuning.of(Preset.PERFORMANCE, distance(12)).renderDistanceCap()).isEqualTo(12);
		assertThat(SessionTuning.of(Preset.QUALITY, distance(10)).renderDistanceCap()).isEqualTo(10);
		assertThat(SessionTuning.of(Preset.PERFORMANCE, distance(12)).lightGraphics()).isTrue();
	}

	@Test
	void zeroMeansNoCapEvenUnderAPreset() {
		assertThat(SessionTuning.of(Preset.PERFORMANCE, distance(0)).renderDistanceCap()).isZero();
		assertThat(SessionTuning.of(Preset.BALANCED, distance(0)).renderDistanceCap()).isZero();
	}

	@Test
	void autoIsNotATuning() {
		assertThatThrownBy(() -> SessionTuning.of(Preset.AUTO, SplitScreenConfig.DEFAULTS))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
