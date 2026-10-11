package dev.hearthside.core.process;

import java.time.Duration;

/** Runs a task later. A port, so restart timing can be tested without waiting. */
public interface Scheduler {
	void schedule(Runnable task, Duration delay);
}
