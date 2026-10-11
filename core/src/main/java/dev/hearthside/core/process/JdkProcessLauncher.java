package dev.hearthside.core.process;

import dev.hearthside.core.launch.LaunchCommand;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** {@link ProcessLauncher} backed by {@link ProcessBuilder}. */
public final class JdkProcessLauncher implements ProcessLauncher {
	@Override
	public RunningProcess start(LaunchCommand command, Path logFile) throws IOException {
		Path parent = logFile.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		return new JdkProcess(builderFor(command, logFile).start());
	}

	static ProcessBuilder builderFor(LaunchCommand command, Path logFile) {
		ProcessBuilder builder = new ProcessBuilder(command.command());
		Map<String, String> env = builder.environment();
		command.removedEnvironment().forEach(env::remove);
		env.putAll(command.environment());
		if (command.workingDirectory() != null) {
			builder.directory(command.workingDirectory().toFile());
		}
		builder.redirectErrorStream(true);
		builder.redirectOutput(Redirect.appendTo(logFile.toFile()));
		return builder;
	}

	private static final class JdkProcess implements RunningProcess {
		private final Process process;
		private final CompletableFuture<Integer> exit = new CompletableFuture<>();

		JdkProcess(Process process) {
			this.process = process;
			// Process.onExit() on Windows watches a second handle opened by pid and was observed to
			// complete while this handle still reported the process alive; waitFor() uses our own.
			Thread.ofPlatform().daemon().name("hearthside-process-" + process.pid()).start(() -> {
				try {
					exit.complete(process.waitFor());
				} catch (InterruptedException e) {
					exit.completeExceptionally(e);
				}
			});
		}

		@Override
		public long pid() {
			return process.pid();
		}

		@Override
		public CompletableFuture<Integer> onExit() {
			return exit;
		}

		@Override
		public boolean isAlive() {
			return process.isAlive();
		}

		@Override
		public void destroy() {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
		}
	}
}
