package dev.hearthside.core.compositor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransportChoiceTest {
	@Test
	void everythingAvailableUsesGpuSharing() {
		assertThat(TransportChoice.choose(new ProbeFacts(true, true, true, false))).isEqualTo(Transport.GPU_SHARED);
	}

	@Test
	void anyMissingFactFallsBackToCopy() {
		assertThat(TransportChoice.choose(new ProbeFacts(false, true, true, false))).isEqualTo(Transport.COPY);
		assertThat(TransportChoice.choose(new ProbeFacts(true, false, true, false))).isEqualTo(Transport.COPY);
		assertThat(TransportChoice.choose(new ProbeFacts(true, true, false, false))).isEqualTo(Transport.COPY);
	}

	@Test
	void forcedCopyWins() {
		assertThat(TransportChoice.choose(new ProbeFacts(true, true, true, true))).isEqualTo(Transport.COPY);
	}
}
