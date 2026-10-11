package dev.hearthside.core.process;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.launch.LaunchCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdkProcessLauncherTest {
	@TempDir
	Path dir;

	final JdkProcessLauncher launcher = new JdkProcessLauncher();

	static LaunchCommand probe(Map<String, String> env, Set<String> removed, String... probeArgs) {
		return probeIn(null, env, removed, probeArgs);
	}

	static LaunchCommand probeIn(Path workingDirectory, Map<String, String> env, Set<String> removed, String... probeArgs) {
		List<String> cmd = new ArrayList<>(List.of(
				Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-cp", System.getProperty("java.class.path"),
				EnvProbe.class.getName()));
		cmd.addAll(List.of(probeArgs));
		return new LaunchCommand(cmd, env, removed, workingDirectory);
	}

	@Test
	void runsInTheGivenWorkingDirectory() throws Exception {
		Path satellite = Files.createDirectories(dir.resolve("satellite 1"));
		Path log = dir.resolve("launch.log");
		assertThat(launcher.start(probeIn(satellite, Map.of(), Set.of(), "--cwd"), log).onExit().get(30, SECONDS)).isZero();
		assertThat(Files.readString(log).strip()).isEqualTo("user.dir=" + satellite.toAbsolutePath());
	}

	@Test
	void addsEnvironmentAndLogsOutput() throws Exception {
		Path log = dir.resolve("logs/launch.log");
		var p = launcher.start(probe(Map.of("HEARTHSIDE_LAUNCH_SECRET", "s3cret"), Set.of(), "HEARTHSIDE_LAUNCH_SECRET"), log);
		assertThat(p.onExit().get(30, SECONDS)).isZero();
		assertThat(Files.readString(log)).contains("HEARTHSIDE_LAUNCH_SECRET=s3cret");
	}

	@Test
	void removesInheritedVariables() throws Exception {
		// The Gradle test JVM sets HEARTHSIDE_TEST_INHERITED=leak (see core/build.gradle).
		Path log = dir.resolve("launch.log");
		var kept = launcher.start(probe(Map.of(), Set.of(), "HEARTHSIDE_TEST_INHERITED"), log);
		assertThat(kept.onExit().get(30, SECONDS)).isZero();
		var removed = launcher.start(probe(Map.of(), Set.of("HEARTHSIDE_TEST_INHERITED"), "HEARTHSIDE_TEST_INHERITED"), log);
		assertThat(removed.onExit().get(30, SECONDS)).isZero();
		assertThat(Files.readAllLines(log))
				.containsExactly("HEARTHSIDE_TEST_INHERITED=leak", "HEARTHSIDE_TEST_INHERITED=<unset>");
	}

	@Test
	void appendsAcrossRuns() throws Exception {
		Path log = dir.resolve("launch.log");
		for (int i = 0; i < 2; i++) {
			launcher.start(probe(Map.of("X", "run"), Set.of(), "X"), log).onExit().get(30, SECONDS);
		}
		assertThat(Files.readAllLines(log)).containsExactly("X=run", "X=run");
	}

	@Test
	void destroyEndsTheProcess() throws Exception {
		var p = launcher.start(probe(Map.of(), Set.of(), "--sleep"), dir.resolve("launch.log"));
		assertThat(p.isAlive()).isTrue();
		assertThat(p.pid()).isPositive();
		p.destroy();
		p.onExit().get(5, SECONDS);
		assertThat(p.isAlive()).isFalse();
	}
}
