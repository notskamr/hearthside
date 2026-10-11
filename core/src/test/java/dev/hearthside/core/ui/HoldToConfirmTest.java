package dev.hearthside.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class HoldToConfirmTest {
	static final long MS = 1_000_000L;

	@Test
	void holdToConfirmCompletesAfterItsLengthAndResetsOnRelease() {
		var hold = new HoldToConfirm(Duration.ofMillis(1500));
		assertThat(hold.step(true, 0)).isZero();
		assertThat(hold.step(true, 750 * MS)).isEqualTo(0.5);
		assertThat(hold.done()).isFalse();
		assertThat(hold.step(false, 800 * MS)).isZero();
		assertThat(hold.step(true, 900 * MS)).isZero();
		assertThat(hold.step(true, 2400 * MS)).isEqualTo(1.0);
		assertThat(hold.done()).isTrue();
		// Once done it stays done until released.
		assertThat(hold.step(true, 3000 * MS)).isEqualTo(1.0);
		hold.step(false, 3100 * MS);
		assertThat(hold.done()).isFalse();
	}
}
