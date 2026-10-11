package dev.hearthside.core.launch;

import java.util.List;
import java.util.Map;

/** A process to start: its argument list and extra environment variables. */
public record LaunchCommand(List<String> command, Map<String, String> environment) {
	public LaunchCommand {
		command = List.copyOf(command);
		environment = Map.copyOf(environment);
	}
}
