package dev.hearthside.fabric.client.host;

import dev.hearthside.core.layout.LayoutPlan;
import dev.hearthside.core.layout.LayoutSettings;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.SplitLayout;
import dev.hearthside.core.layout.ViewFrame;
import dev.hearthside.core.layout.ViewMotion;
import dev.hearthside.fabric.client.compositor.ViewSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The party's views in the one window (Spec A section 6): Spec A's layouts for the players in the world, player 1
 * top-left and the others in the order they joined, gliding whenever someone arrives or the window changes. Render
 * thread.
 */
final class Views implements ViewSource {
	private final ViewMotion motion = new ViewMotion();
	/** Guests' seats in the order they joined (a rejoin goes to the end, so nobody else's view moves for it). */
	private final List<Integer> order = new ArrayList<>();
	private Rect window;
	private Optional<Rect> panel = Optional.empty();

	/** The guests now in the world and the window's size: views glide to the new layout (or jump, if the window changed). */
	void update(Collection<Integer> guests, Rect newWindow, long now, boolean reduceMotion) {
		boolean first = window == null;
		boolean resized = !first && !newWindow.equals(window);
		window = newWindow;
		order.retainAll(guests);
		guests.stream().sorted().filter(seat -> !order.contains(seat)).forEach(order::add);
		List<Integer> seats = new ArrayList<>();
		seats.add(0);
		seats.addAll(order);
		LayoutPlan plan = SplitLayout.plan(seats.size(), newWindow, LayoutSettings.DEFAULTS);
		Map<Integer, Rect> targets = new LinkedHashMap<>();
		for (int i = 0; i < seats.size(); i++) {
			targets.put(seats.get(i), plan.players().get(i));
		}
		panel = plan.panel();
		if (first) {
			// Player 1 had the whole window until now: their view glides from there.
			motion.retarget(Map.of(0, newWindow), now, reduceMotion);
		}
		if (resized) {
			motion.snap(targets);
		} else {
			motion.retarget(targets, now, reduceMotion);
		}
	}

	@Override
	public List<ViewFrame> frames(long nanos) {
		return motion.frames(nanos);
	}

	@Override
	public Optional<Rect> target(int seat) {
		return motion.target(seat);
	}

	@Override
	public Set<Integer> seats() {
		return motion.seats();
	}

	/** The party panel's place (with three players), or empty. */
	Optional<Rect> panel() {
		return panel;
	}
}
