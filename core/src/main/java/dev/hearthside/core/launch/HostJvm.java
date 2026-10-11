package dev.hearthside.core.launch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * How the host game was launched, reconstructed from inside the running JVM.
 *
 * <p>The Windows launcher's real command line is not reliably readable, so it is rebuilt from
 * {@code java.home}, {@code sun.java.command}, {@code java.class.path} and the runtime's input
 * arguments.
 */
public record HostJvm(Path javaExecutable, List<String> jvmArgs, String classpath, String mainClass,
		List<String> gameArgs, long maxHeapBytes) {
	public HostJvm {
		jvmArgs = List.copyOf(jvmArgs);
		gameArgs = List.copyOf(gameArgs);
	}

	/**
	 * @param sunJavaCommand the main class followed by its arguments, space-joined with quoting lost.
	 *     Each {@code --option} owns every token up to the next {@code --} token, so values containing
	 *     spaces (such as Windows paths) are recovered intact.
	 */
	public static HostJvm parse(Path javaHome, String sunJavaCommand, String classpath, List<String> jvmArgs,
			long maxHeapBytes) {
		String[] tokens = sunJavaCommand.strip().split(" ");
		String mainClass = tokens[0];

		List<String> gameArgs = new ArrayList<>();
		StringBuilder value = null;
		for (int i = 1; i < tokens.length; i++) {
			String token = tokens[i];
			if (token.startsWith("--")) {
				flush(gameArgs, value);
				gameArgs.add(token);
				value = new StringBuilder();
			} else if (value != null) {
				if (!value.isEmpty()) {
					value.append(' ');
				}
				value.append(token);
			}
		}
		flush(gameArgs, value);

		Path bin = javaHome.resolve("bin");
		Path javaw = bin.resolve("javaw.exe");
		Path java = Files.exists(javaw) ? javaw : bin.resolve("java");
		return new HostJvm(java, jvmArgs, classpath, mainClass, gameArgs, maxHeapBytes);
	}

	private static void flush(List<String> gameArgs, StringBuilder value) {
		if (value != null && !value.isEmpty()) {
			gameArgs.add(value.toString());
		}
	}
}
