package dev.hearthside.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.layout.LayoutSettings;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Spec D's settings in config/hearthside.json: the preset, whether player 1 takes it too, the Vulkan offer's answer, and
 * "preset" as a guest render distance (the new default). Plan R6: a file from an earlier build, which wrote the old
 * default 8 and no preset, follows the preset.
 */
class SplitScreenConfigPresetTest {
	@TempDir
	Path dir;

	static SplitScreenConfig read(String json) throws IOException {
		return SplitScreenConfig.read(new StringReader(json));
	}

	static String written(SplitScreenConfig config) throws IOException {
		StringWriter out = new StringWriter();
		config.write(out);
		return out.toString();
	}

	@Test
	void presetReadsEachName() throws IOException {
		assertThat(read("{\"preset\": \"auto\"}").preset()).isEqualTo(Preset.AUTO);
		assertThat(read("{\"preset\": \"quality\"}").preset()).isEqualTo(Preset.QUALITY);
		assertThat(read("{\"preset\": \"balanced\"}").preset()).isEqualTo(Preset.BALANCED);
		assertThat(read("{\"preset\": \"performance\"}").preset()).isEqualTo(Preset.PERFORMANCE);
		assertThat(read("{\"preset\": \"Performance\"}").preset()).isEqualTo(Preset.PERFORMANCE);
	}

	@Test
	void anUnknownPresetIsAuto() throws IOException {
		assertThat(read("{\"preset\": \"turbo\"}").preset()).isEqualTo(Preset.AUTO);
		assertThat(read("{\"preset\": 3}").preset()).isEqualTo(Preset.AUTO);
	}

	@Test
	void aMissingPresetIsAuto() throws IOException {
		assertThat(read("{}").preset()).isEqualTo(Preset.AUTO);
		assertThat(SplitScreenConfig.DEFAULTS.preset()).isEqualTo(Preset.AUTO);
	}

	@Test
	void presetForPlayer1DefaultsToFalse() throws IOException {
		assertThat(read("{}").presetForPlayer1()).isFalse();
		assertThat(read("{\"presetForPlayer1\": true}").presetForPlayer1()).isTrue();
	}

	@Test
	void vulkanOfferRoundTrips() throws IOException {
		assertThat(read("{}").vulkanOffer()).isEqualTo(VulkanOffer.PENDING);
		assertThat(read("{\"vulkanOffer\": \"maybe\"}").vulkanOffer()).isEqualTo(VulkanOffer.PENDING);
		for (VulkanOffer offer : VulkanOffer.values()) {
			SplitScreenConfig config = SplitScreenConfig.DEFAULTS.withVulkanOffer(offer);
			assertThat(read(written(config)).vulkanOffer()).isEqualTo(offer);
		}
	}

	@Test
	void withVulkanOfferKeepsEverythingElse() {
		SplitScreenConfig config = new SplitScreenConfig(2048, 12, LayoutSettings.DEFAULTS.withPartyPanel(false),
				Preset.BALANCED, true, VulkanOffer.PENDING);
		assertThat(config.withVulkanOffer(VulkanOffer.DECLINED))
				.isEqualTo(new SplitScreenConfig(2048, 12, LayoutSettings.DEFAULTS.withPartyPanel(false), Preset.BALANCED, true,
						VulkanOffer.DECLINED));
	}

	@Test
	void guestRenderDistanceAcceptsPreset() throws IOException {
		assertThat(read("{\"preset\": \"auto\", \"guestRenderDistance\": \"preset\"}").guestRenderDistance())
				.isEqualTo(SplitScreenConfig.RENDER_DISTANCE_FROM_PRESET);
		assertThat(read("{\"preset\": \"auto\", \"guestRenderDistance\": \"PRESET\"}").guestRenderDistance())
				.isEqualTo(SplitScreenConfig.RENDER_DISTANCE_FROM_PRESET);
		assertThat(read("{\"preset\": \"auto\", \"guestRenderDistance\": 0}").guestRenderDistance()).isZero();
		assertThat(read("{\"preset\": \"auto\", \"guestRenderDistance\": 12}").guestRenderDistance()).isEqualTo(12);
	}

	@Test
	void presetIsTheNewDefaultDistance() throws IOException {
		assertThat(SplitScreenConfig.DEFAULTS.guestRenderDistance()).isEqualTo(SplitScreenConfig.RENDER_DISTANCE_FROM_PRESET);
		assertThat(read("{}").guestRenderDistance()).isEqualTo(SplitScreenConfig.RENDER_DISTANCE_FROM_PRESET);
		assertThat(read("{\"preset\": \"auto\", \"guestRenderDistance\": \"far\"}").guestRenderDistance())
				.isEqualTo(SplitScreenConfig.RENDER_DISTANCE_FROM_PRESET);
	}

	@Test
	void anOlderFileWithTheOldDefaultFollowsThePreset() throws IOException {
		assertThat(read("{\"guestMemoryMb\": 1536, \"guestRenderDistance\": 8}").guestRenderDistance())
				.isEqualTo(SplitScreenConfig.RENDER_DISTANCE_FROM_PRESET);
	}

	@Test
	void aNewFileKeepsAnExplicitEight() throws IOException {
		assertThat(read("{\"preset\": \"auto\", \"guestRenderDistance\": 8}").guestRenderDistance()).isEqualTo(8);
	}

	@Test
	void anOlderFileWithAnotherDistanceKeepsIt() throws IOException {
		assertThat(read("{\"guestRenderDistance\": 6}").guestRenderDistance()).isEqualTo(6);
		assertThat(read("{\"guestRenderDistance\": 0}").guestRenderDistance()).isZero();
	}

	@Test
	void anOlderFileGainsTheNewFields() throws IOException {
		Path file = dir.resolve("hearthside.json");
		Files.writeString(file, "{\"guestMemoryMb\": 2048, \"guestRenderDistance\": 8, \"twoPlayerLayout\": \"stacked\","
				+ " \"threePlayerLayout\": \"wide_top\", \"partyPanel\": false}", StandardCharsets.UTF_8);
		assertThat(SplitScreenConfig.lacksFields(new StringReader(Files.readString(file)))).isTrue();

		SplitScreenConfig config = SplitScreenConfig.loadOrCreate(file);
		assertThat(config.guestMemoryMb()).isEqualTo(2048);
		assertThat(config.guestRenderDistance()).isEqualTo(SplitScreenConfig.RENDER_DISTANCE_FROM_PRESET);
		assertThat(config.layout().two()).isEqualTo(LayoutSettings.TwoPlayer.STACKED);

		String rewritten = Files.readString(file);
		assertThat(rewritten).contains("\"preset\"", "\"presetForPlayer1\"", "\"vulkanOffer\"");
		assertThat(rewritten).containsPattern("\"guestRenderDistance\"\\s*:\\s*\"preset\"");
		assertThat(SplitScreenConfig.lacksFields(new StringReader(rewritten))).isFalse();
		assertThat(SplitScreenConfig.loadOrCreate(file)).isEqualTo(config);
	}

	@Test
	void writeEmitsPresetAsAString() throws IOException {
		String json = written(SplitScreenConfig.DEFAULTS);
		assertThat(json).containsPattern("\"guestRenderDistance\"\\s*:\\s*\"preset\"");
		assertThat(json).containsPattern("\"preset\"\\s*:\\s*\"auto\"");
		assertThat(json).containsPattern("\"presetForPlayer1\"\\s*:\\s*false");
		assertThat(json).containsPattern("\"vulkanOffer\"\\s*:\\s*\"pending\"");
		assertThat(read(json)).isEqualTo(SplitScreenConfig.DEFAULTS);
	}
}
