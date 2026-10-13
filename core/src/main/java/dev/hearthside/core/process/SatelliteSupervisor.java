package dev.hearthside.core.process;

import dev.hearthside.core.launch.LaunchCommand;
import dev.hearthside.core.supervise.RestartPolicy;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Keeps one satellite process running: restarts it after crashes per {@link RestartPolicy}, gives
 * up when the policy says so, and never launches anything after {@link #stop()} or {@link #stopAfter}.
 */
public final class SatelliteSupervisor {
	private static final System.Logger LOG = System.getLogger(SatelliteSupervisor.class.getName());

	public interface Listener {
		void started(RunningProcess process);

		void restarting(int consecutiveFailures, Duration delay);

		void gaveUp(int exitCode);

		/** The satellite exited cleanly by itself (the player closed it); it is not restarted. */
		void ended();
	}

	private final ProcessLauncher launcher;
	private final Supplier<LaunchCommand> command;
	private final Path logFile;
	private final RestartPolicy policy;
	private final Scheduler scheduler;
	private final Clock clock;
	private final Listener listener;

	private boolean stopped;
	/** Bumped by stop(), so restarts scheduled before it become no-ops. */
	private int generation;
	private RunningProcess current;
	/** Asked to quit by {@link #stopAfter}, and given until its grace is over to end by itself; null when none. */
	private RunningProcess quitting;
	private Instant startedAt;
	private int consecutiveFailures;

	public SatelliteSupervisor(ProcessLauncher launcher, Supplier<LaunchCommand> command, Path logFile,
			RestartPolicy policy, Scheduler scheduler, Clock clock, Listener listener) {
		this.launcher = launcher;
		this.command = command;
		this.logFile = logFile;
		this.policy = policy;
		this.scheduler = scheduler;
		this.clock = clock;
		this.listener = listener;
	}

	public synchronized void start() throws IOException {
		stopped = false;
		consecutiveFailures = 0;
		launch();
	}

	public synchronized void stop() {
		stopped = true;
		generation++;
		RunningProcess process = current;
		current = null;
		if (process != null) {
			process.destroy();
		}
		RunningProcess asked = quitting;
		quitting = null;
		if (asked != null && asked.isAlive()) {
			asked.destroy();
		}
	}

	/**
	 * As {@link #stop()}, for a process already asked to quit: it is left to end by itself, and forcibly ended only if it
	 * is still running after {@code grace} (or at a {@link #stop()} before then). Killing a game mid-frame stalls every
	 * other game on the GPU for a moment; one that quits by itself does not.
	 */
	public synchronized void stopAfter(Duration grace) {
		stopped = true;
		generation++;
		RunningProcess process = current;
		current = null;
		if (process == null) {
			return;
		}
		quitting = process;
		try {
			scheduler.schedule(() -> endQuitting(process), grace);
		} catch (RuntimeException e) {
			// Nothing to wait with (shutting down): end it now rather than leave it running.
			quitting = null;
			process.destroy();
		}
	}

	private synchronized void endQuitting(RunningProcess process) {
		if (quitting == process) {
			quitting = null;
			if (process.isAlive()) {
				process.destroy();
			}
		}
	}

	public synchronized boolean isRunning() {
		return !stopped && current != null;
	}

	private void launch() throws IOException {
		RunningProcess process = launcher.start(command.get(), logFile);
		current = process;
		startedAt = clock.instant();
		listener.started(process);
		process.onExit().whenComplete((code, error) -> exited(process, code == null ? -1 : code));
	}

	private synchronized void exited(RunningProcess process, int exitCode) {
		if (stopped || process != current) {
			return;
		}
		current = null;
		if (exitCode == 0) {
			stopped = true;
			listener.ended();
			return;
		}
		if (policy.resetsFailures(Duration.between(startedAt, clock.instant()))) {
			consecutiveFailures = 0;
		}
		failed(exitCode);
	}

	private void failed(int exitCode) {
		consecutiveFailures++;
		Optional<Duration> delay = policy.delayAfterFailure(consecutiveFailures);
		if (delay.isEmpty()) {
			listener.gaveUp(exitCode);
			return;
		}
		listener.restarting(consecutiveFailures, delay.get());
		int scheduledGeneration = generation;
		scheduler.schedule(() -> restart(scheduledGeneration), delay.get());
	}

	private synchronized void restart(int scheduledGeneration) {
		if (stopped || scheduledGeneration != generation) {
			return;
		}
		try {
			launch();
		} catch (IOException | RuntimeException e) {
			// Runs on the scheduler's thread: an escaping exception would silently end supervision.
			LOG.log(Level.WARNING, "Satellite relaunch failed", e);
			failed(-1);
		}
	}
}
