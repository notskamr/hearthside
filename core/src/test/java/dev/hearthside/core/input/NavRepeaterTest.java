package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class NavRepeaterTest {
	static final long MS = 1_000_000L;
	final NavRepeater repeater = new NavRepeater(Duration.ofMillis(300), Duration.ofMillis(80));

	@Test
	void navRepeaterMovesOnceThenRepeatsAfterTheDelay() {
		assertThat(repeater.step(1, 0)).isEqualTo(1);
		assertThat(repeater.step(1, 100 * MS)).isZero();
		assertThat(repeater.step(1, 299 * MS)).isZero();
		assertThat(repeater.step(1, 300 * MS)).isEqualTo(1);
		assertThat(repeater.step(1, 340 * MS)).isZero();
		assertThat(repeater.step(1, 380 * MS)).isEqualTo(1);
	}

	@Test
	void aLongStallMovesOnceNotMany() {
		// A direction held through a stall (or an unplug and replug) must not fire a burst of moves.
		repeater.step(1, 0);
		assertThat(repeater.step(1, 10_000 * MS)).isEqualTo(1);
		assertThat(repeater.step(1, 10_010 * MS)).isZero();
		assertThat(repeater.step(1, 10_080 * MS)).isEqualTo(1);
	}

	@Test
	void navRepeaterResetsOnRelease() {
		repeater.step(1, 0);
		assertThat(repeater.step(0, 50 * MS)).isZero();
		assertThat(repeater.step(1, 60 * MS)).isEqualTo(1);
	}

	@Test
	void changingDirectionMovesAtOnce() {
		repeater.step(1, 0);
		assertThat(repeater.step(-1, 10 * MS)).isEqualTo(1);
		assertThat(repeater.step(-1, 20 * MS)).isZero();
	}
}
