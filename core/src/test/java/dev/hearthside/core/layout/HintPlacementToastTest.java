package dev.hearthside.core.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Minecraft's toasts sit in the top-right column, the hints' old last resort (Spec D section 3.5). With a toast there
 * and the bottom taken, the row goes top left (plan ruling T7-1): hints keep showing while a toast shows.
 */
class HintPlacementToastTest {
	static final Rect SCREEN = new Rect(0, 0, 400, 240);
	static final int W = 120, H = 14, MARGIN = 4;
	static final Rect BOTTOM = new Rect(0, 200, 400, 40);
	static final Rect TOASTS = new Rect(240, 0, 160, 64);

	@Test
	void topLeftWhenAToastTakesTheTopRight() {
		assertThat(HintPlacement.place(SCREEN, List.of(BOTTOM, TOASTS), W, H, MARGIN)).contains(new Rect(4, 4, W, H));
	}

	@Test
	void topRightStaysPreferredWithoutToasts() {
		assertThat(HintPlacement.place(SCREEN, List.of(BOTTOM), W, H, MARGIN)).contains(new Rect(276, 4, W, H));
	}

	@Test
	void aToastNeverMovesHintsThatAreAlreadyClear() {
		assertThat(HintPlacement.place(SCREEN, List.of(TOASTS), W, H, MARGIN)).contains(new Rect(140, 222, W, H));
	}

	@Test
	void stillNothingWhenEverySpotIsTaken() {
		assertThat(HintPlacement.place(SCREEN, List.of(BOTTOM, new Rect(0, 0, 400, 30)), W, H, MARGIN)).isEmpty();
	}
}
