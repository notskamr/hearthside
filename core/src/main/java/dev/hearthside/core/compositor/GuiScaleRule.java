package dev.hearthside.core.compositor;

import dev.hearthside.core.layout.Rect;
import java.util.List;

/**
 * One GUI scale for every view (Spec D section 3.4). Each view's scale is Minecraft's own rule for its size in pixels,
 * at player 1's GUI-scale setting; the party uses the smallest of these, so every HUD and hint looks the same size. A
 * guest at a reduced render resolution gets one scale less and a frame snapped to match, so once the compositor
 * stretches its frame over its view, its GUI is exactly the party's size.
 */
public final class GuiScaleRule {
	/** Minecraft's smallest GUI: a scale only grows while the view stays at least this many pixels per scale. */
	private static final int MIN_GUI_WIDTH = 320;
	private static final int MIN_GUI_HEIGHT = 240;
	/** The smallest party GUI scale at which a reduced render resolution applies (r = 3/4 there). */
	private static final int MIN_REDUCED_PARTY_SCALE = 4;

	private GuiScaleRule() {}

	/**
	 * What one view renders at.
	 *
	 * @param guiScale the GUI scale the view's game uses, at its frame size
	 * @param renderScale the frame's size over the view's; the compositor stretches the frame back over the view
	 * @param frameWidth the frame the view's game renders, in pixels
	 * @param frameHeight the frame the view's game renders, in pixels
	 */
	public record ViewScale(int guiScale, double renderScale, int frameWidth, int frameHeight) {
		public ViewScale {
			if (guiScale < 1 || frameWidth < 1 || frameHeight < 1) {
				throw new IllegalArgumentException("Bad view scale: " + guiScale + " at " + frameWidth + "x" + frameHeight);
			}
		}
	}

	/**
	 * Minecraft's {@code Window.calculateScale(setting, false)} for a framebuffer this size: 0 is Auto, the largest
	 * scale that still fits 320×240 GUI pixels; a fixed setting is used when it fits, else capped the same way.
	 */
	public static int autoScale(int setting, int fbWidth, int fbHeight) {
		int scale = 1;
		while (scale != setting && scale < fbWidth && scale < fbHeight
				&& fbWidth / (scale + 1) >= MIN_GUI_WIDTH && fbHeight / (scale + 1) >= MIN_GUI_HEIGHT) {
			scale++;
		}
		return scale;
	}

	/**
	 * The party's GUI scale: {@link #autoScale} at player 1's {@code setting} for each view, the smallest of them.
	 *
	 * @throws IllegalArgumentException for an empty list
	 */
	public static int partyScale(int setting, List<Rect> views) {
		if (views.isEmpty()) {
			throw new IllegalArgumentException("A party has at least one view");
		}
		int smallest = Integer.MAX_VALUE;
		for (Rect view : views) {
			smallest = Math.min(smallest, autoScale(setting, view.width(), view.height()));
		}
		return smallest;
	}

	/**
	 * What {@code view} renders at under the party's scale. At a reduced resolution and a party scale {@code g} of 4 or
	 * more, the frame is {@code (g-1)/g} of the view with a GUI scale of {@code g-1}, which looks exactly {@code g} once
	 * stretched. At 3 or less the view keeps its full size: at 2 a smaller GUI scale can't look the same, and at 3 the
	 * 1.5× stretch of a 2/3 frame leaves hint text visibly soft (Task 8's visual QA).
	 */
	public static ViewScale forView(Rect view, int partyScale, boolean reducedResolution) {
		if (reducedResolution && partyScale >= MIN_REDUCED_PARTY_SCALE) {
			double r = (partyScale - 1) / (double) partyScale;
			return new ViewScale(partyScale - 1, r, Math.max(1, (int) Math.round(view.width() * r)),
					Math.max(1, (int) Math.round(view.height() * r)));
		}
		return new ViewScale(partyScale, 1.0, Math.max(1, view.width()), Math.max(1, view.height()));
	}
}
