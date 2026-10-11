package dev.hearthside.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A test clock that only moves when told to. */
public final class MutableClock extends Clock {
	private Instant now;

	public MutableClock(Instant start) {
		this.now = start;
	}

	public void advance(Duration by) {
		now = now.plus(by);
	}

	@Override
	public Instant instant() {
		return now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}
}
