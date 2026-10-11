package dev.hearthside.core.launch;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A process to start: its argument list, extra environment variables, and inherited environment
 * variables to remove.
 */
public record LaunchCommand(List<String> command, Map<String, String> environment, Set<String> removedEnvironment) {
	public LaunchCommand {
		command = List.copyOf(command);
		environment = Map.copyOf(environment);
		removedEnvironment = Set.copyOf(removedEnvironment);
	}
}
