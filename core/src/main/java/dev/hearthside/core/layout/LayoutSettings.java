package dev.hearthside.core.layout;

import java.util.Objects;

/**
 * How the screen is shared, as players configure it. Defaults (Spec A §6): two players side by side, three in a grid
 * with the party panel in the free quarter, a 2-pixel divider.
 *
 * @param gap divider width in pixels between views (never at the outer edges)
 */
public record LayoutSettings(TwoPlayer two, ThreePlayer three, boolean partyPanel, int gap) {
	public enum TwoPlayer { SIDE_BY_SIDE, STACKED }

	public enum ThreePlayer { GRID_WITH_PANEL, WIDE_TOP, TALL_LEFT }

	public static final LayoutSettings DEFAULTS = new LayoutSettings(TwoPlayer.SIDE_BY_SIDE, ThreePlayer.GRID_WITH_PANEL, true, 2);

	public LayoutSettings {
		Objects.requireNonNull(two, "two");
		Objects.requireNonNull(three, "three");
		gap = Math.max(0, gap);
	}

	public LayoutSettings withTwo(TwoPlayer value) {
		return new LayoutSettings(value, three, partyPanel, gap);
	}

	public LayoutSettings withThree(ThreePlayer value) {
		return new LayoutSettings(two, value, partyPanel, gap);
	}

	public LayoutSettings withPartyPanel(boolean value) {
		return new LayoutSettings(two, three, value, gap);
	}

	public LayoutSettings withGap(int value) {
		return new LayoutSettings(two, three, partyPanel, value);
	}
}
