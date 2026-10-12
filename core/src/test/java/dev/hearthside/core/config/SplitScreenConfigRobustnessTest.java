package dev.hearthside.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** config/hearthside.json never costs a player their settings (batch review M1, M6). */
class SplitScreenConfigRobustnessTest {
	@TempDir
	Path dir;

	@Test
	void zeroOrNegativeRenderDistanceMeansNoCap() throws IOException {
		assertThat(SplitScreenConfig.read(new StringReader("{\"guestRenderDistance\": -1}")).capsRenderDistance()).isFalse();
		assertThat(new SplitScreenConfig(1536, -5).guestRenderDistance()).isZero();
		assertThat(new SplitScreenConfig(1536, 0).capsRenderDistance()).isFalse();
		assertThat(new SplitScreenConfig(1536, 1).guestRenderDistance()).isEqualTo(2);
	}

	@Test
	void anIncompleteFileThatCantBeRewrittenStillGivesItsValues() throws IOException {
		Path config = dir.resolve("config");
		Files.createDirectories(config);
		Path file = config.resolve("hearthside.json");
		Files.writeString(file, "{\"guestMemoryMb\": 2048}");
		// The rewrite's temporary file can't be created: a directory sits where it would go.
		Files.createDirectories(config.resolve("hearthside.json.tmp"));
		SplitScreenConfig loaded = SplitScreenConfig.loadOrCreate(file);
		assertThat(loaded.guestMemoryMb()).isEqualTo(2048);
		assertThat(file).hasContent("{\"guestMemoryMb\": 2048}");
	}
}
