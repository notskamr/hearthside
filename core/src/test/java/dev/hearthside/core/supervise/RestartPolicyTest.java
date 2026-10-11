package dev.hearthside.core.supervise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RestartPolicyTest {
	@Test
	void backsOffThenGivesUp() {
		var p = RestartPolicy.DEFAULT;
		assertThat(p.delayAfterFailure(1)).contains(Duration.ofSeconds(1));
		assertThat(p.delayAfterFailure(2)).contains(Duration.ofSeconds(2));
		assertThat(p.delayAfterFailure(3)).contains(Duration.ofSeconds(4));
		assertThat(p.delayAfterFailure(4)).isEmpty();
	}

	@Test
	void stableRunResetsFailures() {
		assertThat(RestartPolicy.DEFAULT.resetsFailures(Duration.ofSeconds(60))).isTrue();
		assertThat(RestartPolicy.DEFAULT.resetsFailures(Duration.ofSeconds(59))).isFalse();
	}

	@Test
	void zeroFailuresIsInvalid() {
		assertThatThrownBy(() -> RestartPolicy.DEFAULT.delayAfterFailure(0))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
