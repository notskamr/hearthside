package dev.hearthside.core.compositor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.hearthside.core.layout.Rect;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * One GUI scale for every view (Spec D section 3.4; plan R2, R3): Minecraft's own rule for each view's size in pixels,
 * the smallest across the party, and a guest's reduced render resolution snapped so its GUI is still a whole scale
 * that looks exactly as big as everyone else's.
 */
class GuiScaleRuleTest {
	static Rect view(int width, int height) {
		return new Rect(0, 0, width, height);
	}

	@Test
	void autoMatchesVanilla() {
		assertThat(GuiScaleRule.autoScale(0, 1920, 1080)).isEqualTo(4);
		assertThat(GuiScaleRule.autoScale(0, 960, 1080)).isEqualTo(3);
		assertThat(GuiScaleRule.autoScale(0, 960, 540)).isEqualTo(2);
		assertThat(GuiScaleRule.autoScale(0, 640, 480)).isEqualTo(2);
		assertThat(GuiScaleRule.autoScale(0, 400, 300)).isEqualTo(1);
	}

	@Test
	void aFixedSettingIsCappedLikeVanilla() {
		assertThat(GuiScaleRule.autoScale(3, 960, 540)).isEqualTo(2);
		assertThat(GuiScaleRule.autoScale(2, 1920, 1080)).isEqualTo(2);
		assertThat(GuiScaleRule.autoScale(4, 960, 1080)).isEqualTo(3);
	}

	@Test
	void thePartyUsesTheSmallest() {
		// Tall left: player 1 down the left half, two guests stacked on the right.
		assertThat(GuiScaleRule.partyScale(0, List.of(view(960, 1080), view(960, 540), view(960, 540)))).isEqualTo(2);
		assertThat(GuiScaleRule.partyScale(0, List.of(view(960, 1080), view(960, 1080)))).isEqualTo(3);
		assertThat(GuiScaleRule.partyScale(4, List.of(view(960, 1080), view(960, 1080)))).isEqualTo(3);
	}

	@Test
	void aPartyNeedsAView() {
		assertThatThrownBy(() -> GuiScaleRule.partyScale(0, List.of())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void reducedResolutionSnapsToTheGuiScale() {
		// At 3, 2/3 resolution made hint text visibly soft once stretched (Task 8's visual QA): full resolution there.
		assertThat(GuiScaleRule.forView(view(960, 1080), 3, true)).isEqualTo(new GuiScaleRule.ViewScale(3, 1.0, 960, 1080));

		GuiScaleRule.ViewScale four = GuiScaleRule.forView(view(1920, 1080), 4, true);
		assertThat(four.guiScale()).isEqualTo(3);
		assertThat(four.renderScale()).isCloseTo(0.75, within(1e-9));
		assertThat(four.frameWidth()).isEqualTo(1440);
		assertThat(four.frameHeight()).isEqualTo(810);
	}

	@Test
	void theGuiLooksTheSameSizeAtReducedResolution() {
		for (int party = 4; party <= 6; party++) {
			GuiScaleRule.ViewScale scale = GuiScaleRule.forView(view(1920, 1080), party, true);
			assertThat(scale.guiScale() / scale.renderScale()).isCloseTo(party, within(1e-9));
		}
	}

	@Test
	void noReductionAtThreeOrLess() {
		assertThat(GuiScaleRule.forView(view(960, 1080), 3, true).renderScale()).isEqualTo(1.0);
		assertThat(GuiScaleRule.forView(view(960, 540), 2, true).renderScale()).isEqualTo(1.0);
		assertThat(GuiScaleRule.forView(view(960, 540), 2, true)).isEqualTo(new GuiScaleRule.ViewScale(2, 1.0, 960, 540));
		assertThat(GuiScaleRule.forView(view(640, 480), 1, true)).isEqualTo(new GuiScaleRule.ViewScale(1, 1.0, 640, 480));
	}

	@Test
	void fullResolutionWhenNotReduced() {
		assertThat(GuiScaleRule.forView(view(960, 1080), 3, false)).isEqualTo(new GuiScaleRule.ViewScale(3, 1.0, 960, 1080));
	}

	@Test
	void aTinyViewNeverGetsAZeroFrame() {
		GuiScaleRule.ViewScale tiny = GuiScaleRule.forView(view(1, 1), 4, true);
		assertThat(tiny.frameWidth()).isEqualTo(1);
		assertThat(tiny.frameHeight()).isEqualTo(1);
		GuiScaleRule.ViewScale empty = GuiScaleRule.forView(view(0, 0), 2, false);
		assertThat(empty.frameWidth()).isEqualTo(1);
		assertThat(empty.frameHeight()).isEqualTo(1);
	}
}
