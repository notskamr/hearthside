package dev.hearthside.core.process;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.MutableClock;
import dev.hearthside.core.launch.LaunchCommand;
import dev.hearthside.core.supervise.RestartPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SatelliteSupervisorTest {
	static final class FakeProcess implements RunningProcess {
		final CompletableFuture<Integer> exit = new CompletableFuture<>();
		boolean destroyed;

		@Override public long pid() { return 42; }
		@Override public CompletableFuture<Integer> onExit() { return exit; }
		@Override public boolean isAlive() { return !exit.isDone(); }
		@Override public void destroy() { destroyed = true; exit.complete(-9); }

		void crash() { exit.complete(1); }
	}

	static final class FakeLauncher implements ProcessLauncher {
		final List<FakeProcess> started = new ArrayList<>();
		final Set<Integer> failOnAttempt = new java.util.HashSet<>();
		int attempts;

		@Override
		public RunningProcess start(LaunchCommand command, Path logFile) throws IOException {
			attempts++;
			if (failOnAttempt.contains(attempts)) {
				throw new IOException("launch failed");
			}
			var p = new FakeProcess();
			started.add(p);
			return p;
		}

		FakeProcess last() { return started.getLast(); }
	}

	static final class ManualScheduler implements Scheduler {
		final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

		@Override public void schedule(Runnable task, Duration delay) { tasks.add(task); }

		void runNext() { tasks.removeFirst().run(); }
	}

	final FakeLauncher launcher = new FakeLauncher();
	final ManualScheduler scheduler = new ManualScheduler();
	final MutableClock clock = new MutableClock(Instant.parse("2026-10-11T12:00:00Z"));
	final List<String> events = new ArrayList<>();
	final AtomicInteger commandsBuilt = new AtomicInteger();

	final SatelliteSupervisor supervisor = new SatelliteSupervisor(launcher,
			() -> {
				commandsBuilt.incrementAndGet();
				return new LaunchCommand(List.of("java"), Map.of(), Set.of());
			},
			Path.of("launch.log"), RestartPolicy.DEFAULT, scheduler, clock,
			new SatelliteSupervisor.Listener() {
				@Override public void started(RunningProcess p) { events.add("started"); }
				@Override public void restarting(int failures, Duration delay) { events.add("restarting " + failures + " " + delay.toSeconds() + "s"); }
				@Override public void gaveUp(int exitCode) { events.add("gaveUp " + exitCode); }
			});

	@Test
	void restartsWithBackoffThenGivesUp() throws Exception {
		supervisor.start();
		for (int i = 0; i < 3; i++) {
			launcher.last().crash();
			scheduler.runNext();
		}
		launcher.last().crash();
		assertThat(events).containsExactly(
				"started", "restarting 1 1s", "started", "restarting 2 2s", "started", "restarting 3 4s", "started", "gaveUp 1");
		assertThat(launcher.started).hasSize(4);
		assertThat(scheduler.tasks).isEmpty();
		assertThat(supervisor.isRunning()).isFalse();
	}

	@Test
	void stableRunResetsTheCount() throws Exception {
		supervisor.start();
		launcher.last().crash();
		scheduler.runNext();
		clock.advance(Duration.ofSeconds(60));
		launcher.last().crash();
		assertThat(events).containsExactly("started", "restarting 1 1s", "started", "restarting 1 1s");
	}

	@Test
	void stopDestroysProcessAndPreventsRestart() throws Exception {
		supervisor.start();
		FakeProcess p = launcher.last();
		supervisor.stop();
		assertThat(p.destroyed).isTrue();
		assertThat(scheduler.tasks).isEmpty();
		assertThat(events).containsExactly("started");
		assertThat(supervisor.isRunning()).isFalse();
	}

	@Test
	void stopDuringBackoffNeverRelaunches() throws Exception {
		supervisor.start();
		launcher.last().crash();
		supervisor.stop();
		scheduler.runNext();
		assertThat(launcher.attempts).isEqualTo(1);
	}

	@Test
	void freshCommandOnEveryLaunch() throws Exception {
		supervisor.start();
		launcher.last().crash();
		scheduler.runNext();
		assertThat(commandsBuilt).hasValue(2);
	}

	@Test
	void commandFailureDuringRestartCountsAsFailure() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		var flaky = new SatelliteSupervisor(launcher,
				() -> {
					if (calls.incrementAndGet() == 2) {
						throw new java.io.UncheckedIOException(new IOException("disk full"));
					}
					return new LaunchCommand(List.of("java"), Map.of(), Set.of());
				},
				Path.of("launch.log"), RestartPolicy.DEFAULT, scheduler, clock,
				new SatelliteSupervisor.Listener() {
					@Override public void started(RunningProcess p) { events.add("started"); }
					@Override public void restarting(int failures, Duration delay) { events.add("restarting " + failures + " " + delay.toSeconds() + "s"); }
					@Override public void gaveUp(int exitCode) { events.add("gaveUp " + exitCode); }
				});
		flaky.start();
		launcher.last().crash();
		scheduler.runNext();
		assertThat(events).containsExactly("started", "restarting 1 1s", "restarting 2 2s");
	}

	@Test
	void launchFailureDuringRestartCountsAsFailure() throws Exception {
		launcher.failOnAttempt.add(2);
		supervisor.start();
		launcher.last().crash();
		scheduler.runNext();
		assertThat(events).containsExactly("started", "restarting 1 1s", "restarting 2 2s");
	}
}
