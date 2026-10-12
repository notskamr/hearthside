package dev.hearthside.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

/** The host's split-screen settings file, `config/hearthside.json` (plan Task 8b). */
class SplitScreenConfigTest {
	static SplitScreenConfig read(String json) throws IOException {
		return SplitScreenConfig.read(new StringReader(json));
	}

	@Test
	void defaultsAreFifteenThirtySixAndThePreset() {
		assertThat(SplitScreenConfig.DEFAULTS.guestMemoryMb()).isEqualTo(1536);
		// Spec D: the preset decides guests' render distance by default (it was 8).
		assertThat(SplitScreenConfig.DEFAULTS.guestRenderDistance()).isEqualTo(SplitScreenConfig.RENDER_DISTANCE_FROM_PRESET);
	}

	@Test
	void aMissingFileOrFieldGivesTheDefaults() throws IOException {
		assertThat(read("")).isEqualTo(SplitScreenConfig.DEFAULTS);
		assertThat(read("{}")).isEqualTo(SplitScreenConfig.DEFAULTS);
		assertThat(read("{\"guestMemoryMb\": 2048}"))
				.isEqualTo(new SplitScreenConfig(2048, SplitScreenConfig.RENDER_DISTANCE_FROM_PRESET));
		assertThat(read("{\"guestRenderDistance\": 12}")).isEqualTo(new SplitScreenConfig(1536, 12));
	}

	@Test
	void anInvalidFieldGivesItsDefault() throws IOException {
		assertThat(read("{\"guestMemoryMb\": \"lots\", \"guestRenderDistance\": 10}")).isEqualTo(new SplitScreenConfig(1536, 10));
		assertThat(read("not json at all")).isEqualTo(SplitScreenConfig.DEFAULTS);
	}

	@Test
	void valuesAreClamped() {
		assertThat(new SplitScreenConfig(100, 8).guestMemoryMb()).isEqualTo(768);
		assertThat(new SplitScreenConfig(99999, 8).guestMemoryMb()).isEqualTo(8192);
		assertThat(new SplitScreenConfig(1536, 1).guestRenderDistance()).isEqualTo(2);
		assertThat(new SplitScreenConfig(1536, 40).guestRenderDistance()).isEqualTo(32);
	}

	@Test
	void zeroRenderDistanceMeansNoCap() {
		SplitScreenConfig noCap = new SplitScreenConfig(1536, 0);
		assertThat(noCap.guestRenderDistance()).isZero();
		assertThat(noCap.capsRenderDistance()).isFalse();
		assertThat(SplitScreenConfig.DEFAULTS.capsRenderDistance()).isTrue();
	}

	@Test
	void roundTripsThroughJson() throws IOException {
		SplitScreenConfig config = new SplitScreenConfig(1024, 6);
		StringWriter out = new StringWriter();
		config.write(out);
		assertThat(out.toString()).contains("\"guestMemoryMb\"").contains("\"guestRenderDistance\"");
		assertThat(read(out.toString())).isEqualTo(config);
	}
}
