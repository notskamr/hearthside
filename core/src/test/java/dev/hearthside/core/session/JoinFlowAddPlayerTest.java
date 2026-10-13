package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.MutableClock;
import dev.hearthside.core.input.Control;
import dev.hearthside.core.session.JoinFlow.Effect;
import dev.hearthside.core.session.JoinFlow.Mode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Add player from player 1's pause menu (Spec E section 3.4): for 15 s any free controller's single A joins, without
 * the usual second A; then it expires. Greyed out (refused) when the party is full (plan R3).
 */
class JoinFlowAddPlayerTest {
	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-13T12:00:00Z"));

	@Test
	void addPlayerShowsThePromptForFifteenSeconds() {
		JoinFlow flow = new JoinFlow(Mode.IN_GAME, 3, clock);
		assertThat(flow.addPlayer().effects())
				.containsExactly(new Effect.ShowAddPlayerPrompt(clock.instant().plus(Duration.ofSeconds(15))));
	}

	@Test
	void aSingleAJoinsWhileTheWindowIsOpen() {
		JoinFlow flow = new JoinFlow(Mode.IN_GAME, 3, clock);
		flow.addPlayer();
		clock.advance(Duration.ofSeconds(14));
		var result = flow.press("pad-1", Control.A);
		assertThat(result.effects()).contains(new Effect.HideAddPlayerPrompt(), new Effect.ClaimSeat("pad-1", 0),
				new Effect.OpenProfilePicker("pad-1"), new Effect.StartGuestGame("pad-1", 0));
		assertThat(flow.seatOf("pad-1")).hasValue(0);
	}

	@Test
	void theWindowClosesAfterOneJoin() {
		JoinFlow flow = new JoinFlow(Mode.IN_GAME, 3, clock);
		flow.addPlayer();
		flow.press("pad-1", Control.A);
		assertThat(flow.press("pad-2", Control.A).effects()).containsExactly(new Effect.ShowJoinPrompt("pad-2"));
	}

	@Test
	void theWindowExpires() {
		JoinFlow flow = new JoinFlow(Mode.IN_GAME, 3, clock);
		flow.addPlayer();
		clock.advance(Duration.ofSeconds(15));
		assertThat(flow.tick().effects()).containsExactly(new Effect.HideAddPlayerPrompt());
		assertThat(flow.press("pad-1", Control.A).effects()).containsExactly(new Effect.ShowJoinPrompt("pad-1"));
	}

	@Test
	void cancelling() {
		JoinFlow flow = new JoinFlow(Mode.IN_GAME, 3, clock);
		flow.addPlayer();
		assertThat(flow.cancelAddPlayer().effects()).containsExactly(new Effect.HideAddPlayerPrompt());
		assertThat(flow.cancelAddPlayer().effects()).isEmpty();
		assertThat(flow.press("pad-1", Control.A).effects()).containsExactly(new Effect.ShowJoinPrompt("pad-1"));
	}

	@Test
	void addPlayerBeatsAPendingPrompt() {
		// Review Focus 5: a pad that pressed A once (double-A prompt pending) joins with its next A, prompt cleared.
		JoinFlow flow = new JoinFlow(Mode.IN_GAME, 3, clock);
		flow.press("pad-1", Control.A);
		flow.addPlayer();
		clock.advance(Duration.ofSeconds(10));
		var result = flow.press("pad-1", Control.A);
		assertThat(result.effects()).contains(new Effect.ClaimSeat("pad-1", 0), new Effect.HideAddPlayerPrompt());
		assertThat(flow.seats().get("pad-1")).isInstanceOf(SeatState.ChoosingProfile.class);
	}

	@Test
	void aFullPartyRefusesAddPlayer() {
		JoinFlow flow = new JoinFlow(Mode.IN_GAME, 1, clock);
		flow.press("pad-1", Control.A);
		flow.press("pad-1", Control.A);
		assertThat(flow.partyFull()).isTrue();
		assertThat(flow.addPlayer()).isEqualTo(JoinFlow.Result.NONE);
	}

	@Test
	void aSeatedControllersAIsNotAJoin() {
		JoinFlow flow = new JoinFlow(Mode.IN_GAME, 3, clock);
		flow.press("pad-1", Control.A);
		flow.press("pad-1", Control.A);
		flow.profileChosen("pad-1", UUID.randomUUID());
		flow.addPlayer();
		assertThat(flow.press("pad-1", Control.A).effects()).isEmpty();
	}
}
