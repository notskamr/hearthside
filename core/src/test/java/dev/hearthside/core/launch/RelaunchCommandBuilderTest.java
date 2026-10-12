package dev.hearthside.core.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Starting player 1's game again after "Switch and restart" (Spec D section 3.1): the same command line, minus a forced
 * graphics backend (the saved option chooses Vulkan) and a debugger agent (its port is still taken while this game
 * closes). Its description for the log never shows the access token.
 */
class RelaunchCommandBuilderTest {
	static final Path WORK = Path.of("C:\\Users\\Jo Smith\\AppData\\Roaming\\.minecraft");
	static final String KNOT = "net.fabricmc.loader.impl.launch.knot.KnotClient";

	static HostJvm host(String mainClass, List<String> gameArgs) {
		return new HostJvm(Path.of("C:\\jdk\\bin\\javaw.exe"),
				List.of("-Xmx4G", "-XX:+UseG1GC", "-Dfabric.debug.x=1", "-agentlib:jdwp=transport=dt_socket,server=y,address=5005"),
				"C:\\a b\\x.jar;C:\\y.jar", mainClass, gameArgs, 4L << 30, WORK);
	}

	final HostJvm official = host(KNOT, List.of("--username", "Jo", "--version", "fabric-26.3", "--gameDir", "C:\\mc",
			"--accessToken", "SECRET_TOKEN", "--uuid", "abc"));

	@Test
	void keepsJvmArgsClasspathMainClassAndGameArgs() {
		List<String> cmd = RelaunchCommandBuilder.build(official, WORK).command();
		assertThat(cmd.getFirst()).isEqualTo("C:\\jdk\\bin\\javaw.exe");
		assertThat(cmd).containsSubsequence("-Xmx4G", "-XX:+UseG1GC", "-Dfabric.debug.x=1", "-cp", "C:\\a b\\x.jar;C:\\y.jar",
				KNOT, "--username", "Jo", "--version", "fabric-26.3", "--gameDir", "C:\\mc", "--accessToken", "SECRET_TOKEN",
				"--uuid", "abc");
	}

	@Test
	void dropsTheDebuggerAgent() {
		assertThat(RelaunchCommandBuilder.build(official, WORK).command()).noneMatch(a -> a.contains("jdwp"));
	}

	@Test
	void dropsAForcedGraphicsBackend() {
		HostJvm forced = host(KNOT, List.of("--username", "Jo", "--graphicsBackend", "opengl", "--version", "fabric-26.3"));
		List<String> cmd = RelaunchCommandBuilder.build(forced, WORK).command();
		assertThat(cmd).doesNotContain("--graphicsBackend", "opengl");
		assertThat(cmd).containsSubsequence("--username", "Jo", "--version", "fabric-26.3");
	}

	@Test
	void keepsPrismsMainClassAndArguments() {
		// HostJvm.capture has already swapped Prism's entry point for the game's own main class and arguments.
		HostJvm prism = host(KNOT, List.of("--username", "Jo", "--version", "26.3", "--gameDir",
				"C:\\Prism\\instances\\co-op\\.minecraft", "--assetsDir", "C:\\Prism\\assets", "--accessToken", "SECRET_TOKEN"));
		List<String> cmd = RelaunchCommandBuilder.build(prism, WORK).command();
		assertThat(cmd).containsSubsequence(KNOT, "--username", "Jo", "--version", "26.3", "--gameDir",
				"C:\\Prism\\instances\\co-op\\.minecraft", "--assetsDir", "C:\\Prism\\assets");
		assertThat(cmd).noneMatch(a -> a.contains("prismlauncher"));
	}

	@Test
	void describeRedactsTheAccessToken() {
		String described = RelaunchCommandBuilder.describe(RelaunchCommandBuilder.build(official, WORK));
		assertThat(described).doesNotContain("SECRET_TOKEN").contains("--accessToken <redacted>");
		assertThat(described).contains(KNOT, "--username Jo");
	}

	@Test
	void usesTheGivenWorkingDirectoryAndNoExtraEnvironment() {
		LaunchCommand command = RelaunchCommandBuilder.build(official, WORK);
		assertThat(command.workingDirectory()).isEqualTo(WORK);
		assertThat(command.environment()).isEmpty();
		assertThat(command.removedEnvironment()).isEmpty();
	}
}
