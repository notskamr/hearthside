package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoopWorldsTest {
	@TempDir
	Path dir;

	CoopWorldsStore store() {
		return new CoopWorldsStore(dir.resolve("coop-worlds.json"));
	}

	@Test
	void aSessionCollectsEveryoneWhoPlayed() throws Exception {
		// Ember left before Birch arrived: both still played this session.
		var store = store();
		store.played("farm", 1000, List.of("Ember"), 900);
		store.played("farm", 2000, List.of("Birch"), 900);
		assertThat(store.load()).containsExactly(new CoopWorld("farm", 2000, List.of("Ember", "Birch")));
	}

	@Test
	void playedKeepsTheLatestSessionsNames() throws Exception {
		// "Last played with" means the last session, not everyone who ever played.
		var store = store();
		store.played("farm", 1000, List.of("Ember", "Birch"), 900);
		store.played("farm", 5000, List.of("Moss"), 4000);
		assertThat(store.load().getFirst().players()).containsExactly("Moss");
	}

	@Test
	void playedKeepsAtMostEightNames() throws Exception {
		var store = store();
		store.played("farm", 1, List.of("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9"));
		assertThat(store.load().getFirst().players()).hasSize(8).startsWith("a1");
	}

	@Test
	void loadIsNewestFirst() throws Exception {
		var store = store();
		store.played("old", 1000, List.of("Ember"));
		store.played("new", 3000, List.of("Birch"));
		store.played("mid", 2000, List.of("Moss"));
		assertThat(store.load()).extracting(CoopWorld::levelId).containsExactly("new", "mid", "old");
	}

	@Test
	void orderPutsTheLastCoopWorldFirst() {
		var coop = List.of(new CoopWorld("farm", 3000, List.of("Ember")), new CoopWorld("castle", 1000, List.of()));
		assertThat(CoopWorlds.order(List.of("solo", "castle", "farm", "test"), coop))
				.containsExactly("farm", "solo", "castle", "test");
	}

	@Test
	void missingWorldsAreSkipped() {
		// A co-op world deleted outside the game is not offered.
		var coop = List.of(new CoopWorld("gone", 9000, List.of("Ember")), new CoopWorld("farm", 3000, List.of()));
		assertThat(CoopWorlds.order(List.of("solo", "farm"), coop)).containsExactly("farm", "solo");
		assertThat(CoopWorlds.order(List.of("solo"), coop)).containsExactly("solo");
		assertThat(CoopWorlds.order(List.of(), coop)).isEmpty();
	}

	@Test
	void playedWithReadsNaturally() {
		assertThat(CoopWorlds.playedWith(new CoopWorld("w", 1, List.of("Host", "Ember")), "host"))
				.isEqualTo("Last played with Ember");
		assertThat(CoopWorlds.playedWith(new CoopWorld("w", 1, List.of("Ember", "Birch")), "Host"))
				.isEqualTo("Last played with Ember and Birch");
		assertThat(CoopWorlds.playedWith(new CoopWorld("w", 1, List.of("Ember", "Birch", "Moss")), "Host"))
				.isEqualTo("Last played with Ember, Birch and Moss");
		assertThat(CoopWorlds.playedWith(new CoopWorld("w", 1, List.of("Host")), "Host")).isEmpty();
	}

	@Test
	void corruptFileIsMovedAsideAndEmpty() throws Exception {
		Path file = dir.resolve("coop-worlds.json");
		Files.writeString(file, "{not json");
		assertThat(store().load()).isEmpty();
		assertThat(Files.exists(file)).isFalse();
		try (var files = Files.list(dir)) {
			assertThat(files.map(p -> p.getFileName().toString())).anyMatch(n -> n.startsWith("coop-worlds.json.corrupt-"));
		}
		// Recording still works afterwards.
		store().played("farm", 1, List.of("Ember"));
		assertThat(store().load()).hasSize(1);
	}

	@Test
	void unknownFieldsAreIgnored() throws Exception {
		Files.writeString(dir.resolve("coop-worlds.json"), """
				{"schemaVersion":1,"future":true,"worlds":[{"levelId":"farm","lastPlayed":5,"players":["Ember"],"x":1}]}
				""");
		assertThat(store().load()).containsExactly(new CoopWorld("farm", 5, List.of("Ember")));
	}
}
