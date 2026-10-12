package dev.hearthside.core.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A preset's session settings reach a guest's game as launch flags, like the render-distance cap (Spec D section 3.3). */
class LaunchCommandBuilderTuningTest {
	static final HostJvm HOST = new HostJvm(Path.of("C:\\jdk\\bin\\javaw.exe"), List.of("-XX:+UseG1GC"), "C:\\x.jar",
			"net.fabricmc.loader.impl.launch.knot.KnotClient", List.of("--version", "26.3"), 8L << 30, Path.of("C:\\mc"));

	static SatelliteLaunch sat(int renderDistanceCap, boolean lightGraphics, int fpsCap) {
		return new SatelliteLaunch(1, 50123, Path.of("C:\\mc\\hearthside\\satellite-1"), "Bob_2", "s3cret",
				SatelliteLaunch.Presentation.COMPOSITED, GraphicsBackend.OPENGL, 1536, renderDistanceCap, lightGraphics, fpsCap);
	}

	@Test
	void passesLightGraphicsAndFpsCap() {
		assertThat(LaunchCommandBuilder.build(HOST, sat(6, true, 45)).command()).contains(
				"-Dhearthside.renderDistanceCap=6", "-Dhearthside.lightGraphics=true", "-Dhearthside.fpsCap=45");
	}

	@Test
	void omitsThemUnderQuality() {
		assertThat(LaunchCommandBuilder.build(HOST, sat(0, false, 0)).command())
				.noneMatch(a -> a.startsWith("-Dhearthside.lightGraphics"))
				.noneMatch(a -> a.startsWith("-Dhearthside.fpsCap"));
	}

	@Test
	void theOlderConstructorsChangeNothing() {
		SatelliteLaunch older = new SatelliteLaunch(1, 50123, Path.of("C:\\mc\\hearthside\\satellite-1"), "Bob_2", "s3cret",
				SatelliteLaunch.Presentation.COMPOSITED, GraphicsBackend.OPENGL, 1536, 8);
		assertThat(older.lightGraphics()).isFalse();
		assertThat(older.fpsCap()).isZero();
	}
}
