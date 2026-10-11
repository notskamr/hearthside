package dev.hearthside.core.input;

import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * The latest controller frame received from the host, treated as neutral once it is too old. If the
 * host stalls (window drag, GC pause), a guest stops walking instead of acting on stale input.
 * Safe to write from one thread and read from another.
 */
public final class FreshInput {
	private record Stamped(InputFrame frame, long receivedNanos) {}

	private final LongSupplier nanoClock;
	private final long maxAgeNanos;
	private volatile Stamped latest;

	public FreshInput(LongSupplier nanoClock, long maxAgeNanos) {
		this.nanoClock = nanoClock;
		this.maxAgeNanos = maxAgeNanos;
	}

	public void accept(InputFrame frame) {
		latest = new Stamped(frame, nanoClock.getAsLong());
	}

	/** Empty before the first frame; {@link InputFrame#NEUTRAL} once the last frame is stale. */
	public Optional<InputFrame> current() {
		Stamped s = latest;
		if (s == null) {
			return Optional.empty();
		}
		return Optional.of(nanoClock.getAsLong() - s.receivedNanos() > maxAgeNanos ? InputFrame.NEUTRAL : s.frame());
	}

	public void clear() {
		latest = null;
	}
}
