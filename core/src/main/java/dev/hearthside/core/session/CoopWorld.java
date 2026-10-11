package dev.hearthside.core.session;

import java.util.List;
import java.util.Objects;

/**
 * A world played in co-op: when, and who played it (in the order they first did).
 *
 * @param levelId the world's folder name
 * @param lastPlayed epoch milliseconds
 */
public record CoopWorld(String levelId, long lastPlayed, List<String> players) {
	public CoopWorld {
		Objects.requireNonNull(levelId, "levelId");
		players = List.copyOf(players);
	}
}
