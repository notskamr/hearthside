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
 * Taking over a disconnected player (Spec E section 3.3): after the take-over delay, the next free controller's single
 * A takes the player's seat, state and all; before it, the same A is an ordinary join.
 */
class JoinFlowTakeoverTest {
	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-13T12:00:00Z"));
	private final UUID alex = UUID.randomUUID();

	/** An in-game flow with {@code controller} playing in the lowest free seat. */
	JoinFlow playing(JoinFlow flow, String controller, UUID profile) {
		flow.press(controller, Control.A);
		flow.press(controller, Control.A);
		flow.profileChosen(controller, profile);
		flow.loaded(controller);
		return flow;
	}

	@Test
	void afterTheDelayAFreeControllersSingleATakesOver() {
		JoinFlow flow = playing(new JoinFlow(Mode.IN_GAME, 3, clock), "pad-1", alex);
		flow.disconnected("pad-1");
		clock.advance(Duration.ofSeconds(60));
		assertThat(flow.tick().effects()).containsExactly(new Effect.OfferTakeover("pad-1"));

		var result = flow.press("pad-2", Control.A);
		assertThat(result.effects()).contains(new Effect.TookOver("pad-1", "pad-2"));
		assertThat(flow.seatOf("pad-2")).hasValue(0);
		assertThat(flow.seatOf("pad-1")).isEmpty();
		assertThat(flow.seats().get("pad-2")).isEqualTo(new SeatState.Playing(alex));
	}

	@Test
	void beforeTheDelayTheSameAIsAnOrdinaryJoin() {
		JoinFlow flow = playing(new JoinFlow(Mode.IN_GAME, 3, clock), "pad-1", alex);
		flow.disconnected("pad-1");
		clock.advance(Duration.ofSeconds(59));
		flow.tick();
		assertThat(flow.press("pad-2", Control.A).effects()).containsExactly(new Effect.ShowJoinPrompt("pad-2"));
		assertThat(flow.seatOf("pad-1")).hasValue(0);
	}

	@Test
	void onlyTheFirstFreeControllerTakesOver() {
		// Review Focus 3: two pads pressing A on one offer — one takes over, the other gets the normal join prompt.
		JoinFlow flow = playing(new JoinFlow(Mode.IN_GAME, 3, clock), "pad-1", alex);
		flow.disconnected("pad-1");
		clock.advance(Duration.ofSeconds(60));
		flow.tick();
		assertThat(flow.press("pad-2", Control.A).effects()).contains(new Effect.TookOver("pad-1", "pad-2"));
		assertThat(flow.press("pad-3", Control.A).effects()).containsExactly(new Effect.ShowJoinPrompt("pad-3"));
		assertThat(flow.seatOf("pad-3")).isEmpty();
	}

	@Test
	void theLowestOfferedSeatIsTakenFirst() {
		JoinFlow flow = new JoinFlow(Mode.IN_GAME, 3, clock);
		playing(flow, "pad-1", alex);
		playing(flow, "pad-2", UUID.randomUUID());
		flow.disconnected("pad-2");
		flow.disconnected("pad-1");
		clock.advance(Duration.ofSeconds(60));
		flow.tick();
		assertThat(flow.press("pad-9", Control.A).effects()).contains(new Effect.TookOver("pad-1", "pad-9"));
		assertThat(flow.seatOf("pad-9")).hasValue(0);
	}

	@Test
	void theNextTakeOverIsTheSeatAFreeAWouldTake() {
		// Review Minor 1: only that seat's card offers it.
		JoinFlow flow = new JoinFlow(Mode.IN_GAME, 3, clock);
		playing(flow, "pad-1", alex);
		playing(flow, "pad-2", UUID.randomUUID());
		assertThat(flow.nextTakeover()).isEmpty();
		flow.disconnected("pad-2");
		flow.disconnected("pad-1");
		assertThat(flow.nextTakeover()).as("not offered yet").isEmpty();
		clock.advance(Duration.ofSeconds(60));
		flow.tick();
		assertThat(flow.nextTakeover()).hasValue("pad-1");
		flow.press("pad-9", Control.A);
		assertThat(flow.nextTakeover()).hasValue("pad-2");
		flow.reconnected("pad-2");
		assertThat(flow.nextTakeover()).isEmpty();
	}

