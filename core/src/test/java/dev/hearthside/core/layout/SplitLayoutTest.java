package dev.hearthside.core.layout;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.layout.LayoutSettings.ThreePlayer;
import dev.hearthside.core.layout.LayoutSettings.TwoPlayer;
import org.junit.jupiter.api.Test;

class SplitLayoutTest {
	private static final Rect SCREEN = new Rect(0, 0, 1920, 1080);
	private static final LayoutSettings DEFAULTS = LayoutSettings.DEFAULTS;

	@Test
	void defaultsMatchTheSpec() {
		assertThat(DEFAULTS).isEqualTo(new LayoutSettings(TwoPlayer.SIDE_BY_SIDE, ThreePlayer.GRID_WITH_PANEL, true, 2));
	}

	@Test
	void onePlayerFullScreen() {
		LayoutPlan plan = SplitLayout.plan(1, SCREEN, DEFAULTS);
		assertThat(plan.players()).containsExactly(SCREEN);
		assertThat(plan.panel()).isEmpty();
	}

	@Test
	void twoSideBySideSplitsWidthWithGap() {
		LayoutPlan plan = SplitLayout.plan(2, SCREEN, DEFAULTS);
		assertThat(plan.players()).containsExactly(new Rect(0, 0, 959, 1080), new Rect(961, 0, 959, 1080));
	}

	@Test
	void twoStacked() {
		LayoutPlan plan = SplitLayout.plan(2, SCREEN, DEFAULTS.withTwo(TwoPlayer.STACKED));
		assertThat(plan.players()).containsExactly(new Rect(0, 0, 1920, 539), new Rect(0, 541, 1920, 539));
	}

	@Test
	void threeGridHasPanel() {
		LayoutPlan plan = SplitLayout.plan(3, SCREEN, DEFAULTS);
		assertThat(plan.players()).containsExactly(new Rect(0, 0, 959, 539), new Rect(961, 0, 959, 539),
				new Rect(0, 541, 959, 539));
		assertThat(plan.panel()).contains(new Rect(961, 541, 959, 539));
	}

	@Test
	void threeGridWithoutPanelLeavesQuarterEmpty() {
		LayoutPlan plan = SplitLayout.plan(3, SCREEN, DEFAULTS.withPartyPanel(false));
		assertThat(plan.players()).hasSize(3);
		assertThat(plan.panel()).isEmpty();
	}

	@Test
	void threeWideTop() {
		LayoutPlan plan = SplitLayout.plan(3, SCREEN, DEFAULTS.withThree(ThreePlayer.WIDE_TOP));
		assertThat(plan.players()).containsExactly(new Rect(0, 0, 1920, 539), new Rect(0, 541, 959, 539),
				new Rect(961, 541, 959, 539));
		assertThat(plan.panel()).isEmpty();
	}

	@Test
	void threeTallLeft() {
		LayoutPlan plan = SplitLayout.plan(3, SCREEN, DEFAULTS.withThree(ThreePlayer.TALL_LEFT));
		assertThat(plan.players()).containsExactly(new Rect(0, 0, 959, 1080), new Rect(961, 0, 959, 539),
				new Rect(961, 541, 959, 539));
	}

	@Test
	void fourQuarters() {
		LayoutPlan plan = SplitLayout.plan(4, SCREEN, DEFAULTS);
		assertThat(plan.players()).containsExactly(new Rect(0, 0, 959, 539), new Rect(961, 0, 959, 539),
				new Rect(0, 541, 959, 539), new Rect(961, 541, 959, 539));
		assertThat(plan.panel()).isEmpty();
	}

	@Test
	void offsetScreenIsRespected() {
		LayoutPlan plan = SplitLayout.plan(2, new Rect(100, 50, 800, 600), DEFAULTS.withGap(0));
		assertThat(plan.players()).containsExactly(new Rect(100, 50, 400, 600), new Rect(500, 50, 400, 600));
	}
}
