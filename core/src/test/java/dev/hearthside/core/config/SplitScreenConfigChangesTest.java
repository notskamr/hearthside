package dev.hearthside.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import dev.hearthside.core.layout.BorderStyle;
import dev.hearthside.core.layout.LayoutSettings;
import org.junit.jupiter.api.Test;

/**
 * Player 1's Split-screen screen (Spec E section 3.5) changes one setting at a time: each copy keeps every other field,
 * and what changed is told as the file writes it ({@code Hearthside split-screen applied <key>=<value>}).
 */
class SplitScreenConfigChangesTest {
	private static final SplitScreenConfig CHANGED = SplitScreenConfig.DEFAULTS
			.withLayout(SplitScreenConfig.DEFAULTS.layout().withBorders(BorderStyle.COLOUR).withLayoutAnimation(false))
			.withTitleStyle(TitleStyleSetting.VANILLA)
			.withVulkanOffer(VulkanOffer.DECLINED);

	@Test
	void eachCopyKeepsEveryOtherField() {
		SplitScreenConfig c = CHANGED.withPreset(Preset.QUALITY).withPresetForPlayer1(true).withGuestRenderDistance(12)
				.withGuestMemoryMb(3072);
		assertThat(c.preset()).isEqualTo(Preset.QUALITY);
		assertThat(c.presetForPlayer1()).isTrue();
		assertThat(c.guestRenderDistance()).isEqualTo(12);
		assertThat(c.guestMemoryMb()).isEqualTo(3072);
		assertThat(c.layout()).isEqualTo(CHANGED.layout());
		assertThat(c.titleStyle()).isEqualTo(TitleStyleSetting.VANILLA);
		assertThat(c.vulkanOffer()).isEqualTo(VulkanOffer.DECLINED);
	}

	@Test
	void copiesClampAsTheFileDoes() {
		assertThat(CHANGED.withGuestMemoryMb(100).guestMemoryMb()).isEqualTo(SplitScreenConfig.MIN_GUEST_MEMORY_MB);
		assertThat(CHANGED.withGuestRenderDistance(-7).guestRenderDistance()).isEqualTo(SplitScreenConfig.NO_RENDER_DISTANCE_CAP);
		assertThat(CHANGED.withGuestRenderDistance(SplitScreenConfig.RENDER_DISTANCE_FROM_PRESET).guestRenderDistance())
				.isEqualTo(SplitScreenConfig.RENDER_DISTANCE_FROM_PRESET);
	}

	@Test
	void nothingChanged() {
		assertThat(CHANGED.changesFrom(CHANGED)).isEmpty();
	}

	@Test
	void changesAreTheFilesKeysAndValues() {
		SplitScreenConfig next = CHANGED
				.withLayout(CHANGED.layout().withTwo(LayoutSettings.TwoPlayer.STACKED).withBorders(BorderStyle.OFF)
						.withNameTags(false))
				.withTitleStyle(TitleStyleSetting.HEARTHSIDE)
				.withPreset(Preset.PERFORMANCE)
				.withGuestRenderDistance(SplitScreenConfig.NO_RENDER_DISTANCE_CAP);
		assertThat(next.changesFrom(CHANGED)).containsExactly(
				entry("guestRenderDistance", "0"),
				entry("preset", "performance"),
				entry("twoPlayerLayout", "stacked"),
				entry("borders", "off"),
				entry("nameTags", "false"),
				entry("titleStyle", "hearthside"));
		assertThat(CHANGED.changesFrom(next)).containsEntry("guestRenderDistance", "preset");
	}
}
