package dev.hearthside.core.session;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Each seat's view phase and how much of it the card covers (Spec A sections 3.1(5), 3.2): a joining player's view
 * shows their card, never their game's loading screens, until the game says it is in the world; then the card
 * crossfades into the world. A restarted game brings the card back over its last frame. Times are
 * {@link System#nanoTime()} values. Not thread-safe.
 */
public final class ViewPhases {
	public static final Duration APPEAR = Duration.ofMillis(200), CROSSFADE = Duration.ofMillis(250),
			STUCK_AFTER = Duration.ofSeconds(45);

	private record State(ViewPhase phase, long since, float coverWhenLeaving) {}

	private final Map<Integer, State> states = new HashMap<>();

	public void picking(int seat, long now) {
		states.put(seat, new State(ViewPhase.PICKING, now, 1));
	}

	public void joining(int seat, long now) {
		states.put(seat, new State(ViewPhase.JOINING, now, 1));
	}

	/** The guest is in the world: the card crossfades to its frames. */
	public void inWorld(int seat, long now) {
		states.put(seat, new State(ViewPhase.LIVE, now, 0));
	}

	/** Its game restarted: the card comes back over its last frame. */
	public void reconnecting(int seat, long now) {
		states.put(seat, new State(ViewPhase.RECONNECTING, now, 1));
	}

	/** Fading out: whatever showed (card or world) stays as it was. */
	public void leaving(int seat, long now) {
		float cover = cardCover(seat, now, true);
		states.put(seat, new State(ViewPhase.LEAVING, now, cover));
	}

	public void remove(int seat) {
		states.remove(seat);
	}

	public Optional<ViewPhase> phase(int seat) {
		State state = states.get(seat);
		return state == null ? Optional.empty() : Optional.of(state.phase());
	}

	/** How much the card covers the view: 1 = only the card shows, 0 = only the game's frames. */
	public float cardCover(int seat, long now, boolean reduceMotion) {
		State state = states.get(seat);
		if (state == null) {
			return 0;
		}
		return switch (state.phase()) {
			case PICKING, JOINING -> 1;
			case LIVE -> reduceMotion ? 0 : 1 - progress(state.since(), now, CROSSFADE);
			case RECONNECTING -> reduceMotion ? 1 : progress(state.since(), now, APPEAR);
			case LEAVING -> state.coverWhenLeaving();
		};
	}

	/** Joining (or reconnecting) for longer than {@link #STUCK_AFTER}. */
	public boolean stuck(int seat, long now) {
		State state = states.get(seat);
		return state != null && (state.phase() == ViewPhase.JOINING || state.phase() == ViewPhase.RECONNECTING)
				&& now - state.since() >= STUCK_AFTER.toNanos();
	}

	private static float progress(long since, long now, Duration length) {
		return (float) Math.clamp((now - since) / (double) length.toNanos(), 0, 1);
	}
}
