package dev.hearthside.core.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Swap views (Spec E §3.5): the order in which seats fill the layout's views, moved one step at a time so player 1
 * isn't always top-left. Returns a new list; the input is never changed. For the session only.
 */
public final class ViewOrder {
	private ViewOrder() {}

	/** {@code seat} swapped with the seat before it; unchanged if it's first or not in the order. */
	public static List<Integer> earlier(List<Integer> order, int seat) {
		return swap(order, order.indexOf(seat), -1);
	}

	/** {@code seat} swapped with the seat after it; unchanged if it's last or not in the order. */
	public static List<Integer> later(List<Integer> order, int seat) {
		return swap(order, order.indexOf(seat), 1);
	}

	private static List<Integer> swap(List<Integer> order, int at, int step) {
		List<Integer> moved = new ArrayList<>(order);
		int to = at + step;
		if (at >= 0 && to >= 0 && to < moved.size()) {
			Collections.swap(moved, at, to);
		}
		return List.copyOf(moved);
	}
}
