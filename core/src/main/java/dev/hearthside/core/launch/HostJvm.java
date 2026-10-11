package dev.hearthside.core.launch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How the host game was launched, reconstructed from inside the running JVM so a satellite can be
 * started the same way.
 */
public record HostJvm(Path javaExecutable, List<String> jvmArgs, String classpath, String mainClass,
		List<String> gameArgs, long maxHeapBytes, Path gameDir) {
	static final String PRISM_ENTRY = "org.prismlauncher.EntryPoint";
	static final String MULTIMC_ENTRY = "org.multimc.EntryPoint";
	static final String DEV_LAUNCH_INJECTOR = "net.fabricmc.devlaunchinjector.Main";
	static final String PRISM_MAIN_CLASS = "org.prismlauncher.launch.mainclass";
	static final String PRISM_GAME_ARGS = "org.prismlauncher.launch.gameargs";

	private static final String UNREADABLE = "Hearthside couldn't read how Minecraft was started, so it can't open"
			+ " extra players. Please launch with the official Minecraft Launcher or Prism Launcher.";
	private static final String MULTIMC = "MultiMC isn't supported yet. Please use Prism Launcher or the official"
			+ " Minecraft Launcher.";

	public HostJvm {
		jvmArgs = List.copyOf(jvmArgs);
		gameArgs = List.copyOf(gameArgs);
	}

	public static HostJvm capture(LaunchFacts f) throws UnsupportedLauncherException {
		if (f.sunJavaCommand() == null || f.sunJavaCommand().isBlank()) {
			throw new UnsupportedLauncherException(UNREADABLE);
		}
		String[] tokens = f.sunJavaCommand().strip().split(" ");
		String first = tokens[0];

		String mainClass;
		List<String> gameArgs;
		switch (first) {
			case PRISM_ENTRY -> {
				mainClass = f.properties().get(PRISM_MAIN_CLASS);
				String joined = f.properties().get(PRISM_GAME_ARGS);
				if (mainClass == null || joined == null) {
					throw new UnsupportedLauncherException(UNREADABLE);
				}
				gameArgs = joined.isEmpty() ? List.of() : Arrays.asList(joined.split("\u001F", -1));
			}
			case MULTIMC_ENTRY -> throw new UnsupportedLauncherException(MULTIMC);
			case DEV_LAUNCH_INJECTOR -> {
				// Loom's launcher re-adds its own arguments from its config file; pass only what it was given.
				mainClass = first;
				gameArgs = groupOptions(tokens);
			}
			default -> {
				mainClass = first;
				gameArgs = f.loaderLaunchArgs();
			}
		}

		Path bin = f.javaHome().resolve("bin");
		Path javaw = bin.resolve("javaw.exe");
		Path java = Files.exists(javaw) ? javaw : bin.resolve("java");
		return new HostJvm(java, f.inputArgs(), f.classpath(), mainClass, gameArgs, f.maxHeapBytes(), f.gameDir());
	}

	/**
	 * {@code sun.java.command} is space-joined with quoting lost, so each {@code --option} owns
	 * every token up to the next {@code --} token; values with spaces (Windows paths) survive.
	 */
	private static List<String> groupOptions(String[] tokens) {
		List<String> args = new ArrayList<>();
		StringBuilder value = null;
		for (int i = 1; i < tokens.length; i++) {
			String token = tokens[i];
			if (token.startsWith("--")) {
				flush(args, value);
				args.add(token);
				value = new StringBuilder();
			} else if (value != null) {
				if (!value.isEmpty()) {
					value.append(' ');
				}
				value.append(token);
			}
		}
		flush(args, value);
		return args;
	}

	private static void flush(List<String> args, StringBuilder value) {
		if (value != null && !value.isEmpty()) {
			args.add(value.toString());
		}
	}
}
