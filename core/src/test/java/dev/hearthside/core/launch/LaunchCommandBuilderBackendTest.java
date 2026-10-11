package dev.hearthside.core.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guests launch on the host's graphics backend (Spec C section 3.2). */
class LaunchCommandBuilderBackendTest {
	static final Path HOST_GAME_DIR = Path.of("C:\\mc");
	static final Path SAT_DIR = Path.of("C:\\mc\\hearthside\\satellite-1");

	static HostJvm host(List<String> gameArgs) {
		return new HostJvm(Path.of("C:\\jdk\\bin\\javaw.exe"), List.of("-XX:+UseG1GC"), "C:\\x.jar",
				"net.fabricmc.loader.impl.launch.knot.KnotClient", gameArgs, 8L << 30, HOST_GAME_DIR);
	}

	static SatelliteLaunch sat(GraphicsBackend backend) {
		return new SatelliteLaunch(1, 50123, SAT_DIR, "Bob_2", "s3cret", SatelliteLaunch.Presentation.COMPOSITED, backend);
	}

	@Test
	void satellitesAskForTheGivenBackend() {
		List<String> cmd = LaunchCommandBuilder.build(host(List.of("--version", "26.3")), sat(GraphicsBackend.VULKAN)).command();
		assertThat(cmd).containsSubsequence("--graphicsBackend", "vulkan");
		assertThat(Collections.frequency(cmd, "--graphicsBackend")).isEqualTo(1);
	}

	@Test
	void theHostsOwnBackendOptionIsReplaced() {
		List<String> cmd = LaunchCommandBuilder.build(
				host(List.of("--graphicsBackend", "opengl", "--version", "26.3")), sat(GraphicsBackend.VULKAN)).command();
		assertThat(cmd).containsSubsequence("--graphicsBackend", "vulkan").doesNotContain("opengl");
		assertThat(Collections.frequency(cmd, "--graphicsBackend")).isEqualTo(1);
		assertThat(cmd).containsSubsequence("--version", "26.3");
	}

	@Test
	void theOlderConstructorsDefaultToOpenGl() {
		assertThat(new SatelliteLaunch(1, 1, SAT_DIR, "Bob_2", "s").graphicsBackend()).isEqualTo(GraphicsBackend.OPENGL);
		assertThat(new SatelliteLaunch(1, 1, SAT_DIR, "Bob_2", "s", SatelliteLaunch.Presentation.TILED).graphicsBackend())
				.isEqualTo(GraphicsBackend.OPENGL);
	}

	@Test
	void backendArgumentsAreMinecraftsNames() {
		assertThat(GraphicsBackend.OPENGL.argument()).isEqualTo("opengl");
		assertThat(GraphicsBackend.VULKAN.argument()).isEqualTo("vulkan");
	}
}
