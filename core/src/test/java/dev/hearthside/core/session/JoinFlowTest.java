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

class JoinFlowTest {
	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-11T12:00:00Z"));
	private final UUID alex = UUID.randomUUID();

	@Test
	void lobbyAClaimsLowestSeatAndPrewarms() {
		JoinFlow flow = new JoinFlow(Mode.LOBBY, 3, clock);
		var result = flow.press("pad-1", Control.A);
		assertThat(result.effects()).containsExactly(new Effect.ClaimSeat("pad-1", 0), new Effect.OpenProfilePicker("pad-1"),
				new Effect.StartGuestGame("pad-1", 0));
		assertThat(flow.seats().get("pad-1")).isInstanceOf(SeatState.ChoosingProfile.class);
		assertThat(flow.seatOf("pad-1")).hasValue(0);
	}

	@Test
	void twoControllersSameTickGetDifferentSeats() {
		JoinFlow flow = new JoinFlow(Mode.LOBBY, 3, clock);
		flow.press("pad-1", Control.A);
		flow.press("pad-2", Control.A);
		assertThat(flow.seatOf("pad-1")).hasValue(0);
		assertThat(flow.seatOf("pad-2")).hasValue(1);
	}

	@Test
	void fullLobbyIgnoresA() {
		JoinFlow flow = new JoinFlow(Mode.LOBBY, 1, clock);
		flow.press("pad-1", Control.A);
		assertThat(flow.press("pad-2", Control.A).effects()).isEmpty();
		assertThat(flow.seatOf("pad-2")).isEmpty();
	}

	@Test
	void bLeavesBeforePlaying() {
		JoinFlow flow = new JoinFlow(Mode.LOBBY, 3, clock);
		flow.press("pad-1", Control.A);
		assertThat(flow.press("pad-1", Control.B).effects()).containsExactly(new Effect.StopGuestGame("pad-1", 0));
		assertThat(flow.seatOf("pad-1")).isEmpty();
		// The freed seat is the lowest again.
		flow.press("pad-2", Control.A);
		assertThat(flow.seatOf("pad-2")).hasValue(0);
	}

	@Test
	void bDoesNotLeaveOncePlaying() {
		JoinFlow flow = new JoinFlow(Mode.LOBBY, 3, clock);
		flow.press("pad-1", Control.A);
		flow.profileChosen("pad-1", alex);
		flow.loaded("pad-1");
		assertThat(flow.press("pad-1", Control.B).effects()).isEmpty();
		assertThat(flow.seats().get("pad-1")).isEqualTo(new SeatState.Playing(alex));
	}

	@Test
	void inGameNeedsTwoPresses() {
		JoinFlow flow = new JoinFlow(Mode.IN_GAME, 3, clock);
		assertThat(flow.press("pad-1", Control.A).effects()).containsExactly(new Effect.ShowJoinPrompt("pad-1"));
		assertThat(flow.seatOf("pad-1")).isEmpty();
		clock.advance(Duration.ofSeconds(2));
		assertThat(flow.press("pad-1", Control.A).effects()).containsExactly(new Effect.HideJoinPrompt("pad-1"),
				new Effect.ClaimSeat("pad-1", 0), new Effect.OpenProfilePicker("pad-1"), new Effect.StartGuestGame("pad-1", 0));
	}

	@Test
	void promptExpiresAfterFiveSeconds() {
		JoinFlow flow = new JoinFlow(Mode.IN_GAME, 3, clock);
		flow.press("pad-1", Control.A);
		clock.advance(Duration.ofSeconds(5));
		assertThat(flow.tick().effects()).containsExactly(new Effect.HideJoinPrompt("pad-1"));
		assertThat(flow.seats()).doesNotContainKey("pad-1");
		// A press after expiry starts over with a new prompt.
		assertThat(flow.press("pad-1", Control.A).effects()).containsExactly(new Effect.ShowJoinPrompt("pad-1"));
	}

	@Test
	void disconnectWhileChoosingKeepsSeat() {
		JoinFlow flow = new JoinFlow(Mode.LOBBY, 3, clock);
		flow.press("pad-1", Control.A);
		flow.disconnected("pad-1");
		assertThat(flow.seatOf("pad-1")).hasValue(0);
		assertThat(flow.seats().get("pad-1")).isInstanceOf(SeatState.Disconnected.class);
		flow.press("pad-2", Control.A);
		assertThat(flow.seatOf("pad-2")).hasValue(1);
	}

	@Test
	void reconnectRestoresState() {
		JoinFlow flow = new JoinFlow(Mode.LOBBY, 3, clock);
		flow.press("pad-1", Control.A);
		flow.profileChosen("pad-1", alex);
		flow.loaded("pad-1");
		flow.disconnected("pad-1");
		flow.reconnected("pad-1");
		assertThat(flow.seats().get("pad-1")).isEqualTo(new SeatState.Playing(alex));
	}

	@Test
	void takeoverOfferedAfterSixtySeconds() {
		JoinFlow flow = new JoinFlow(Mode.IN_GAME, 3, clock);
		flow.press("pad-1", Control.A);
		flow.press("pad-1", Control.A);
		flow.profileChosen("pad-1", alex);
		flow.loaded("pad-1");
		flow.disconnected("pad-1");
		clock.advance(Duration.ofSeconds(59));
		assertThat(flow.tick().effects()).isEmpty();
		clock.advance(Duration.ofSeconds(1));
		assertThat(flow.tick().effects()).containsExactly(new Effect.OfferTakeover("pad-1"));
		assertThat(flow.seats().get("pad-1")).isInstanceOf(SeatState.TakeoverOffered.class);
		assertThat(flow.tick().effects()).as("offered once").isEmpty();
	}

	@Test
	void leaveFreesSeat() {
		JoinFlow flow = new JoinFlow(Mode.LOBBY, 3, clock);
		flow.press("pad-1", Control.A);
		flow.profileChosen("pad-1", alex);
		flow.loaded("pad-1");
		assertThat(flow.leave("pad-1").effects()).containsExactly(new Effect.StopGuestGame("pad-1", 0));
		assertThat(flow.seatOf("pad-1")).isEmpty();
	}

	@Test
	void profileStepsOnlyAdvanceInOrder() {
		JoinFlow flow = new JoinFlow(Mode.LOBBY, 3, clock);
		flow.press("pad-1", Control.A);
		flow.loaded("pad-1"); // no profile yet: ignored
		assertThat(flow.seats().get("pad-1")).isInstanceOf(SeatState.ChoosingProfile.class);
		flow.profileChosen("pad-1", alex);
		assertThat(flow.seats().get("pad-1")).isEqualTo(new SeatState.Ready(alex));
	}
}
