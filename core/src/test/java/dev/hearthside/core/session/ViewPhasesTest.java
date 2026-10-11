package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ViewPhasesTest {
	static final long MS = 1_000_000L;

	@Test
	void cardCoversUntilInWorldThenCrossfades() {
		var phases = new ViewPhases();
		phases.picking(1, 0);
		assertThat(phases.cardCover(1, 0, false)).isEqualTo(1f);
		phases.joining(1, 2000 * MS);
		assertThat(phases.phase(1)).contains(ViewPhase.JOINING);
		assertThat(phases.cardCover(1, 9000 * MS, false)).isEqualTo(1f);
		long in = 10_000 * MS;
		phases.inWorld(1, in);
		assertThat(phases.phase(1)).contains(ViewPhase.LIVE);
		assertThat(phases.cardCover(1, in + 125 * MS, false)).isCloseTo(0.5f, within(0.01f));
		assertThat(phases.cardCover(1, in + 250 * MS, false)).isEqualTo(0f);
	}

	@Test
	void stuckAfterFortyFiveSeconds() {
		var phases = new ViewPhases();
		phases.joining(1, 0);
		assertThat(phases.stuck(1, 44_999 * MS)).isFalse();
		assertThat(phases.stuck(1, 45_000 * MS)).isTrue();
		phases.inWorld(1, 46_000 * MS);
		assertThat(phases.stuck(1, 90_000 * MS)).isFalse();
	}

	@Test
	void reconnectingCoversAgain() {
		var phases = new ViewPhases();
		phases.joining(1, 0);
		phases.inWorld(1, 1000 * MS);
		long crash = 5000 * MS;
		phases.reconnecting(1, crash);
		assertThat(phases.phase(1)).contains(ViewPhase.RECONNECTING);
		assertThat(phases.cardCover(1, crash + 100 * MS, false)).isCloseTo(0.5f, within(0.01f));
		assertThat(phases.cardCover(1, crash + 200 * MS, false)).isEqualTo(1f);
	}

	@Test
	void leavingKeepsWhatWasShowing() {
		var phases = new ViewPhases();
		phases.joining(1, 0);
		phases.leaving(1, 1000 * MS);
		assertThat(phases.phase(1)).contains(ViewPhase.LEAVING);
		assertThat(phases.cardCover(1, 1100 * MS, false)).isEqualTo(1f);
		phases.remove(1);
		assertThat(phases.phase(1)).isEmpty();
		assertThat(phases.cardCover(1, 1200 * MS, false)).isEqualTo(0f);
	}

	@Test
	void reduceMotionSwapsAtOnce() {
		var phases = new ViewPhases();
		phases.joining(1, 0);
		phases.inWorld(1, 1000 * MS);
		assertThat(phases.cardCover(1, 1000 * MS, true)).isEqualTo(0f);
		phases.reconnecting(1, 2000 * MS);
		assertThat(phases.cardCover(1, 2000 * MS, true)).isEqualTo(1f);
	}
}
