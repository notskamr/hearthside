package dev.hearthside.core.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Where a row of controller hints goes on a Minecraft screen: the first free spot of five, bottom centre, bottom right,
 * bottom left, top right and top left, never over a button, a text field or an inventory panel (user session,
 * 2026-10-11), nor a toast (Spec D; see HintPlacementToastTest).
 */
class HintPlacementTest {
	static final Rect SCREEN = new Rect(0, 0, 400, 240);
	static final int W = 120, H = 14, MARGIN = 4;

	@Test
	void bottomCentreWhenFree() {
		assertThat(HintPlacement.place(SCREEN, List.of(), W, H, MARGIN)).contains(new Rect(140, 222, W, H));
	}

	@Test
	void bottomRightWhenTheCentreIsTaken() {
		List<Rect> done = List.of(new Rect(150, 215, 100, 20)); // e.g. Options' "Done"
		assertThat(HintPlacement.place(SCREEN, done, W, H, MARGIN)).contains(new Rect(276, 222, W, H));
	}

	@Test
	void bottomLeftWhenTheRightIsTakenToo() {
		List<Rect> row = List.of(new Rect(150, 215, 100, 20), new Rect(270, 215, 126, 20));
		assertThat(HintPlacement.place(SCREEN, row, W, H, MARGIN)).contains(new Rect(4, 222, W, H));
	}

	@Test
	void topRightAsALastResort() {
		List<Rect> bottom = List.of(new Rect(0, 200, 400, 40)); // a chat box or a tall inventory
		assertThat(HintPlacement.place(SCREEN, bottom, W, H, MARGIN)).contains(new Rect(276, 4, W, H));
	}

	@Test
	void nothingWhenEverySpotIsTaken() {
		assertThat(HintPlacement.place(SCREEN, List.of(new Rect(0, 0, 400, 240)), W, H, MARGIN)).isEmpty();
	}

	@Test
	void nothingWhenTheRowIsWiderThanTheScreen() {
		assertThat(HintPlacement.place(new Rect(0, 0, 100, 240), List.of(), W, H, MARGIN)).isEmpty();
	}

	@Test
	void touchingEdgesAreNotOverlaps() {
		// A button ending exactly where the row starts leaves the bottom centre free.
		List<Rect> above = List.of(new Rect(140, 200, W, 22));
		assertThat(HintPlacement.place(SCREEN, above, W, H, MARGIN)).contains(new Rect(140, 222, W, H));
	}
}
