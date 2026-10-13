package dev.hearthside.core.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The join prompt keeps clear of Minecraft's toasts in player 1's view (Spec E section 3.4). */
class BelowToastsTest {
	static final int X = 100, W = 200, H = 38, GAP = 4, TOP = 26;

	@Test
	void staysAtItsPlaceWithNoToasts() {
		assertThat(BelowToasts.top(TOP, X, W, H, GAP, List.of())).isEqualTo(TOP);
	}

	@Test
	void sitsJustBelowAToastItWouldMeet() {
		assertThat(BelowToasts.top(TOP, X, W, H, GAP, List.of(new Rect(50, 0, 160, 32)))).isEqualTo(36);
	}

	@Test
	void aToastBesideItChangesNothing() {
		assertThat(BelowToasts.top(TOP, X, W, H, GAP, List.of(new Rect(300, 0, 160, 32)))).isEqualTo(TOP);
		assertThat(BelowToasts.top(TOP, X, W, H, GAP, List.of(new Rect(0, 0, 100, 32)))).isEqualTo(TOP);
	}

	@Test
	void aToastEndingWithinTheGapAboveStillMovesIt() {
		assertThat(BelowToasts.top(TOP, X, W, H, GAP, List.of(new Rect(50, 0, 160, 24)))).isEqualTo(28);
	}

	@Test
	void aToastFarBelowChangesNothing() {
		assertThat(BelowToasts.top(TOP, X, W, H, GAP, List.of(new Rect(50, 200, 160, 32)))).isEqualTo(TOP);
	}

	@Test
	void belowTheLowestOfAStack() {
		List<Rect> stack = List.of(new Rect(250, 32, 160, 32), new Rect(250, 0, 160, 32));
		assertThat(BelowToasts.top(TOP, X, W, H, GAP, stack)).isEqualTo(68);
	}
}
