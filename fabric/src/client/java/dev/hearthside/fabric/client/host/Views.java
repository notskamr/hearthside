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
 * The party's views in the one window (Spec A section 6): Spec A's layouts for the players in the world, as the host's
 * {@code config/hearthside.json} arranges them, player 1 top-left and the others in the order they joined, gliding
 * whenever someone arrives or the window changes. A leaving view fades out where it is first; the party then re-lays
 * out without it. Render thread.
 */
final class Views implements ViewSource {
	private final ViewMotion motion = new ViewMotion();
	/** Side by side or stacked, the three-player arrangement and the party panel. */
	private final LayoutSettings settings;
	/** Guests' seats in the order they joined (a rejoin goes to the end, so nobody else's view moves for it). */
	private final List<Integer> order = new ArrayList<>();
	private Rect window;
	private Optional<Rect> panel = Optional.empty();
	/** When the layout last changed (name tags show again for a while after), and the panel last appeared or went. */
	private long changedAt, panelSince, panelGoneAt;
	/** Where the panel was before it went (it fades out there), or null. */
	private Rect lastPanel;

	Views(LayoutSettings settings) {
		this.settings = settings;
	}

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
		LayoutPlan plan = SplitLayout.plan(seats.size(), newWindow, settings);
		Map<Integer, Rect> targets = new LinkedHashMap<>();
		for (int i = 0; i < seats.size(); i++) {
			targets.put(seats.get(i), plan.players().get(i));
		}
		if (plan.panel().isPresent() && panel.isEmpty()) {
			panelSince = now;
		}
		// A panel that goes fades out where it was while the views glide over it (one cut by a resize just goes).
		lastPanel = plan.panel().isEmpty() && !resized ? panel.orElse(lastPanel) : null;
		if (plan.panel().isEmpty() && panel.isPresent()) {
			panelGoneAt = now;
		}
		panel = plan.panel();
		changedAt = now;
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

	/** A guest leaves: their view fades out where it is while everyone else stays put; then {@link #update} without them. */
	void leave(int seat, long now) {
		order.remove(Integer.valueOf(seat));
		motion.leave(seat, Map.of(), now, false);
	}

	/** Whether a leaving view is still fading out. */
	boolean fading(long now) {
		return motion.fading(now);
	}

	/** Whether any view is still fading or gliding. */
	boolean moving(long now) {
		return motion.moving(now);
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

	long changedAt() {
		return changedAt;
	}

	/** Where the party panel is drawn now, and how opaque: it fades in as it appears and out as it goes. */
	record PanelDraw(Rect rect, float alpha) {}

	/** The party panel (with three players) as drawn now, or empty. */
	Optional<PanelDraw> panelAt(long now, boolean reduceMotion) {
		if (panel.isPresent()) {
			return Optional.of(new PanelDraw(panel.get(), reduceMotion ? 1f : progress(panelSince, now)));
		}
		float shown = lastPanel == null || reduceMotion ? 0f : 1f - progress(panelGoneAt, now);
		return shown > 0f ? Optional.of(new PanelDraw(lastPanel, shown)) : Optional.empty();
	}

	private static float progress(long since, long now) {
		return Math.clamp((now - since) / (float) ViewMotion.GLIDE.toNanos(), 0f, 1f);
	}
}
