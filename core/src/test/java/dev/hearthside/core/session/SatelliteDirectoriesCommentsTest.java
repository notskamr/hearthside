package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Iris rewrites {@code iris.properties} with the date in a comment whenever a game starts: that isn't the guest's
 * change, so the host's settings still reach the guest. A changed value still is.
 */
class SatelliteDirectoriesCommentsTest {
	@TempDir
	Path host;

	Path hostFile(String content) throws Exception {
		Path file = host.resolve("config/iris.properties");
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
		return file;
	}

	@Test
	void aRewrittenCommentIsNotTheGuestsChange() throws Exception {
		hostFile("#Iris\n#Sun Oct 11 22:24:43 EDT 2026\nshaderPack=\n");
		Path sat = SatelliteDirectories.prepare(host, 1);
		Files.writeString(sat.resolve("config/iris.properties"), "#Iris\r\n#Sun Oct 11 22:25:16 EDT 2026\r\nshaderPack=\r\n");
		hostFile("#Iris\n#Sun Oct 11 23:00:00 EDT 2026\nshaderPack=Complementary.zip\n");
		SatelliteDirectories.prepare(host, 1);
		assertThat(sat.resolve("config/iris.properties")).content().contains("shaderPack=Complementary.zip");
	}

	@Test
	void aChangedValueIsTheGuests() throws Exception {
		hostFile("#Iris\nenableShaders=true\n");
		Path sat = SatelliteDirectories.prepare(host, 1);
		Files.writeString(sat.resolve("config/iris.properties"), "#Iris, later\nenableShaders=false\n");
		hostFile("#Iris\nenableShaders=true\nshaderPack=x\n");
		SatelliteDirectories.prepare(host, 1);
		assertThat(sat.resolve("config/iris.properties")).content().contains("enableShaders=false");
	}

	@Test
	void aContinuedValueStartingWithAHashCounts() throws Exception {
		hostFile("list=a,\\\n#b\n");
		Path sat = SatelliteDirectories.prepare(host, 1);
		Files.writeString(sat.resolve("config/iris.properties"), "list=a,\\\n#c\n");
		hostFile("list=a,\\\n#d\n");
		SatelliteDirectories.prepare(host, 1);
		assertThat(sat.resolve("config/iris.properties")).hasContent("list=a,\\\n#c\n");
	}
}
