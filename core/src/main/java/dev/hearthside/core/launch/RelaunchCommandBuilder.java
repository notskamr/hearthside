package dev.hearthside.core.launch;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Starts player 1's game again after "Switch and restart" (Spec D §3.1): the host's own launch, unchanged, except that a
 * forced {@code --graphicsBackend} is dropped so the saved Graphics API option (now Vulkan) chooses, and a debugger
 * agent is dropped because the closing game still holds its port.
 */
public final class RelaunchCommandBuilder {
	private static final List<String> DROPPED_JVM_PREFIXES = List.of("-agentlib:jdwp", "-Xrunjdwp");
	private static final String GRAPHICS_BACKEND = "--graphicsBackend";
	private static final String ACCESS_TOKEN = "--accessToken";

	private RelaunchCommandBuilder() {}

	public static LaunchCommand build(HostJvm host, Path workingDirectory) {
		List<String> cmd = new ArrayList<>();
		cmd.add(host.javaExecutable().toString());
		for (String arg : host.jvmArgs()) {
			if (DROPPED_JVM_PREFIXES.stream().noneMatch(arg::startsWith)) {
				cmd.add(arg);
			}
		}
		cmd.add("-cp");
		cmd.add(host.classpath());
		cmd.add(host.mainClass());

		List<String> game = host.gameArgs();
		for (int i = 0; i < game.size(); i++) {
			String arg = game.get(i);
			if (arg.equals(GRAPHICS_BACKEND)) {
				if (i + 1 < game.size() && !game.get(i + 1).startsWith("--")) {
					i++; // drop its value too
				}
				continue;
			}
			if (arg.startsWith(GRAPHICS_BACKEND + "=")) {
				continue; // the same, in the form jopt also accepts
			}
			cmd.add(arg);
		}
		return new LaunchCommand(cmd, Map.of(), Set.of(), workingDirectory);
	}

	/** The command for the log: joined with spaces, the access token never shown. */
	public static String describe(LaunchCommand command) {
		List<String> shown = new ArrayList<>(command.command());
		for (int i = 0; i + 1 < shown.size(); i++) {
			if (shown.get(i).equals(ACCESS_TOKEN)) {
				shown.set(i + 1, "<redacted>");
			}
		}
		return String.join(" ", shown);
	}
}
