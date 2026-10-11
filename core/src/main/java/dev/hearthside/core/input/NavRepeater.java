package dev.hearthside.core.input;

import java.time.Duration;

/**
 * Held-direction repeat for menus and the on-screen keyboard: one move on a new direction, then, after a delay, one
 * move every repeat interval. Not thread-safe.
 */
public final class NavRepeater {
	private final long initialNanos;
	private final long repeatNanos;
	private int direction;
	private long nextNanos;

	public NavRepeater(Duration initial, Duration repeat) {
		this.initialNanos = initial.toNanos();
		this.repeatNanos = repeat.toNanos();
	}

	/**
	 * @param direction the held direction (-1, 0 or 1 along one axis)
	 * @return how many moves to make this frame
	 */
	public int step(int direction, long nanos) {
		if (direction == 0) {
			this.direction = 0;
			return 0;
		}
		if (direction != this.direction) {
			this.direction = direction;
			nextNanos = nanos + initialNanos;
			return 1;
		}
		if (nanos < nextNanos) {
			return 0;
		}
		// One move per frame at most: a stall (or a held direction across an unplug) must not fire a burst.
		nextNanos = Math.max(nextNanos, nanos) + repeatNanos;
		return 1;
	}
}
