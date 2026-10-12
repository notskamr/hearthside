package dev.hearthside.core.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Option values that hold for one session only (Spec D section 3.3): a preset's value stands in for the player's own
 * while they play, but whenever the options are saved the player's own value is what's written, and the session value
 * comes back afterwards. A player who changes one of those options themselves keeps their change: it is saved, and the
 * session value is never put back over it. Not thread-safe; one game thread uses it.
 *
 * @param <K> what names an option
 */
public final class SessionOverrides<K> {
	/** The player's own value and the session's, for each option the session has overridden. */
	private record Override(Object own, Object session) {}

	private final Map<K, Override> overrides = new HashMap<>();
	/** The value the player last changed each option to, away from the session's. */
	private final Map<K, Object> choices = new HashMap<>();

	/**
	 * The session sets {@code key} to {@code session} in place of the player's {@code own}. Applying a key again keeps
	 * the first own value (the player's) and only changes the session's.
	 */
	public void apply(K key, Object own, Object session) {
		Override before = overrides.get(key);
		overrides.put(key, new Override(before != null ? before.own() : own, session));
	}

	/**
	 * What to save for {@code key}, whose value is now {@code current}: the player's own value while the session's still
	 * stands; once the player has changed it ({@code current} isn't the session's), their change, and the key is the
	 * player's again from then on.
	 */
	public Object valueToSave(K key, Object current) {
		Override override = overrides.get(key);
		if (override == null) {
			return current;
		}
		if (!Objects.equals(current, override.session())) {
			overrides.remove(key);
			choices.put(key, current);
			return current;
		}
		return override.own();
	}

	/** The session value to put back once the options are saved; null if the session doesn't override {@code key}. */
	public Object valueAfterSave(K key) {
		Override override = overrides.get(key);
		return override == null ? null : override.session();
	}

	/**
	 * The value the player last changed {@code key} to themselves this session (away from the session's), or null if
	 * they haven't. It stays theirs even if the session sets the option again later.
	 */
	public Object playersChoice(K key) {
		return choices.get(key);
	}

	/** Whether any option is overridden for the session. */
	public boolean active() {
		return !overrides.isEmpty();
	}
}
