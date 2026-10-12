package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Mirroring the host's config never stops a guest joining and never copies half-written files (batch review M1). */
class SatelliteDirectoriesRobustnessTest {
	@TempDir
	Path host;

	@Test
	void temporaryFilesAreNotMirrored() throws Exception {
		Files.createDirectories(host.resolve("config"));
		Files.writeString(host.resolve("config/hearthside.json"), "{}");
		Files.writeString(host.resolve("config/hearthside.json.tmp"), "half");
		Files.writeString(host.resolve("config/sodium-options.json.tmp"), "half");
		Path sat = SatelliteDirectories.prepare(host, 1);
		assertThat(sat.resolve("config/hearthside.json")).exists();
		assertThat(sat.resolve("config/hearthside.json.tmp")).doesNotExist();
		assertThat(sat.resolve("config/sodium-options.json.tmp")).doesNotExist();
	}

	@Test
	void aMirrorRecordThatCantBeWrittenDoesNotStopTheGuest() throws Exception {
		Files.createDirectories(host.resolve("config"));
		Files.writeString(host.resolve("config/sodium-options.json"), "host");
		Path sat = SatelliteDirectories.prepare(host, 1);
		// Make the record unwritable: a directory where the file recording what was mirrored lives.
		Path record = SatelliteDirectories.mirrorRecord(sat);
		Files.deleteIfExists(record);
		Files.createDirectories(record);
		Files.writeString(host.resolve("config/sodium-options.json"), "host changed");
		assertThatCode(() -> SatelliteDirectories.prepare(host, 1)).doesNotThrowAnyException();
		assertThat(sat.resolve("config/sodium-options.json")).exists();
	}
}
