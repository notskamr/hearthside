package dev.hearthside.core.launch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a satellite's command line from the host's own launch.
 *
 * <p>The satellite runs the host's exact JVM, classpath and mods, with a smaller heap, without the
 * host's debugger or class-data archive, and with the host's account identity replaced by the
 * guest's. The launch secret travels in the environment, never on the command line.
 */
public final class LaunchCommandBuilder {
	public static final double HEAP_FRACTION = 0.6;
	public static final long MIN_HEAP_MB = 1024;
	public static final String SECRET_ENV = "HEARTHSIDE_LAUNCH_SECRET";

	/** Fabric Loader 0.19.5 {@code SystemProperties.MODS_FOLDER}. */
	private static final String MODS_FOLDER_ARG = "-Dfabric.modsFolder=";

	private static final List<String> DROPPED_JVM_PREFIXES = List.of(
			"-Xmx", "-Xms", "-agentlib:jdwp", "-Xrunjdwp",
			"-XX:SharedArchiveFile", "-XX:ArchiveClassesAtExit", "-Dhearthside.");

	private static final Set<String> DROPPED_GAME_OPTIONS = Set.of(
			"--username", "--uuid", "--accessToken", "--xuid", "--clientId", "--userType", "--gameDir",
			"--quickPlayPath", "--quickPlaySingleplayer", "--quickPlayMultiplayer", "--quickPlayRealms",
			"--server", "--port");

	/**
	 * The JVM reads extra options from these; a debugger configured through them would make every
	 * satellite fight the host for the same debug port.
	 */
	private static final Set<String> INHERITED_JVM_OPTION_VARIABLES =
			Set.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS");

	private LaunchCommandBuilder() {}

	public static LaunchCommand build(HostJvm host, SatelliteLaunch sat) {
		List<String> cmd = new ArrayList<>();
		cmd.add(host.javaExecutable().toString());

		for (String arg : host.jvmArgs()) {
			if (DROPPED_JVM_PREFIXES.stream().noneMatch(arg::startsWith)) {
				cmd.add(arg);
			}
		}
		long heapMb = Math.max(MIN_HEAP_MB, (long) (host.maxHeapBytes() * HEAP_FRACTION / (1024 * 1024)));
		cmd.add("-Xmx" + heapMb + "m");
		cmd.add("-Dhearthside.role=satellite");
		cmd.add("-Dhearthside.controlPort=" + sat.controlPort());
		cmd.add("-Dhearthside.player=" + sat.playerIndex());
		if (host.jvmArgs().stream().noneMatch(a -> a.startsWith(MODS_FOLDER_ARG))) {
			// The satellite's own --gameDir would otherwise make Fabric load mods from <satellite>/mods.
			cmd.add(MODS_FOLDER_ARG + host.gameDir().resolve("mods").toAbsolutePath());
		}

		cmd.add("-cp");
		cmd.add(host.classpath());
		cmd.add(host.mainClass());

		List<String> game = host.gameArgs();
		for (int i = 0; i < game.size(); i++) {
			String arg = game.get(i);
			if (DROPPED_GAME_OPTIONS.contains(arg)) {
				if (i + 1 < game.size() && !game.get(i + 1).startsWith("--")) {
					i++; // drop its value too
				}
				continue;
			}
			cmd.add(arg);
		}
		cmd.add("--gameDir");
		cmd.add(sat.gameDir().toString());
		cmd.add("--username");
		cmd.add(sat.guestName());
		cmd.add("--accessToken");
		cmd.add("0");

		return new LaunchCommand(cmd, Map.of(SECRET_ENV, sat.launchSecret()), INHERITED_JVM_OPTION_VARIABLES);
	}
}
