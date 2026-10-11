package dev.hearthside.core.compositor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FrameNamesTest {
	private static final String FILE = "C:\\Users\\me\\.minecraft\\hearthside\\satellite-1\\frames.slots";

	@Test
	void nameIsLocalAndDeterministic() {
		String name = FrameNames.gpuMemory(FILE, 7, 2);
		assertThat(name).startsWith("Local\\Hearthside-").endsWith("-7-2");
		assertThat(FrameNames.gpuMemory(FILE, 7, 2)).isEqualTo(name);
	}

	@Test
	void pathCaseDoesNotMatter() {
		// Windows paths are case-insensitive; both sides must agree even if one spells the folder differently.
		assertThat(FrameNames.gpuMemory(FILE.toUpperCase(), 7, 2)).isEqualTo(FrameNames.gpuMemory(FILE, 7, 2));
	}

	@Test
	void differentGuestsGenerationsAndSlotsDiffer() {
		String other = FILE.replace("satellite-1", "satellite-2");
		assertThat(FrameNames.gpuMemory(other, 7, 2)).isNotEqualTo(FrameNames.gpuMemory(FILE, 7, 2));
		assertThat(FrameNames.gpuMemory(FILE, 8, 2)).isNotEqualTo(FrameNames.gpuMemory(FILE, 7, 2));
		assertThat(FrameNames.gpuMemory(FILE, 7, 3)).isNotEqualTo(FrameNames.gpuMemory(FILE, 7, 2));
	}

	@Test
	void nameHasNoPathCharacters() {
		// Kernel object names may not contain backslashes after the namespace prefix.
		assertThat(FrameNames.gpuMemory(FILE, 1, 0).substring("Local\\".length())).doesNotContain("\\").doesNotContain("/");
	}

	@Test
	void firstGenerationIsPositiveAndVaries() {
		int a = FrameNames.firstGeneration(), b = FrameNames.firstGeneration();
		assertThat(a).isPositive();
		assertThat(b).isPositive();
	}
}
