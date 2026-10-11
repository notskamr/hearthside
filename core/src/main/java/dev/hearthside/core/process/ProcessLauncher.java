package dev.hearthside.core.process;

import dev.hearthside.core.launch.LaunchCommand;
import java.io.IOException;
import java.nio.file.Path;

/** Starts operating-system processes. A port, so supervision logic can be tested without real processes. */
public interface ProcessLauncher {
	/** Starts {@code command}, appending its stdout and stderr to {@code logFile}. */
	RunningProcess start(LaunchCommand command, Path logFile) throws IOException;
}
