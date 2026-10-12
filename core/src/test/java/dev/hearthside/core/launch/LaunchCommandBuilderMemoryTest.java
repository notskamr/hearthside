package dev.hearthside.core.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guests get the configured heap and render-distance cap, not a fraction of the host's heap (plan Task 8b). */
class LaunchCommandBuilderMemoryTest {
	static final Path SAT_DIR = Path.of("C:\\mc\\hearthside\\satellite-1");

	static HostJvm host(long maxHeapBytes) {
		return new HostJvm(Path.of("C:\\jdk\\bin\\javaw.exe"), List.of("-Xmx8G", "-XX:+UseG1GC"), "C:\\x.jar",
				"net.fabricmc.loader.impl.launch.knot.KnotClient", List.of("--version", "26.3"), maxHeapBytes, Path.of("C:\\mc"));
	}

	static SatelliteLaunch sat(int heapMb, int renderDistanceCap) {
		return new SatelliteLaunch(1, 50123, SAT_DIR, "Bob_2", "s3cret", SatelliteLaunch.Presentation.COMPOSITED,
				GraphicsBackend.OPENGL, heapMb, renderDistanceCap);
	}

	@Test
	void guestsGetTheConfiguredHeapNotAFractionOfTheHost() {
		List<String> cmd = LaunchCommandBuilder.build(host(8L << 30), sat(1536, 8)).command();
		assertThat(cmd).contains("-Xmx1536m").doesNotContain("-Xmx4915m", "-Xmx8G");
		assertThat(cmd.stream().filter(a -> a.startsWith("-Xmx"))).hasSize(1);
	}

	@Test
	void theRenderDistanceCapIsPassed() {
		assertThat(LaunchCommandBuilder.build(host(8L << 30), sat(1536, 8)).command())
				.contains("-Dhearthside.renderDistanceCap=8");
		assertThat(LaunchCommandBuilder.build(host(8L << 30), sat(1536, 0)).command())
				.contains("-Dhearthside.renderDistanceCap=0");
	}

	@Test
	void theOlderConstructorsUseTheDefaults() {
		SatelliteLaunch old = new SatelliteLaunch(1, 1, SAT_DIR, "Bob_2", "s");
		assertThat(old.heapMb()).isEqualTo(1536);
		assertThat(old.renderDistanceCap()).isEqualTo(8);
		assertThat(Collections.frequency(LaunchCommandBuilder.build(host(2L << 30), old).command(), "-Xmx1536m")).isEqualTo(1);
	}
}
