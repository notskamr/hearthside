package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The pause menu's status line (Spec E section 3.2): who is still playing, or that the world is frozen. */
class PauseLineTest {
	static PausePolicy.Decision playing(String... names) {
		return new PausePolicy.Decision(false, List.of(names));
	}

	@Test
	void frozen() {
		assertThat(PauseLine.of(new PausePolicy.Decision(true, List.of()))).isEqualTo("Everyone's paused — the world is frozen");
	}

	@Test
	void oneStillPlaying() {
		assertThat(PauseLine.of(playing("Alex"))).isEqualTo("The world keeps going — Alex is still playing");
	}

	@Test
	void twoStillPlaying() {
		assertThat(PauseLine.of(playing("Alex", "Sam"))).isEqualTo("The world keeps going — Alex and Sam are still playing");
	}

	@Test
	void threeStillPlaying() {
		assertThat(PauseLine.of(playing("Alex", "Sam", "Kai")))
				.isEqualTo("The world keeps going — Alex, Sam and Kai are still playing");
	}

	@Test
	void notFrozenWithNobodyNamedStillSaysTheWorldKeepsGoing() {
		// e.g. a player from another machine is connected over LAN: the world never freezes.
		assertThat(PauseLine.of(playing())).isEqualTo("The world keeps going");
	}

	@Test
	void aPlayersOwnMenuNeverNamesThem() {
		// A guest's menu opens before player 1's game has heard that guest paused (final review M1).
		assertThat(PauseLine.forPlayer(playing("Alex", "Sam"), "Sam")).isEqualTo("The world keeps going — Alex is still playing");
		assertThat(PauseLine.forPlayer(playing("Sam"), "Sam")).isEqualTo("The world keeps going");
		assertThat(PauseLine.forPlayer(playing("Alex", "Sam", "Kai"), "Alex"))
				.isEqualTo("The world keeps going — Sam and Kai are still playing");
	}

	@Test
	void anotherPlayersNameAndTheFrozenLineAreKept() {
		assertThat(PauseLine.forPlayer(playing("Alex"), "Sam")).isEqualTo("The world keeps going — Alex is still playing");
		assertThat(PauseLine.forPlayer(new PausePolicy.Decision(true, List.of()), "Sam"))
				.isEqualTo("Everyone's paused — the world is frozen");
	}
}
