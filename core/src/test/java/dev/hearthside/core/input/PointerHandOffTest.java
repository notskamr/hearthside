package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Who drives player 1's screens, the controller's pointer or the real mouse: whichever was used last (batch review M3,
 * I2). A nudged desk or a jittery mouse doesn't take a screen from the controller; a real click always goes where the
 * real arrow is.
 */
class PointerHandOffTest {
	static final long MS = 1_000_000L;

	@Test
	void theControllerDrivesOnceItMoves() {
		PointerHandOff handOff = new PointerHandOff();
		handOff.padUsed(0);
		assertThat(handOff.owner()).isEqualTo(PointerHandOff.Owner.PAD);
	}

	@Test
	void aSmallMouseNudgeDoesNotTakeOver() {
		PointerHandOff handOff = new PointerHandOff();
		handOff.padUsed(0);
		handOff.mouseMoved(1, 1, 10 * MS);
		handOff.mouseMoved(1, 0, 20 * MS);
		assertThat(handOff.owner()).isEqualTo(PointerHandOff.Owner.PAD);
	}

	@Test
	void aRealMouseMoveTakesOver() {
		PointerHandOff handOff = new PointerHandOff();
		handOff.padUsed(0);
		handOff.mouseMoved(3, 0, 10 * MS);
		handOff.mouseMoved(3, 0, 20 * MS);
		assertThat(handOff.owner()).isEqualTo(PointerHandOff.Owner.MOUSE);
	}

	@Test
	void nudgesSpreadOverTimeDoNotAddUp() {
		PointerHandOff handOff = new PointerHandOff();
		handOff.padUsed(0);
		for (int i = 1; i <= 10; i++) {
			handOff.mouseMoved(1, 0, i * 400 * MS); // one pixel every 0.4 s: a desk being bumped, not a hand
		}
		assertThat(handOff.owner()).isEqualTo(PointerHandOff.Owner.PAD);
	}

	@Test
	void aMouseClickAlwaysTakesOverAndAsksForTheRealArrowFirst() {
		PointerHandOff handOff = new PointerHandOff();
		handOff.padUsed(0);
		assertThat(handOff.mouseClicked(10 * MS)).as("the click must be re-aimed at the real arrow").isTrue();
		assertThat(handOff.owner()).isEqualTo(PointerHandOff.Owner.MOUSE);
		assertThat(handOff.mouseClicked(20 * MS)).as("already the mouse's: nothing to re-aim").isFalse();
	}

	@Test
	void theControllerTakesBackOnItsNextInput() {
		PointerHandOff handOff = new PointerHandOff();
		handOff.padUsed(0);
		handOff.mouseClicked(10 * MS);
		handOff.padUsed(20 * MS);
		assertThat(handOff.owner()).isEqualTo(PointerHandOff.Owner.PAD);
	}

	@Test
	void theThresholdIsFourPixelsWithinAQuarterSecond() {
		assertThat(PointerHandOff.TAKE_OVER_PIXELS).isEqualTo(4);
		assertThat(PointerHandOff.TAKE_OVER_WINDOW.toMillis()).isEqualTo(250);
	}
}
