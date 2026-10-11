package dev.hearthside.core.supervise;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * When to restart a crashed satellite.
 *
 * <p>Consecutive failure {@code n} waits {@code delays[n-1]}; once the delays run out the host gives
 * up and shows an error. A satellite that ran for at least {@code stableUptime} before crashing
 * starts counting from zero again.
 */
public record RestartPolicy(List<Duration> delays, Duration stableUptime) {
	public static final RestartPolicy DEFAULT = new RestartPolicy(
			List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4)), Duration.ofSeconds(60));

	public RestartPolicy {
		delays = List.copyOf(delays);
	}

	public Optional<Duration> delayAfterFailure(int consecutiveFailures) {
		if (consecutiveFailures < 1) {
			throw new IllegalArgumentException("consecutiveFailures must be >= 1, was " + consecutiveFailures);
		}
		return consecutiveFailures > delays.size()
				? Optional.empty()
				: Optional.of(delays.get(consecutiveFailures - 1));
	}

	public boolean resetsFailures(Duration uptime) {
		return uptime.compareTo(stableUptime) >= 0;
	}
}
