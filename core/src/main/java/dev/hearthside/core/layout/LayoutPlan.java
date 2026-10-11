package dev.hearthside.core.layout;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Where each player's view goes, and the party panel if there is one.
 *
 * @param players one rectangle per player; index 0 is player 1
 */
public record LayoutPlan(List<Rect> players, Optional<Rect> panel) {
	public LayoutPlan {
		players = List.copyOf(players);
		Objects.requireNonNull(panel, "panel");
	}
}
