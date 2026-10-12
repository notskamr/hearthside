package dev.hearthside.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.layout.LayoutSettings;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A config/hearthside.json from before a setting existed gains it, so players can find every setting in the file
 * (orchestrator follow-up, 2026-10-11): the values it has are kept, invalid ones become their defaults.
 */
class SplitScreenConfigFillTest {
	@TempDir
	Path dir;

	static boolean lacksFields(String json) throws IOException {
		return SplitScreenConfig.lacksFields(new StringReader(json));
	}

	@Test
	void anOlderFileLacksTheLayoutFields() throws IOException {
		assertThat(lacksFields("{\"guestMemoryMb\": 2048, \"guestRenderDistance\": 6}")).isTrue();
		assertThat(lacksFields("{}")).isTrue();
	}

	@Test
	void writesOutputHasEveryField() throws IOException {
		StringWriter out = new StringWriter();
		SplitScreenConfig.DEFAULTS.write(out);
		assertThat(lacksFields(out.toString())).isFalse();
	}

	@Test
	void contentThatIsntAnObjectIsLeftAlone() throws IOException {
		assertThat(lacksFields("not json at all")).isFalse();
		assertThat(lacksFields("[1, 2]")).isFalse();
		assertThat(lacksFields("")).isFalse();
	}

	@Test
	void loadingAnOlderFileAddsTheMissingFieldsAndKeepsItsValues() throws IOException {
		Path file = dir.resolve("config").resolve("hearthside.json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "{\"guestMemoryMb\": 2048, \"guestRenderDistance\": \"far\", \"twoPlayerLayout\": \"stacked\"}");
		SplitScreenConfig config = SplitScreenConfig.loadOrCreate(file);
		assertThat(config).isEqualTo(new SplitScreenConfig(2048, 8, LayoutSettings.DEFAULTS.withTwo(LayoutSettings.TwoPlayer.STACKED)));
		String rewritten = Files.readString(file);
		assertThat(rewritten).contains("\"guestMemoryMb\": 2048").contains("\"guestRenderDistance\": 8")
				.contains("\"twoPlayerLayout\": \"stacked\"").contains("\"threePlayerLayout\": \"grid_with_panel\"")
				.contains("\"partyPanel\": true");
		assertThat(SplitScreenConfig.loadOrCreate(file)).isEqualTo(config);
	}

	@Test
	void aMissingFileIsCreatedWithEveryField() throws IOException {
		Path file = dir.resolve("config").resolve("hearthside.json");
		assertThat(SplitScreenConfig.loadOrCreate(file)).isEqualTo(SplitScreenConfig.DEFAULTS);
		assertThat(lacksFields(Files.readString(file))).isFalse();
	}

	@Test
	void aCompleteFileIsNotRewritten() throws IOException {
		Path file = dir.resolve("hearthside.json");
		String mine = "{\"partyPanel\": false, \"threePlayerLayout\": \"wide_top\", \"twoPlayerLayout\": \"side_by_side\","
				+ " \"guestRenderDistance\": 12, \"guestMemoryMb\": 1024}";
		Files.writeString(file, mine);
		SplitScreenConfig.loadOrCreate(file);
		assertThat(file).hasContent(mine);
	}

	@Test
	void anUnreadableFileIsLeftAsItIs() throws IOException {
		Path file = dir.resolve("hearthside.json");
		Files.writeString(file, "{ oops");
		assertThat(SplitScreenConfig.loadOrCreate(file)).isEqualTo(SplitScreenConfig.DEFAULTS);
		assertThat(file).hasContent("{ oops");
	}
}
