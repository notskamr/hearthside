package dev.hearthside.core.layout;

import java.util.List;
import java.util.Optional;

/** Lays out 1–4 player views (and the party panel) on a screen, per {@link LayoutSettings}. */
public final class SplitLayout {
	private SplitLayout() {}

	public static LayoutPlan plan(int players, Rect screen, LayoutSettings settings) {
		if (players < 1 || players > 4) {
			throw new IllegalArgumentException("Player count must be 1-4, was " + players);
		}
		if (players == 1) {
			return new LayoutPlan(List.of(screen), Optional.empty());
		}
		Split cols = Split.of(screen.x(), screen.width(), settings.gap());
		Split rows = Split.of(screen.y(), screen.height(), settings.gap());
		Rect left = new Rect(cols.firstStart, screen.y(), cols.firstSize, screen.height());
		Rect right = new Rect(cols.secondStart, screen.y(), cols.secondSize, screen.height());
		Rect top = new Rect(screen.x(), rows.firstStart, screen.width(), rows.firstSize);
		Rect bottom = new Rect(screen.x(), rows.secondStart, screen.width(), rows.secondSize);
		Rect topLeft = new Rect(cols.firstStart, rows.firstStart, cols.firstSize, rows.firstSize);
		Rect topRight = new Rect(cols.secondStart, rows.firstStart, cols.secondSize, rows.firstSize);
		Rect bottomLeft = new Rect(cols.firstStart, rows.secondStart, cols.firstSize, rows.secondSize);
		Rect bottomRight = new Rect(cols.secondStart, rows.secondStart, cols.secondSize, rows.secondSize);
		return switch (players) {
			case 2 -> new LayoutPlan(settings.two() == LayoutSettings.TwoPlayer.SIDE_BY_SIDE
					? List.of(left, right)
					: List.of(top, bottom), Optional.empty());
			case 3 -> switch (settings.three()) {
				case GRID_WITH_PANEL -> new LayoutPlan(List.of(topLeft, topRight, bottomLeft),
						settings.partyPanel() ? Optional.of(bottomRight) : Optional.empty());
				case WIDE_TOP -> new LayoutPlan(List.of(top, bottomLeft, bottomRight), Optional.empty());
				case TALL_LEFT -> new LayoutPlan(List.of(left, topRight, bottomRight), Optional.empty());
			};
			default -> new LayoutPlan(List.of(topLeft, topRight, bottomLeft, bottomRight), Optional.empty());
		};
	}

	/** One axis cut in two with a divider; on odd sizes the second part gets the extra pixel. */
	private record Split(int firstStart, int firstSize, int secondStart, int secondSize) {
		static Split of(int start, int size, int gap) {
			int g = Math.min(gap, size); // a divider wider than the screen would leave negative views
			int first = (size - g) / 2;
			int second = size - g - first;
			return new Split(start, first, start + first + g, second);
		}
	}
}
