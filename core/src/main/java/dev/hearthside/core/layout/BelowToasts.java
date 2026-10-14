package dev.hearthside.core.layout;

import java.util.Comparator;
import java.util.List;

/**
 * Where a panel hung from the top of the screen sits clear of Minecraft's toasts (the join prompt, Spec E section 3.4):
 * at its own place when nothing is in the way, else just below the lowest toast it would meet, a gap clear of each.
 */
public final class BelowToasts {
	private BelowToasts() {}

	/**
	 * The top edge for a panel {@code width} wide and {@code height} tall at {@code x}, wanted at {@code top}, kept
	 * {@code gap} clear of each of {@code toasts} it would come within that gap of.
	 */
	public static int top(int top, int x, int width, int height, int gap, List<Rect> toasts) {
		int y = top;
		// Top to bottom: moving below one toast can only bring the panel to toasts lower down.
		for (Rect toast : toasts.stream().sorted(Comparator.comparingInt(Rect::y)).toList()) {
			if (new Rect(x, y - gap, width, height + 2 * gap).intersects(toast)) {
				y = toast.y() + toast.height() + gap;
			}
		}
		return y;
	}
}
