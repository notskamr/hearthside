package dev.hearthside.core.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostJvmTest {
	@Test
	void hostCommandWithSpacedValuesParses(@TempDir Path javaHome) {
		var h = HostJvm.parse(javaHome,
				"net.fabricmc.loader.impl.launch.knot.KnotClient --username Host --gameDir C:\\Users\\Jo Smith\\AppData\\Roaming\\.minecraft --assetIndex 29 --demo",
				"a.jar;b.jar", List.of("-Xmx4G"), 4L << 30);
		assertThat(h.mainClass()).isEqualTo("net.fabricmc.loader.impl.launch.knot.KnotClient");
		assertThat(h.gameArgs()).containsExactly("--username", "Host",
				"--gameDir", "C:\\Users\\Jo Smith\\AppData\\Roaming\\.minecraft", "--assetIndex", "29", "--demo");
		assertThat(h.javaExecutable()).isEqualTo(javaHome.resolve("bin").resolve("java"));
		assertThat(h.classpath()).isEqualTo("a.jar;b.jar");
		assertThat(h.jvmArgs()).containsExactly("-Xmx4G");
		assertThat(h.maxHeapBytes()).isEqualTo(4L << 30);
	}

	@Test
	void prefersJavawWhenPresent(@TempDir Path javaHome) throws Exception {
		Files.createDirectories(javaHome.resolve("bin"));
		Files.createFile(javaHome.resolve("bin").resolve("javaw.exe"));
		var h = HostJvm.parse(javaHome, "Main", "", List.of(), 1L << 30);
		assertThat(h.javaExecutable()).isEqualTo(javaHome.resolve("bin").resolve("javaw.exe"));
	}

	@Test
	void mainClassWithoutArgs(@TempDir Path javaHome) {
		var h = HostJvm.parse(javaHome, "Main", "", List.of(), 1L << 30);
		assertThat(h.mainClass()).isEqualTo("Main");
		assertThat(h.gameArgs()).isEmpty();
	}
}
