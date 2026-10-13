package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.MutableClock;
import dev.hearthside.core.input.Control;
import dev.hearthside.core.session.JoinFlow.Effect;
import dev.hearthside.core.session.JoinFlow.Mode;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class JoinFlowProperties {
	enum Kind { A, B, PROFILE, LOADED, LEAVE, DISCONNECT, RECONNECT, TICK, WAIT, REASSIGN, ENTER_GAME, ADD_PLAYER, CANCEL_ADD_PLAYER }

	record Event(Kind kind, int pad) {}

	@Property
	void seatsAreNeverShared(@ForAll("events") List<Event> events, @ForAll Mode mode) {
		MutableClock clock = new MutableClock(java.time.Instant.parse("2026-10-11T12:00:00Z"));
		JoinFlow flow = new JoinFlow(mode, 3, clock);
		Map<Integer, Integer> running = new HashMap<>(); // seat -> started minus stopped
		for (Event e : events) {
			JoinFlow.Result result = apply(flow, clock, e);
			for (Effect effect : result.effects()) {
				if (effect instanceof Effect.StartGuestGame s) {
					running.merge(s.seat(), 1, Integer::sum);
				} else if (effect instanceof Effect.StopGuestGame s) {
					running.merge(s.seat(), -1, Integer::sum);
				}
			}
			Set<Integer> taken = new HashSet<>();
			int seated = 0;
			for (int pad2 = 0; pad2 < 5; pad2++) {
				var seat = flow.seatOf("pad-" + pad2);
				if (seat.isPresent()) {
					seated++;
					assertThat(taken.add(seat.getAsInt())).as("seat %d shared", seat.getAsInt()).isTrue();
					assertThat(seat.getAsInt()).isBetween(0, 2);
				}
			}
			assertThat(seated).isLessThanOrEqualTo(3);
			for (var entry : running.entrySet()) {
				assertThat(entry.getValue()).as("guest games running in seat %d", entry.getKey()).isBetween(0, 1);
			}
		}
	}

	/** A take-over is only ever reported when the seat really moved: the new controller has it, the old one doesn't. */
	@Property
	void aTakeOverAlwaysMovesTheSeat(@ForAll("events") List<Event> events, @ForAll Mode mode) {
		MutableClock clock = new MutableClock(java.time.Instant.parse("2026-10-11T12:00:00Z"));
		JoinFlow flow = new JoinFlow(mode, 3, clock);
		for (Event e : events) {
			Map<String, Integer> before = new HashMap<>();
			for (int pad = 0; pad < 5; pad++) {
				String id = "pad-" + pad;
				flow.seatOf(id).ifPresent(seat -> before.put(id, seat));
			}
			for (Effect effect : apply(flow, clock, e).effects()) {
				if (effect instanceof Effect.TookOver took) {
					assertThat(flow.seatOf(took.from())).as("%s's seat after the take-over", took.from()).isEmpty();
					assertThat(flow.seatOf(took.to())).as("%s's seat after the take-over", took.to())
							.hasValue(before.get(took.from()));
				}
			}
		}
	}

	private static JoinFlow.Result apply(JoinFlow flow, MutableClock clock, Event e) {
		String pad = "pad-" + e.pad();
		return switch (e.kind()) {
			case A -> flow.press(pad, Control.A);
			case B -> flow.press(pad, Control.B);
			case PROFILE -> flow.profileChosen(pad, UUID.nameUUIDFromBytes(pad.getBytes()));
			case LOADED -> flow.loaded(pad);
			case LEAVE -> flow.leave(pad);
			case DISCONNECT -> flow.disconnected(pad);
			case RECONNECT -> flow.reconnected(pad);
			case TICK -> flow.tick();
			case REASSIGN -> flow.reassign(pad, "pad-" + ((e.pad() + 1) % 5));
			case ADD_PLAYER -> flow.addPlayer();
			case CANCEL_ADD_PLAYER -> flow.cancelAddPlayer();
			case ENTER_GAME -> {
				flow.enterGame();
				yield JoinFlow.Result.NONE;
			}
			case WAIT -> {
				clock.advance(Duration.ofSeconds(7));
				yield flow.tick();
			}
		};
	}

	@Provide
	Arbitrary<List<Event>> events() {
		Arbitrary<Event> event = Combinators.combine(Arbitraries.of(Kind.class), Arbitraries.integers().between(0, 4))
				.as(Event::new);
		return event.list().ofMaxSize(120);
	}
}
