package dev.hearthside.core.compositor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The name of a guest's copied-frame memory (Spec D section 3.2): a page-file-backed Windows section both games derive
 * from the slot table's path in {@code FrameSlotsReady}, so nothing else has to travel and nothing lands on disk.
 */
class FrameNamesPixelsTest {
	static final String CONTROL = "C:\\Users\\Jo Smith\\AppData\\Roaming\\.minecraft\\hearthside\\satellite-1\\frames-7.slots";

	@Test
	void pixelsIsStableForTheSameControlFile() {
		assertThat(FrameNames.pixels(CONTROL)).isEqualTo(FrameNames.pixels(CONTROL));
	}

	@Test
	void pixelsDiffersBetweenGenerationsAndGameFolders() {
		String nextGeneration = CONTROL.replace("frames-7", "frames-8");
		String otherGame = "C:\\Games\\Prism\\instances\\co-op\\.minecraft\\hearthside\\satellite-1\\frames-7.slots";
		String otherSeat = CONTROL.replace("satellite-1", "satellite-2");
		assertThat(java.util.Set.of(FrameNames.pixels(CONTROL), FrameNames.pixels(nextGeneration), FrameNames.pixels(otherGame),
				FrameNames.pixels(otherSeat))).hasSize(4);
	}

	@Test
	void pixelsIsALocalNameWithoutFurtherBackslashes() {
		String name = FrameNames.pixels(CONTROL);
		assertThat(name).startsWith("Local\\").endsWith("-pixels");
		assertThat(name.chars().filter(c -> c == '\\').count()).isEqualTo(1);
		assertThat(name.length()).isLessThanOrEqualTo(80);
	}

	@Test
	void pixelsNeverCollidesWithSlotMemory() {
		for (int slot = 0; slot < 6; slot++) {
			assertThat(FrameNames.pixels(CONTROL)).isNotEqualTo(FrameNames.gpuMemory(CONTROL, 7, slot));
		}
	}

	@Test
	void theCaseOfThePathDoesNotMatter() {
		// Windows paths are case-insensitive, as gpuMemory's names already treat them.
		assertThat(FrameNames.pixels(CONTROL.toUpperCase(java.util.Locale.ROOT))).isEqualTo(FrameNames.pixels(CONTROL));
	}
}
