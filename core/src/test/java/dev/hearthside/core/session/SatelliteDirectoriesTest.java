package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SatelliteDirectoriesTest {
	@TempDir
	Path host;

	@Test
	void createsSatelliteFolder() throws Exception {
		Path sat = SatelliteDirectories.prepare(host, 2);
		assertThat(sat).isEqualTo(host.resolve("hearthside").resolve("satellite-2"));
		assertThat(sat).isDirectory();
	}

	@Test
	void mirrorsHostConfigIncludingSubfolders() throws Exception {
		Files.createDirectories(host.resolve("config/iris"));
		Files.writeString(host.resolve("config/sodium-options.json"), "{}");
		Files.writeString(host.resolve("config/iris/x.properties"), "a=1");
		Path sat = SatelliteDirectories.prepare(host, 1);
		assertThat(sat.resolve("config/sodium-options.json")).hasContent("{}");
		assertThat(sat.resolve("config/iris/x.properties")).hasContent("a=1");
	}

	@Test
	void hostConfigOverwritesStaleCopy() throws Exception {
		Files.createDirectories(host.resolve("config"));
		Files.writeString(host.resolve("config/a.json"), "old");
		Path sat = SatelliteDirectories.prepare(host, 1);
		Files.writeString(host.resolve("config/a.json"), "new");
		SatelliteDirectories.prepare(host, 1);
		assertThat(sat.resolve("config/a.json")).hasContent("new");
	}

	@Test
	void satelliteOnlyConfigFilesSurvive() throws Exception {
		Path sat = SatelliteDirectories.prepare(host, 1);
		Files.createDirectories(sat.resolve("config"));
		Files.writeString(sat.resolve("config/mine.json"), "keep");
		SatelliteDirectories.prepare(host, 1);
		assertThat(sat.resolve("config/mine.json")).hasContent("keep");
	}

	@Test
	void optionsSeededOnlyOnce() throws Exception {
		Files.writeString(host.resolve("options.txt"), "renderDistance:12");
		Path sat = SatelliteDirectories.prepare(host, 1);
		assertThat(sat.resolve("options.txt")).hasContent("renderDistance:12");
		Files.writeString(sat.resolve("options.txt"), "renderDistance:6");
		SatelliteDirectories.prepare(host, 1);
		assertThat(sat.resolve("options.txt")).hasContent("renderDistance:6");
	}

	@Test
	void missingHostConfigIsFine() throws Exception {
		Path sat = SatelliteDirectories.prepare(host, 3);
		assertThat(sat.resolve("config")).doesNotExist();
		assertThat(sat.resolve("options.txt")).doesNotExist();
	}
}
