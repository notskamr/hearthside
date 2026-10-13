package dev.hearthside.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.layout.BorderStyle;
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
 * Spec E section 3.5's new settings in config/hearthside.json: borders ("colour", "thin", "off"; plan R2, default thin),
 * name tags, layout animation, and the title-screen style; older files gain them.
 */
class SplitScreenConfigMenusTest {
	@TempDir
	Path dir;

	static SplitScreenConfig read(String json) throws IOException {
		return SplitScreenConfig.read(new StringReader(json));
	}

	@Test
	void defaults() {
		LayoutSettings layout = SplitScreenConfig.DEFAULTS.layout();
		assertThat(layout.borders()).isEqualTo(BorderStyle.THIN);
		assertThat(layout.nameTags()).isTrue();
		assertThat(layout.layoutAnimation()).isTrue();
		assertThat(SplitScreenConfig.DEFAULTS.titleStyle()).isEqualTo(TitleStyleSetting.HEARTHSIDE);
	}

	@Test
	void eachValueReads() throws IOException {
		SplitScreenConfig config = read("{\"borders\": \"colour\", \"nameTags\": false, \"layoutAnimation\": false,"
				+ " \"titleStyle\": \"vanilla\"}");
		assertThat(config.layout().borders()).isEqualTo(BorderStyle.COLOUR);
		assertThat(config.layout().nameTags()).isFalse();
		assertThat(config.layout().layoutAnimation()).isFalse();
		assertThat(config.titleStyle()).isEqualTo(TitleStyleSetting.VANILLA);
		assertThat(read("{\"borders\": \"off\"}").layout().borders()).isEqualTo(BorderStyle.OFF);
	}

	@Test
	void unknownValuesAreTheDefaults() throws IOException {
		SplitScreenConfig config = read("{\"borders\": \"thick\", \"titleStyle\": \"neon\", \"nameTags\": \"yes\"}");
		assertThat(config.layout().borders()).isEqualTo(BorderStyle.THIN);
		assertThat(config.titleStyle()).isEqualTo(TitleStyleSetting.HEARTHSIDE);
		assertThat(config.layout().nameTags()).isTrue();
	}

	@Test
	void writeRoundTrips() throws IOException {
		SplitScreenConfig config = SplitScreenConfig.DEFAULTS
				.withLayout(SplitScreenConfig.DEFAULTS.layout().withBorders(BorderStyle.OFF).withNameTags(false)
						.withLayoutAnimation(false))
				.withTitleStyle(TitleStyleSetting.VANILLA);
		StringWriter out = new StringWriter();
		config.write(out);
		assertThat(out.toString()).containsPattern("\"borders\"\\s*:\\s*\"off\"").containsPattern("\"titleStyle\"\\s*:\\s*\"vanilla\"");
		assertThat(read(out.toString())).isEqualTo(config);
	}

	@Test
	void anOlderFileGainsTheNewFields() throws IOException {
		Path file = dir.resolve("hearthside.json");
		// Everything Spec D's build wrote, and nothing newer.
		Files.writeString(file, "{\"guestMemoryMb\": 1536, \"guestRenderDistance\": \"preset\", \"preset\": \"auto\","
				+ " \"presetForPlayer1\": false, \"twoPlayerLayout\": \"stacked\", \"threePlayerLayout\": \"grid_with_panel\","
				+ " \"partyPanel\": true, \"vulkanOffer\": \"declined\"}", StandardCharsets.UTF_8);
		assertThat(SplitScreenConfig.lacksFields(new StringReader(Files.readString(file)))).isTrue();
		SplitScreenConfig loaded = SplitScreenConfig.loadOrCreate(file);
		assertThat(loaded.layout().two()).isEqualTo(LayoutSettings.TwoPlayer.STACKED);
		assertThat(loaded.vulkanOffer()).isEqualTo(VulkanOffer.DECLINED);
		assertThat(loaded.layout().borders()).isEqualTo(BorderStyle.THIN);
		String rewritten = Files.readString(file);
		assertThat(rewritten).contains("\"borders\"", "\"nameTags\"", "\"layoutAnimation\"", "\"titleStyle\"");
		assertThat(SplitScreenConfig.lacksFields(new StringReader(rewritten))).isFalse();
	}
}
