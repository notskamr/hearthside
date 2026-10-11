package dev.hearthside.core.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaunchCommandBuilderTest {
	final HostJvm host = new HostJvm(Path.of("C:\\jdk\\bin\\javaw.exe"),
			List.of("-Xmx8G", "-Xms2G", "-agentlib:jdwp=transport=dt_socket,server=y,address=5005",
					"-XX:+UseG1GC", "-Dhearthside.role=host"),
			"C:\\a b\\x.jar;C:\\y.jar", "net.fabricmc.loader.impl.launch.knot.KnotClient",
			List.of("--username", "HostName", "--accessToken", "SECRET_HOST_TOKEN", "--uuid", "abc",
					"--gameDir", "C:\\mc", "--assetsDir", "C:\\mc\\assets", "--assetIndex", "29", "--version", "26.3"),
			8L << 30);
	final SatelliteLaunch sat = new SatelliteLaunch(1, 50123, Path.of("C:\\mc\\hearthside\\satellite-1"), "Bob_2", "s3cret");

	@Test
	void startsWithJavaExecutable() {
		assertThat(LaunchCommandBuilder.build(host, sat).command().getFirst()).isEqualTo("C:\\jdk\\bin\\javaw.exe");
	}

	@Test
	void debuggerAgentIsDropped() {
		assertThat(LaunchCommandBuilder.build(host, sat).command()).noneMatch(a -> a.contains("jdwp"));
	}

	@Test
	void heapIsSixtyPercentOfHost() {
		var cmd = LaunchCommandBuilder.build(host, sat).command();
		assertThat(cmd).contains("-Xmx4915m").doesNotContain("-Xmx8G", "-Xms2G");
	}

	@Test
	void heapHasFloor() {
		var small = new HostJvm(host.javaExecutable(), host.jvmArgs(), host.classpath(), host.mainClass(),
				host.gameArgs(), 1L << 30);
		assertThat(LaunchCommandBuilder.build(small, sat).command()).contains("-Xmx1024m");
	}

	@Test
	void hostIdentityNeverLeaks() {
		var cmd = LaunchCommandBuilder.build(host, sat).command();
		assertThat(cmd).doesNotContain("SECRET_HOST_TOKEN", "HostName", "abc", "-Dhearthside.role=host");
		assertThat(cmd).containsSubsequence("--username", "Bob_2").containsSubsequence("--accessToken", "0");
	}

	@Test
	void roleAndControlPortAreSet() {
		assertThat(LaunchCommandBuilder.build(host, sat).command()).contains(
				"-Dhearthside.role=satellite", "-Dhearthside.controlPort=50123", "-Dhearthside.player=1", "-XX:+UseG1GC");
	}

	@Test
	void gameDirWithSpacesStaysOneArgument() {
		var spaced = new SatelliteLaunch(1, 1, Path.of("C:\\Users\\Jo Smith\\sat 1"), "Bob_2", "s");
		assertThat(LaunchCommandBuilder.build(host, spaced).command())
				.containsSubsequence("--gameDir", "C:\\Users\\Jo Smith\\sat 1")
				.containsSubsequence("-cp", "C:\\a b\\x.jar;C:\\y.jar");
	}

	@Test
	void keepsAssetArgsAndReplacesGameDir() {
		var cmd = LaunchCommandBuilder.build(host, sat).command();
		assertThat(cmd).containsSubsequence("--assetIndex", "29").containsSubsequence("--version", "26.3");
		assertThat(Collections.frequency(cmd, "--gameDir")).isEqualTo(1);
	}

	@Test
	void valuelessFlagsSurvive() {
		var demo = new HostJvm(host.javaExecutable(), List.of(), "cp", "Main",
				List.of("--demo", "--username", "HostName", "--fullscreen"), 4L << 30);
		var cmd = LaunchCommandBuilder.build(demo, sat).command();
		assertThat(cmd).contains("--demo", "--fullscreen").doesNotContain("HostName");
	}

	@Test
	void secretOnlyInEnvironment() {
		var lc = LaunchCommandBuilder.build(host, sat);
		assertThat(lc.command()).doesNotContain("s3cret");
		assertThat(lc.environment()).containsEntry("HEARTHSIDE_LAUNCH_SECRET", "s3cret");
	}
}
