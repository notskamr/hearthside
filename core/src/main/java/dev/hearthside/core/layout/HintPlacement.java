package dev.hearthside.core.layout;

import java.util.List;
import java.util.Optional;

/**
 * Where a row of controller hints goes on a Minecraft screen: the first free spot of five, bottom centre, bottom right,
 * bottom left, top right and top left, inside the screen and over nothing the screen shows (its buttons, fields, lists
 * and inventory panel) or Minecraft draws over it (its toasts, top right). Edges are half-open, so a row touching a
 * button does not cover it.
 */
public final class HintPlacement {
	private HintPlacement() {}

	/**
	 * @param screen the screen's bounds
	 * @param obstacles what the row must not cover
	 * @param margin the gap kept from the screen's edges
	 * @return the row's bounds, or empty when it fits nowhere
	 */
	public static Optional<Rect> place(Rect screen, List<Rect> obstacles, int width, int height, int margin) {
		int left = screen.x() + margin;
		int right = screen.x() + screen.width() - margin - width;
		int centre = screen.x() + (screen.width() - width) / 2;
		int bottom = screen.y() + screen.height() - margin - height;
		int top = screen.y() + margin;
		List<Rect> spots = List.of(new Rect(centre, bottom, width, height), new Rect(right, bottom, width, height),
				new Rect(left, bottom, width, height), new Rect(right, top, width, height),
				new Rect(left, top, width, height));
		for (Rect spot : spots) {
			if (screen.contains(spot) && obstacles.stream().noneMatch(spot::intersects)) {
				return Optional.of(spot);
			}
		}
		return Optional.empty();
	}
}
