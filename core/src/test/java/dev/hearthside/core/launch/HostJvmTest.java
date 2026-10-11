package dev.hearthside.core.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostJvmTest {
	static final String KNOT = "net.fabricmc.loader.impl.launch.knot.KnotClient";
	static final Path GAME_DIR = Path.of("C:\\Users\\Jo Smith\\AppData\\Roaming\\.minecraft");

	@TempDir
	Path javaHome;

	LaunchFacts facts(String command, Map<String, String> props, List<String> loaderArgs) {
		return new LaunchFacts(javaHome, command, "a.jar;b.jar", List.of("-Xmx4G"), props, loaderArgs, GAME_DIR, 4L << 30);
	}

	@Test
	void officialLauncherTakesArgsFromLoader() throws Exception {
		var loaderArgs = List.of("--username", "Host", "--gameDir", "C:\\Users\\Jo Smith\\.minecraft");
		var h = HostJvm.capture(facts(KNOT + " --username Host --gameDir C:\\Users\\Jo Smith\\.minecraft", Map.of(), loaderArgs));
		assertThat(h.mainClass()).isEqualTo(KNOT);
		assertThat(h.gameArgs()).isEqualTo(loaderArgs);
		assertThat(h.classpath()).isEqualTo("a.jar;b.jar");
		assertThat(h.jvmArgs()).containsExactly("-Xmx4G");
		assertThat(h.maxHeapBytes()).isEqualTo(4L << 30);
		assertThat(h.javaExecutable()).isEqualTo(javaHome.resolve("bin").resolve("java"));
	}

	@Test
	void prismReadsMainClassAndArgsFromProperties() throws Exception {
		var h = HostJvm.capture(facts("org.prismlauncher.EntryPoint", Map.of(
				"org.prismlauncher.launch.mainclass", KNOT,
				"org.prismlauncher.launch.gameargs", "--username\u001FHost\u001F--version\u001F26.3"), List.of()));
		assertThat(h.mainClass()).isEqualTo(KNOT);
		assertThat(h.gameArgs()).containsExactly("--username", "Host", "--version", "26.3");
	}

	@Test
	void prismWithoutPropertiesIsUnsupported() {
		assertThatThrownBy(() -> HostJvm.capture(facts("org.prismlauncher.EntryPoint", Map.of(), List.of())))
				.isInstanceOf(UnsupportedLauncherException.class)
				.hasMessageContaining("Prism Launcher");
	}

	@Test
	void multiMcIsUnsupported() {
		assertThatThrownBy(() -> HostJvm.capture(facts("org.multimc.EntryPoint", Map.of(), List.of())))
				.isInstanceOf(UnsupportedLauncherException.class)
				.hasMessageContaining("MultiMC")
				.hasMessageContaining("Prism Launcher");
	}

	@Test
	void missingCommandIsUnsupported() {
		assertThatThrownBy(() -> HostJvm.capture(facts(null, Map.of(), List.of())))
				.isInstanceOf(UnsupportedLauncherException.class);
		assertThatThrownBy(() -> HostJvm.capture(facts("  ", Map.of(), List.of())))
				.isInstanceOf(UnsupportedLauncherException.class);
	}

	@Test
	void devLaunchInjectorKeepsPassthroughArgs() throws Exception {
		var h = HostJvm.capture(facts("net.fabricmc.devlaunchinjector.Main --demo --gameDir C:\\Users\\Jo Smith\\run",
				Map.of(), List.of("--assetIndex", "29", "--demo")));
		assertThat(h.mainClass()).isEqualTo("net.fabricmc.devlaunchinjector.Main");
		assertThat(h.gameArgs()).containsExactly("--demo", "--gameDir", "C:\\Users\\Jo Smith\\run");
	}

	@Test
	void gameDirIsCarried() throws Exception {
		assertThat(HostJvm.capture(facts(KNOT, Map.of(), List.of())).gameDir()).isEqualTo(GAME_DIR);
	}

	@Test
	void prefersJavawWhenPresent() throws Exception {
		Files.createDirectories(javaHome.resolve("bin"));
		Files.createFile(javaHome.resolve("bin").resolve("javaw.exe"));
		assertThat(HostJvm.capture(facts(KNOT, Map.of(), List.of())).javaExecutable())
				.isEqualTo(javaHome.resolve("bin").resolve("javaw.exe"));
	}
}
