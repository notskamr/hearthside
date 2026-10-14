package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.session.PauseInputs.Seat;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Which players count toward freezing the world (Spec E section 3.2, plan R5): only players in the world; a disconnected
 * controller counts as paused; never while the world is open to LAN.
 */
class PauseInputsTest {
	static Seat seat(int index, String name, boolean inWorld, boolean paused, boolean disconnected) {
		return new Seat(index, name, inWorld, paused, disconnected);
	}

	@Test
	void everyonePausedFreezes() {
		var d = PauseInputs.decide(List.of(seat(0, "Player1", true, true, false), seat(1, "Alex", true, true, false)), false);
		assertThat(d.freeze()).isTrue();
		assertThat(d.stillPlaying()).isEmpty();
	}

	@Test
	void anyonePlayingKeepsTheWorldGoing() {
		var d = PauseInputs.decide(List.of(seat(0, "Player1", true, true, false), seat(1, "Alex", true, false, false)), false);
		assertThat(d.freeze()).isFalse();
		assertThat(d.stillPlaying()).containsExactly("Alex");
	}

	@Test
	void aDisconnectedControllerCountsAsPaused() {
		var d = PauseInputs.decide(List.of(seat(0, "Player1", true, true, false), seat(1, "Alex", true, false, true)), false);
		assertThat(d.freeze()).isTrue();
	}

	@Test
	void notInTheWorldDoesNotCount() {
		// Review Focus 2: a newcomer still loading or choosing a profile neither blocks the freeze nor counts.
		var d = PauseInputs.decide(List.of(seat(0, "Player1", true, true, false), seat(1, "Alex", true, true, false),
				seat(2, "Sam", false, false, false)), false);
		assertThat(d.freeze()).isTrue();
		assertThat(d.stillPlaying()).doesNotContain("Sam");
	}

	@Test
	void neverWhileOpenToLan() {
		var d = PauseInputs.decide(List.of(seat(0, "Player1", true, true, false), seat(1, "Alex", true, true, false)), true);
		assertThat(d.freeze()).isFalse();
	}

	@Test
	void playerOneAloneInTheWorldIsNotSplitScreen() {
		// With every guest still loading, only player 1 is in the world: vanilla's rule applies (their pause freezes).
		var d = PauseInputs.decide(List.of(seat(0, "Player1", true, true, false), seat(1, "Alex", false, false, false)), false);
		assertThat(d.freeze()).isTrue();
	}
}
