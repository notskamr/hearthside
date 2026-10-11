package dev.hearthside.core.process;

import java.util.concurrent.CompletableFuture;

/** A started process. */
public interface RunningProcess {
	long pid();

	/** Completes with the exit code when the process ends. */
	CompletableFuture<Integer> onExit();

	boolean isAlive();

	/** Forcibly ends the process and everything it started. */
	void destroy();
}
