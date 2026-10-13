package dev.hearthside.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.config.PresetChooser.GpuKind;
import dev.hearthside.core.config.PresetChooser.Machine;
import org.junit.jupiter.api.Test;

/** Auto's choice of preset from the machine (Spec D section 3.3; plan R7: "less than 12 GB" is under 11.5 GiB). */
class PresetChooserTest {
	static final long GIB = 1L << 30;

	static PresetChooser.Choice choose(GpuKind gpu, double gib, int players, int width, int height) {
		return PresetChooser.choose(new Machine(gpu, (long) (gib * GIB), players, width, height));
	}

	@Test
	void anIntegratedGpuIsPerformance() {
		var choice = choose(GpuKind.INTEGRATED, 32, 2, 1920, 1080);
		assertThat(choice.preset()).isEqualTo(Preset.PERFORMANCE);
		assertThat(choice.reasons()).containsExactly("integrated GPU");
	}

	@Test
	void anUnknownGpuIsPerformance() {
		var choice = choose(GpuKind.UNKNOWN, 32, 2, 1920, 1080);
		assertThat(choice.preset()).isEqualTo(Preset.PERFORMANCE);
		assertThat(choice.reasons()).containsExactly("unknown GPU");
	}

	@Test
	void littleMemoryIsPerformance() {
		var choice = choose(GpuKind.DISCRETE, 8, 2, 1920, 1080);
		assertThat(choice.preset()).isEqualTo(Preset.PERFORMANCE);
		assertThat(choice.reasons()).containsExactly("8 GB RAM");
	}

	@Test
	void everyReasonIsListed() {
		var choice = choose(GpuKind.INTEGRATED, 8, 4, 3840, 2160);
		assertThat(choice.preset()).isEqualTo(Preset.PERFORMANCE);
		assertThat(choice.reasons()).containsExactly("integrated GPU", "8 GB RAM");
	}

	@Test
	void twelveGigabyteMachinesAreNotLittleMemory() {
		// Windows reports a 12 GB machine as about 11.8 GiB.
		var choice = choose(GpuKind.DISCRETE, 11.8, 2, 1920, 1080);
		assertThat(choice.preset()).isEqualTo(Preset.QUALITY);
		assertThat(choice.reasons()).containsExactly("discrete GPU", "12 GB RAM");
		assertThat(choose(GpuKind.DISCRETE, 11.4, 2, 1920, 1080).preset()).isEqualTo(Preset.PERFORMANCE);
	}

	@Test
	void threePlayersOnADiscreteGpuIsBalanced() {
		var choice = choose(GpuKind.DISCRETE, 32, 3, 1920, 1080);
		assertThat(choice.preset()).isEqualTo(Preset.BALANCED);
		assertThat(choice.reasons()).containsExactly("3 players");
		assertThat(choose(GpuKind.DISCRETE, 32, 4, 1920, 1080).reasons()).containsExactly("4 players");
	}

	@Test
	void aBigWindowIsBalanced() {
		var choice = choose(GpuKind.DISCRETE, 32, 2, 3840, 2160);
		assertThat(choice.preset()).isEqualTo(Preset.BALANCED);
		assertThat(choice.reasons()).containsExactly("window 3840x2160");
		assertThat(choose(GpuKind.DISCRETE, 32, 3, 3840, 2160).reasons()).containsExactly("3 players", "window 3840x2160");
	}

	@Test
	void exactly1440pIsNotBig() {
		assertThat(choose(GpuKind.DISCRETE, 32, 2, 2560, 1440).preset()).isEqualTo(Preset.QUALITY);
	}

	@Test
	void otherwiseQuality() {
		var choice = choose(GpuKind.DISCRETE, 32, 2, 1920, 1080);
		assertThat(choice.preset()).isEqualTo(Preset.QUALITY);
		assertThat(choice.reasons()).containsExactly("discrete GPU", "32 GB RAM");
	}
}
