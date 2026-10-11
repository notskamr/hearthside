package dev.hearthside.core.session;

import dev.hearthside.core.input.Control;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Who has a guest seat and how far they are in joining, driven by controller events (Spec A §3). The same flow runs
 * the party lobby ({@link Mode#LOBBY}: one A takes a seat) and drop-in during play ({@link Mode#IN_GAME}: a second A
 * within {@link #CONFIRM_WINDOW} confirms). Returns the effects the game must carry out. Not thread-safe.
 */
public final class JoinFlow {
	public static final Duration CONFIRM_WINDOW = Duration.ofSeconds(5);
	public static final Duration TAKEOVER_AFTER = Duration.ofSeconds(60);

	public enum Mode { LOBBY, IN_GAME }

	/** Something the game must do in response. */
	public sealed interface Effect {
		record ShowJoinPrompt(String controller) implements Effect {}

		record HideJoinPrompt(String controller) implements Effect {}

		record ClaimSeat(String controller, int seat) implements Effect {}

		record OpenProfilePicker(String controller) implements Effect {}

		/** Start (pre-warm) this seat's guest game. */
		record StartGuestGame(String controller, int seat) implements Effect {}

		record StopGuestGame(String controller, int seat) implements Effect {}

		record OfferTakeover(String controller) implements Effect {}
	}

	public record Result(List<Effect> effects) {
		static final Result NONE = new Result(List.of());

		public Result {
			effects = List.copyOf(effects);
		}
	}

	private final Mode mode;
	private final int seatCount;
	private final Clock clock;
	private final Map<String, SeatState> states = new LinkedHashMap<>();
	private final Map<String, Integer> seats = new HashMap<>();

	/** @param seatCount guest seats (player 1 is not counted), 1–3 */
	public JoinFlow(Mode mode, int seatCount, Clock clock) {
		if (seatCount < 1 || seatCount > 3) {
			throw new IllegalArgumentException("Guest seats must be 1-3, was " + seatCount);
		}
		this.mode = mode;
		this.seatCount = seatCount;
		this.clock = clock;
	}

	/** Every controller that is prompted or seated, and its state. */
	public Map<String, SeatState> seats() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(states));
	}

	public OptionalInt seatOf(String controller) {
		Integer seat = seats.get(controller);
		return seat == null ? OptionalInt.empty() : OptionalInt.of(seat);
	}

	public Result press(String controller, Control control) {
		SeatState state = states.get(controller);
		Instant now = clock.instant();
		if (control == Control.A) {
			if (state == null) {
				if (mode == Mode.LOBBY) {
					return claim(controller, new ArrayList<>());
				}
				if (freeSeat() < 0) {
					return Result.NONE;
				}
				states.put(controller, new SeatState.Prompted(now.plus(CONFIRM_WINDOW)));
				return new Result(List.of(new Effect.ShowJoinPrompt(controller)));
			}
			if (state instanceof SeatState.Prompted prompted) {
				states.remove(controller);
				List<Effect> effects = new ArrayList<>(List.of(new Effect.HideJoinPrompt(controller)));
				if (now.isBefore(prompted.until())) {
					return claim(controller, effects);
				}
				states.put(controller, new SeatState.Prompted(now.plus(CONFIRM_WINDOW)));
				effects.add(new Effect.ShowJoinPrompt(controller));
				return new Result(effects);
			}
			return Result.NONE;
		}
		if (control == Control.B) {
			if (state instanceof SeatState.Prompted) {
				states.remove(controller);
				return new Result(List.of(new Effect.HideJoinPrompt(controller)));
			}
			if (state instanceof SeatState.ChoosingProfile || state instanceof SeatState.Ready) {
				return free(controller);
			}
		}
		return Result.NONE;
	}

	public Result profileChosen(String controller, UUID profile) {
		if (states.get(controller) instanceof SeatState.ChoosingProfile) {
			states.put(controller, new SeatState.Ready(profile));
		}
		return Result.NONE;
	}

	public Result loaded(String controller) {
		if (states.get(controller) instanceof SeatState.Ready ready) {
			states.put(controller, new SeatState.Playing(ready.profile()));
		}
		return Result.NONE;
	}

	public Result leave(String controller) {
		return seats.containsKey(controller) ? free(controller) : Result.NONE;
	}

	public Result disconnected(String controller) {
		SeatState state = states.get(controller);
		if (state instanceof SeatState.Prompted) {
			states.remove(controller);
			return new Result(List.of(new Effect.HideJoinPrompt(controller)));
		}
		if (seats.containsKey(controller) && !(state instanceof SeatState.Disconnected)
				&& !(state instanceof SeatState.TakeoverOffered)) {
			states.put(controller, new SeatState.Disconnected(state, clock.instant()));
		}
		return Result.NONE;
	}

	public Result reconnected(String controller) {
		SeatState state = states.get(controller);
		if (state instanceof SeatState.Disconnected d) {
			states.put(controller, d.before());
		} else if (state instanceof SeatState.TakeoverOffered t) {
			states.put(controller, t.before());
		}
		return Result.NONE;
	}

	/** Expires join prompts and offers take-overs; call regularly (e.g. once a second). */
	public Result tick() {
		Instant now = clock.instant();
		List<Effect> effects = new ArrayList<>();
		for (Map.Entry<String, SeatState> entry : List.copyOf(states.entrySet())) {
			String controller = entry.getKey();
			if (entry.getValue() instanceof SeatState.Prompted p && !now.isBefore(p.until())) {
				states.remove(controller);
				effects.add(new Effect.HideJoinPrompt(controller));
			} else if (entry.getValue() instanceof SeatState.Disconnected d
					&& !now.isBefore(d.since().plus(TAKEOVER_AFTER))) {
				states.put(controller, new SeatState.TakeoverOffered(d.before()));
				effects.add(new Effect.OfferTakeover(controller));
			}
		}
		return new Result(effects);
	}

	private Result claim(String controller, List<Effect> effects) {
		int seat = freeSeat();
		if (seat < 0) {
			return new Result(effects);
		}
		seats.put(controller, seat);
		states.put(controller, new SeatState.ChoosingProfile());
		effects.add(new Effect.ClaimSeat(controller, seat));
		effects.add(new Effect.OpenProfilePicker(controller));
		effects.add(new Effect.StartGuestGame(controller, seat));
		return new Result(effects);
	}

	private Result free(String controller) {
		Integer seat = seats.remove(controller);
		states.remove(controller);
		return seat == null ? Result.NONE : new Result(List.of(new Effect.StopGuestGame(controller, seat)));
	}

	private int freeSeat() {
		for (int seat = 0; seat < seatCount; seat++) {
			if (!seats.containsValue(seat)) {
				return seat;
			}
		}
		return -1;
	}
}
