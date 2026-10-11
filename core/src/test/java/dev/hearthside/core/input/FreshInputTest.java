package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FreshInputTest {
	final AtomicLong nanos = new AtomicLong(1_000_000_000L);
	final FreshInput input = new FreshInput(nanos::get, 300_000_000L);
	final InputFrame walking = new InputFrame(1, 0, 0, -1, 0, 0, 0, 0);

	@Test
	void nothingBeforeTheFirstFrame() {
		assertThat(input.current()).isEmpty();
	}

	@Test
	void recentFrameIsUsed() {
		input.accept(walking);
		nanos.addAndGet(299_000_000L);
		assertThat(input.current()).contains(walking);
	}

	@Test
	void staleFrameBecomesNeutral() {
		input.accept(walking);
		nanos.addAndGet(301_000_000L);
		assertThat(input.current()).contains(InputFrame.NEUTRAL);
	}

	@Test
	void aNewFrameRefreshes() {
		input.accept(walking);
		nanos.addAndGet(301_000_000L);
		input.accept(walking);
		assertThat(input.current()).contains(walking);
	}
}
