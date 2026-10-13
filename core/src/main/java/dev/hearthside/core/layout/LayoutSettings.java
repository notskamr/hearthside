package dev.hearthside.core.layout;

import java.util.Objects;

/**
 * How the screen is shared, as players configure it. Defaults (Spec A §6, Spec E §3.5): two players side by side, three
 * in a grid with the party panel in the free quarter, a 2-pixel divider, thin borders, name tags, and views that glide
 * to a new layout.
 *
 * @param gap divider width in pixels between views (never at the outer edges)
 * @param borders how each view is edged
 * @param nameTags whether each view shows its player's name
 * @param layoutAnimation whether views glide to a new layout rather than jump
 */
public record LayoutSettings(TwoPlayer two, ThreePlayer three, boolean partyPanel, int gap, BorderStyle borders,
		boolean nameTags, boolean layoutAnimation) {
	public enum TwoPlayer { SIDE_BY_SIDE, STACKED }

	public enum ThreePlayer { GRID_WITH_PANEL, WIDE_TOP, TALL_LEFT }

	public static final LayoutSettings DEFAULTS = new LayoutSettings(TwoPlayer.SIDE_BY_SIDE, ThreePlayer.GRID_WITH_PANEL, true, 2);

	public LayoutSettings {
		Objects.requireNonNull(two, "two");
		Objects.requireNonNull(three, "three");
		Objects.requireNonNull(borders, "borders");
		gap = Math.max(0, gap);
	}

	/** The arrangement, with the look (borders, name tags, animation) at its defaults. */
	public LayoutSettings(TwoPlayer two, ThreePlayer three, boolean partyPanel, int gap) {
		this(two, three, partyPanel, gap, BorderStyle.THIN, true, true);
	}

	public LayoutSettings withTwo(TwoPlayer value) {
		return new LayoutSettings(value, three, partyPanel, gap, borders, nameTags, layoutAnimation);
	}

	public LayoutSettings withThree(ThreePlayer value) {
		return new LayoutSettings(two, value, partyPanel, gap, borders, nameTags, layoutAnimation);
	}

	public LayoutSettings withPartyPanel(boolean value) {
		return new LayoutSettings(two, three, value, gap, borders, nameTags, layoutAnimation);
	}

	public LayoutSettings withGap(int value) {
		return new LayoutSettings(two, three, partyPanel, value, borders, nameTags, layoutAnimation);
	}

	public LayoutSettings withBorders(BorderStyle value) {
		return new LayoutSettings(two, three, partyPanel, gap, value, nameTags, layoutAnimation);
	}

	public LayoutSettings withNameTags(boolean value) {
		return new LayoutSettings(two, three, partyPanel, gap, borders, value, layoutAnimation);
	}

	public LayoutSettings withLayoutAnimation(boolean value) {
		return new LayoutSettings(two, three, partyPanel, gap, borders, nameTags, value);
	}
}
