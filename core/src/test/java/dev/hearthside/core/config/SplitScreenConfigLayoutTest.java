package dev.hearthside.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.layout.LayoutSettings;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

/** Side by side or stacked, the three-player arrangement and the party panel, in config/hearthside.json. */
class SplitScreenConfigLayoutTest {
	static SplitScreenConfig read(String json) throws IOException {
		return SplitScreenConfig.read(new StringReader(json));
	}

	@Test
	void theDefaultLayoutIsSpecAs() {
		assertThat(SplitScreenConfig.DEFAULTS.layout()).isEqualTo(LayoutSettings.DEFAULTS);
		assertThat(new SplitScreenConfig(1536, 8).layout()).isEqualTo(LayoutSettings.DEFAULTS);
	}

	@Test
	void readsTheTwoPlayerLayout() throws IOException {
		assertThat(read("{\"twoPlayerLayout\": \"stacked\"}").layout().two()).isEqualTo(LayoutSettings.TwoPlayer.STACKED);
		assertThat(read("{\"twoPlayerLayout\": \"side_by_side\"}").layout().two()).isEqualTo(LayoutSettings.TwoPlayer.SIDE_BY_SIDE);
	}

	@Test
	void readsTheThreePlayerLayoutAndThePanel() throws IOException {
		SplitScreenConfig config = read("{\"threePlayerLayout\": \"wide_top\", \"partyPanel\": false}");
		assertThat(config.layout().three()).isEqualTo(LayoutSettings.ThreePlayer.WIDE_TOP);
		assertThat(config.layout().partyPanel()).isFalse();
		assertThat(read("{\"threePlayerLayout\": \"tall_left\"}").layout().three()).isEqualTo(LayoutSettings.ThreePlayer.TALL_LEFT);
	}

	@Test
	void anUnknownLayoutGivesTheDefault() throws IOException {
		SplitScreenConfig config = read("{\"twoPlayerLayout\": \"diagonal\", \"threePlayerLayout\": 3, \"partyPanel\": \"yes\"}");
		assertThat(config.layout()).isEqualTo(LayoutSettings.DEFAULTS);
	}

	@Test
	void theLayoutRoundTripsWithTheRest() throws IOException {
		SplitScreenConfig config = new SplitScreenConfig(1024, 6).withLayout(
				LayoutSettings.DEFAULTS.withTwo(LayoutSettings.TwoPlayer.STACKED).withThree(LayoutSettings.ThreePlayer.WIDE_TOP));
		StringWriter out = new StringWriter();
		config.write(out);
		assertThat(out.toString()).contains("\"twoPlayerLayout\": \"stacked\"").contains("\"threePlayerLayout\": \"wide_top\"")
				.contains("\"partyPanel\": true");
		assertThat(read(out.toString())).isEqualTo(config);
	}
}