	@Test
	void nothingIsNextInTheLobby() {
		JoinFlow flow = new JoinFlow(Mode.LOBBY, 3, clock);
		flow.press("pad-1", Control.A);
		flow.disconnected("pad-1");
		clock.advance(Duration.ofSeconds(60));
		flow.tick();
		assertThat(flow.nextTakeover()).isEmpty();
	}

	@Test
	void theDelayIsConfigurable() {
		JoinFlow flow = playing(new JoinFlow(Mode.IN_GAME, 3, clock, Duration.ofSeconds(2)), "pad-1", alex);
		flow.disconnected("pad-1");
		clock.advance(Duration.ofSeconds(2));
		assertThat(flow.tick().effects()).containsExactly(new Effect.OfferTakeover("pad-1"));
	}

	@Test
	void theOldControllerReturningAfterATakeOverIsNobody() {
		JoinFlow flow = playing(new JoinFlow(Mode.IN_GAME, 3, clock), "pad-1", alex);
		flow.disconnected("pad-1");
		clock.advance(Duration.ofSeconds(60));
		flow.tick();
		flow.press("pad-2", Control.A);
		assertThat(flow.reconnected("pad-1").effects()).isEmpty();
		assertThat(flow.seatOf("pad-1")).isEmpty();
		assertThat(flow.seatOf("pad-2")).hasValue(0);
	}

	@Test
	void reconnectingBeforeATakeOverKeepsTheSeat() {
		JoinFlow flow = playing(new JoinFlow(Mode.IN_GAME, 3, clock), "pad-1", alex);
		flow.disconnected("pad-1");
		clock.advance(Duration.ofSeconds(60));
		flow.tick();
		flow.reconnected("pad-1");
		assertThat(flow.press("pad-2", Control.A).effects()).containsExactly(new Effect.ShowJoinPrompt("pad-2"));
		assertThat(flow.seats().get("pad-1")).isEqualTo(new SeatState.Playing(alex));
	}

	@Test
	void inTheLobbyAFreeControllersAStillClaimsAFreeSeat() {
		// Ruling P-10: taking over is for a player in the world; in the lobby nothing is offered, and A takes a seat.
		JoinFlow flow = new JoinFlow(Mode.LOBBY, 3, clock);
		flow.press("pad-1", Control.A);
		flow.profileChosen("pad-1", alex);
		flow.disconnected("pad-1");
		clock.advance(Duration.ofSeconds(60));
		assertThat(flow.tick().effects()).as("nothing offered in the lobby").isEmpty();

		var result = flow.press("pad-2", Control.A);
		assertThat(result.effects()).contains(new Effect.ClaimSeat("pad-2", 1))
				.noneMatch(effect -> effect instanceof Effect.TookOver);
		assertThat(flow.seatOf("pad-1")).hasValue(0);
		assertThat(flow.seats().get("pad-1")).isInstanceOf(SeatState.Disconnected.class);
	}

	@Test
	void aSeatDroppedInTheLobbyIsOfferedOnceInTheWorld() {
		JoinFlow flow = new JoinFlow(Mode.LOBBY, 3, clock);
		flow.press("pad-1", Control.A);
		flow.profileChosen("pad-1", alex);
		flow.disconnected("pad-1");
		clock.advance(Duration.ofSeconds(60));
		flow.tick();
		flow.enterGame();
		assertThat(flow.tick().effects()).containsExactly(new Effect.OfferTakeover("pad-1"));
		assertThat(flow.press("pad-2", Control.A).effects()).contains(new Effect.TookOver("pad-1", "pad-2"));
	}
}
