package dev.hearthside.core.launch;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A process to start: its argument list, extra environment variables, inherited environment
 * variables to remove, and the directory it runs in.
 *
 * @param workingDirectory where the process starts; {@code null} inherits the parent's
 */
public record LaunchCommand(List<String> command, Map<String, String> environment, Set<String> removedEnvironment,
		Path workingDirectory) {
	public LaunchCommand {
		command = List.copyOf(command);
		environment = Map.copyOf(environment);
		removedEnvironment = Set.copyOf(removedEnvironment);
	}
}
