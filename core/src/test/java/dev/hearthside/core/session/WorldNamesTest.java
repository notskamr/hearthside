package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorldNamesTest {
	@Test
	void uniqueWorldNameCountsUp() {
		assertThat(WorldNames.unique("Co-op World", List.of())).isEqualTo("Co-op World");
		assertThat(WorldNames.unique("Co-op World", List.of("co-op world"))).isEqualTo("Co-op World 2");
		assertThat(WorldNames.unique("Co-op World", List.of("Co-op World", "Co-op World 2", "Other")))
				.isEqualTo("Co-op World 3");
	}

	@Test
	void blankNamesFallBackToTheDefault() {
		assertThat(WorldNames.unique("   ", List.of())).isEqualTo(WorldNames.DEFAULT);
	}
}
