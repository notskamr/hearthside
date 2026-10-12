package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A guest's own mod settings survive its next launch (user session, 2026-10-11): the host's config is mirrored into a
 * guest's folder file by file, except files the guest has changed since they were last mirrored. Hearthside's own
 * config always follows the host.
 */
class SatelliteDirectoriesMirrorTest {
	@TempDir
	Path host;

	Path hostFile(String name, String content) throws Exception {
		Path file = host.resolve("config").resolve(name);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
		return file;
	}

	@Test
	void anUntouchedCopyFollowsTheHost() throws Exception {
		hostFile("sodium-options.json", "old");
		Path sat = SatelliteDirectories.prepare(host, 1);
		hostFile("sodium-options.json", "new");
		SatelliteDirectories.prepare(host, 1);
		assertThat(sat.resolve("config/sodium-options.json")).hasContent("new");
	}

	@Test
	void aFileTheGuestChangedIsKept() throws Exception {
		hostFile("sodium-options.json", "host");
		Path sat = SatelliteDirectories.prepare(host, 1);
		Files.writeString(sat.resolve("config/sodium-options.json"), "mine");
		hostFile("sodium-options.json", "host changed");
		SatelliteDirectories.prepare(host, 1);
		assertThat(sat.resolve("config/sodium-options.json")).hasContent("mine");
	}

	@Test
	void aGuestsChangeSurvivesSeveralLaunches() throws Exception {
		hostFile("sodium-options.json", "host");
		Path sat = SatelliteDirectories.prepare(host, 1);
		Files.writeString(sat.resolve("config/sodium-options.json"), "mine");
		for (int launch = 0; launch < 3; launch++) {
			hostFile("sodium-options.json", "host " + launch);
			SatelliteDirectories.prepare(host, 1);
		}
		assertThat(sat.resolve("config/sodium-options.json")).hasContent("mine");
	}

	@Test
	void newHostFilesArrive() throws Exception {
		hostFile("a.json", "a");
		Path sat = SatelliteDirectories.prepare(host, 1);
		hostFile("iris/b.properties", "b=1");
		SatelliteDirectories.prepare(host, 1);
		assertThat(sat.resolve("config/a.json")).hasContent("a");
		assertThat(sat.resolve("config/iris/b.properties")).hasContent("b=1");
	}

	@Test
	void hearthsidesOwnConfigAlwaysFollowsTheHost() throws Exception {
		hostFile("hearthside.json", "{\"guestMemoryMb\": 1536}");
		Path sat = SatelliteDirectories.prepare(host, 1);
		Files.writeString(sat.resolve("config/hearthside.json"), "{\"guestMemoryMb\": 4096}");
		hostFile("hearthside.json", "{\"guestMemoryMb\": 1024}");
		SatelliteDirectories.prepare(host, 1);
		assertThat(sat.resolve("config/hearthside.json")).hasContent("{\"guestMemoryMb\": 1024}");
	}

	@Test
	void eachSeatKeepsItsOwnChanges() throws Exception {
		hostFile("sodium-options.json", "host");
		Path one = SatelliteDirectories.prepare(host, 1);
		Path two = SatelliteDirectories.prepare(host, 2);
		Files.writeString(one.resolve("config/sodium-options.json"), "seat 1");
		hostFile("sodium-options.json", "host changed");
		SatelliteDirectories.prepare(host, 1);
		SatelliteDirectories.prepare(host, 2);
		assertThat(one.resolve("config/sodium-options.json")).hasContent("seat 1");
		assertThat(two.resolve("config/sodium-options.json")).hasContent("host changed");
	}
}
