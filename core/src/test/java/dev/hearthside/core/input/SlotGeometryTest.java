package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class SlotGeometryTest {
	/** A 3×2 grid of slot centres, 18 px apart, like an inventory. */
	static final List<SlotGeometry.Point> GRID = List.of(
			new SlotGeometry.Point(8, 8), new SlotGeometry.Point(26, 8), new SlotGeometry.Point(44, 8),
			new SlotGeometry.Point(8, 26), new SlotGeometry.Point(26, 26), new SlotGeometry.Point(44, 26));

	@Test
	void snapGlidesHalfwayToANearbySlot() {
		var target = SlotGeometry.snap(GRID, 30, 12, 12).orElseThrow();
		assertThat(target.x()).isCloseTo(28, within(1e-9));
		assertThat(target.y()).isCloseTo(10, within(1e-9));
	}

	@Test
	void snapIgnoresFarSlots() {
		assertThat(SlotGeometry.snap(GRID, 100, 100, 12)).isEmpty();
	}

	@Test
	void snapStopsWhenCentred() {
		assertThat(SlotGeometry.snap(GRID, 26, 8, 12)).isEmpty();
	}

	@Test
	void hopRightFindsTheNextColumn() {
		assertThat(SlotGeometry.hop(GRID, 8, 8, 1, 0)).contains(new SlotGeometry.Point(26, 8));
	}

	@Test
	void hopDownFindsTheNextRow() {
		assertThat(SlotGeometry.hop(GRID, 26, 8, 0, 1)).contains(new SlotGeometry.Point(26, 26));
	}

	@Test
	void hopPrefersStraightOverDiagonal() {
		// From (8,8) going right: (26,8) is straight ahead; (26,26) is 45° off and farther.
		assertThat(SlotGeometry.hop(GRID, 8, 8, 1, 0)).contains(new SlotGeometry.Point(26, 8));
	}

	@Test
	void hopAtTheEdgeGoesNowhere() {
		assertThat(SlotGeometry.hop(GRID, 44, 8, 1, 0)).isEmpty();
		assertThat(SlotGeometry.hop(GRID, 8, 8, 0, 0)).isEmpty();
	}

	@Test
	void noSlotsMeansNoTargets() {
		assertThat(SlotGeometry.snap(List.of(), 0, 0, 12)).isEmpty();
		assertThat(SlotGeometry.hop(List.of(), 0, 0, 1, 0)).isEmpty();
	}
}
