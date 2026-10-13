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
 *
 * <p>Two things beat the usual join (Spec E §3.3–3.4): once a dropped player's seat is offered for take-over, a seatless
 * controller's A takes that player over; and while player 1's Add player window is open, a seatless controller's
 * single A joins.
 */
public final class JoinFlow {
	public static final Duration CONFIRM_WINDOW = Duration.ofSeconds(5);
	public static final Duration TAKEOVER_AFTER = Duration.ofSeconds(60);
	public static final Duration ADD_PLAYER_WINDOW = Duration.ofSeconds(15);

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

		/** Controller {@code to} took over the player whose controller {@code from} dropped: same seat, same game. */
		record TookOver(String from, String to) implements Effect {}

		/** Player 1 chose Add player: a free controller's single A joins until {@code until}. */
		record ShowAddPlayerPrompt(Instant until) implements Effect {}

		/** The Add player window closed: someone joined, it expired, or it was cancelled. */
		record HideAddPlayerPrompt() implements Effect {}
	}

	public record Result(List<Effect> effects) {
		public static final Result NONE = new Result(List.of());

		public Result {
			effects = List.copyOf(effects);
		}
	}

	private Mode mode;
	private final int seatCount;
	private final Clock clock;
	private final Duration takeoverAfter;
	private final Map<String, SeatState> states = new LinkedHashMap<>();
	private final Map<String, Integer> seats = new HashMap<>();
	/** When the Add player window closes; null while it's closed. */
	private Instant addPlayerUntil;

	/** @param seatCount guest seats (player 1 is not counted), 1–3 */
	public JoinFlow(Mode mode, int seatCount, Clock clock) {
		this(mode, seatCount, clock, TAKEOVER_AFTER);
	}

	/**
	 * @param seatCount guest seats (player 1 is not counted), 1–3
	 * @param takeoverAfter how long a dropped controller is waited for before another may take the player over
	 */
	public JoinFlow(Mode mode, int seatCount, Clock clock, Duration takeoverAfter) {
		if (seatCount < 1 || seatCount > 3) {
			throw new IllegalArgumentException("Guest seats must be 1-3, was " + seatCount);
		}
		if (takeoverAfter.isNegative()) {
			throw new IllegalArgumentException("Take-over delay must not be negative, was " + takeoverAfter);
		}
		this.mode = mode;
		this.seatCount = seatCount;
		this.clock = clock;
		this.takeoverAfter = takeoverAfter;
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
			if (!seats.containsKey(controller)) {
				String dropped = lowestTakeoverOffered();
				if (dropped != null) {
					List<Effect> effects = new ArrayList<>(reassign(dropped, controller).effects());
					effects.add(new Effect.TookOver(dropped, controller));
					return new Result(effects);
				}
				if (addPlayerUntil != null && now.isBefore(addPlayerUntil) && freeSeat() >= 0) {
					// Player 1 asked for a player: one A is enough, and it settles any double-A prompt this pad had.
					List<Effect> effects = new ArrayList<>();
					if (states.remove(controller) instanceof SeatState.Prompted) {
						effects.add(new Effect.HideJoinPrompt(controller));
					}
					addPlayerUntil = null;
					effects.add(new Effect.HideAddPlayerPrompt());
					return claim(controller, effects);
				}
			}
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

	/**
	 * Player 1 chose Add player: for {@link #ADD_PLAYER_WINDOW} a free controller's single A joins. Choosing it again
	 * restarts the window. Nothing happens when the party is full.
	 */
	public Result addPlayer() {
		if (partyFull()) {
			return Result.NONE;
		}
		addPlayerUntil = clock.instant().plus(ADD_PLAYER_WINDOW);
		return new Result(List.of(new Effect.ShowAddPlayerPrompt(addPlayerUntil)));
	}

	/** Player 1 cancelled the Add player prompt. */
	public Result cancelAddPlayer() {
		if (addPlayerUntil == null) {
			return Result.NONE;
		}
		addPlayerUntil = null;
		return new Result(List.of(new Effect.HideAddPlayerPrompt()));
	}

	/** Whether every guest seat is taken. */
	public boolean partyFull() {
		return freeSeat() < 0;
	}

	/** The lobby is over and play has started: keep every seat, and new joins now need A, A. */
	public void enterGame() {
		mode = Mode.IN_GAME;
	}

	public Result profileChosen(String controller, UUID profile) {
		advance(controller, state -> state instanceof SeatState.ChoosingProfile ? new SeatState.Ready(profile) : state);
		return Result.NONE;
	}

	/**
	 * A ready seat goes back to choosing its profile (Y on its lobby card, Spec A section 5.5). Not once playing: a
	 * player in the world changes profile from their pause menu. Returns whether the seat is choosing again.
	 */
	public boolean changeProfile(String controller) {
		if (states.get(controller) instanceof SeatState.Ready) {
			states.put(controller, new SeatState.ChoosingProfile());
			return true;
		}
		return false;
	}

	/** The seat's game reached the world. Applies even while the controller is unplugged. */
	public Result loaded(String controller) {
		advance(controller, state -> state instanceof SeatState.Ready ready ? new SeatState.Playing(ready.profile()) : state);
		return Result.NONE;
	}

	/**
	 * Moves a seat, with its state, to another controller: the same pad reconnecting under a new id, or another pad
	 * taking over a disconnected player. Their game keeps running. Refused if {@code to} already has a seat.
	 */
	public Result reassign(String from, String to) {
		Integer seat = seats.get(from);
		if (seat == null || from.equals(to) || seats.containsKey(to)) {
			return Result.NONE;
		}
		SeatState state = states.remove(from);
		seats.remove(from);
		// The new controller is connected, so a dropped seat is live again.
		if (state instanceof SeatState.Disconnected d) {
			state = d.before();
		} else if (state instanceof SeatState.TakeoverOffered t) {
			state = t.before();
		}
		List<Effect> effects = new ArrayList<>();
		if (states.remove(to) instanceof SeatState.Prompted) {
			effects.add(new Effect.HideJoinPrompt(to));
		}
		seats.put(to, seat);
		states.put(to, state);
		return new Result(effects);
	}

	/** Applies a step to a seat's state, looking through a disconnect so progress made meanwhile isn't lost. */
	private void advance(String controller, java.util.function.UnaryOperator<SeatState> step) {
		SeatState state = states.get(controller);
		if (state instanceof SeatState.Disconnected d) {
			states.put(controller, new SeatState.Disconnected(step.apply(d.before()), d.since()));
		} else if (state instanceof SeatState.TakeoverOffered t) {
			states.put(controller, new SeatState.TakeoverOffered(step.apply(t.before())));
		} else if (state != null) {
			states.put(controller, step.apply(state));
		}
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

	/** Expires join prompts and the Add player window, and offers take-overs; call regularly (e.g. once a second). */
	public Result tick() {
		Instant now = clock.instant();
		List<Effect> effects = new ArrayList<>();
		if (addPlayerUntil != null && !now.isBefore(addPlayerUntil)) {
			addPlayerUntil = null;
			effects.add(new Effect.HideAddPlayerPrompt());
		}
		for (Map.Entry<String, SeatState> entry : List.copyOf(states.entrySet())) {
			String controller = entry.getKey();
			if (entry.getValue() instanceof SeatState.Prompted p && !now.isBefore(p.until())) {
				states.remove(controller);
				effects.add(new Effect.HideJoinPrompt(controller));
			} else if (entry.getValue() instanceof SeatState.Disconnected d
					&& !now.isBefore(d.since().plus(takeoverAfter))) {
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

	/** The controller of the lowest seat offered for take-over, or null if none is. */
	private String lowestTakeoverOffered() {
		String lowest = null;
		for (Map.Entry<String, SeatState> entry : states.entrySet()) {
			if (entry.getValue() instanceof SeatState.TakeoverOffered
					&& (lowest == null || seats.get(entry.getKey()) < seats.get(lowest))) {
				lowest = entry.getKey();
			}
		}
		return lowest;
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
