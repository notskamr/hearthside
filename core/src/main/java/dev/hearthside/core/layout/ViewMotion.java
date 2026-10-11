package dev.hearthside.core.layout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Where each player's view is drawn while the layout changes (Spec A sections 4.4, 6): views glide to new rectangles
 * over {@link #GLIDE}, a new view fades in at its place, and a leaving view fades out where it is before the others
 * glide into its space. A change during a glide starts from where the views are drawn now, so nothing ever jumps.
 * With reduce motion every change is immediate. Times are {@link System#nanoTime()} values. Not thread-safe.
 */
public final class ViewMotion {
	public static final Duration GLIDE = Duration.ofMillis(300), FADE_OUT = Duration.ofMillis(250);

	private static final long GLIDE_NANOS = GLIDE.toNanos(), FADE_NANOS = FADE_OUT.toNanos();

	/** One seat's motion: a rect glide (which may start later than now) and an alpha fade. */
	private static final class Track {
		Rect from, to;
		long rectStart, rectNanos;
		float alphaFrom = 1, alphaTo = 1;
		long alphaStart, alphaNanos;
		/** Set while leaving: the time it is gone. */
		long goneAt = Long.MAX_VALUE;

		Track(Rect at) {
			from = at;
			to = at;
		}

		Rect rect(long now) {
			if (rectNanos == 0 || now >= rectStart + rectNanos) {
				return now < rectStart ? from : to;
			}
			if (now <= rectStart) {
				return from;
			}
			double e = Ease.inOutCubic((now - rectStart) / (double) rectNanos);
			int left = lerp(from.x(), to.x(), e);
			int top = lerp(from.y(), to.y(), e);
			int right = lerp(from.x() + from.width(), to.x() + to.width(), e);
			int bottom = lerp(from.y() + from.height(), to.y() + to.height(), e);
			return new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
		}

		float alpha(long now) {
			if (alphaNanos == 0 || now >= alphaStart + alphaNanos) {
				return now < alphaStart ? alphaFrom : alphaTo;
			}
			if (now <= alphaStart) {
				return alphaFrom;
			}
			double t = (now - alphaStart) / (double) alphaNanos;
			return (float) (alphaFrom + (alphaTo - alphaFrom) * t);
		}

		void glide(Rect target, long start, long nanos, long now) {
			from = rect(now);
			to = target;
			rectStart = start;
			rectNanos = nanos;
		}

		void place(Rect target) {
			from = target;
			to = target;
			rectNanos = 0;
		}

		void fade(float target, long start, long nanos, long now) {
			alphaFrom = alpha(now);
			alphaTo = target;
			alphaStart = start;
			alphaNanos = nanos;
		}

		boolean moving(long now) {
			return (rectNanos > 0 && now < rectStart + rectNanos) || (alphaNanos > 0 && now < alphaStart + alphaNanos)
					|| goneAt != Long.MAX_VALUE;
		}
	}

	private final Map<Integer, Track> tracks = new TreeMap<>();

	private static int lerp(int a, int b, double e) {
		return (int) Math.round(a + (b - a) * e);
	}

	/**
	 * A new layout: rects by seat (0 = player 1). Seats already drawn glide from where they are now; a new seat fades
	 * in at its rect (or simply appears, in the first layout); a seat missing from {@code targets} vanishes at once,
	 * unless it is fading out after {@link #leave}.
	 */
	public void retarget(Map<Integer, Rect> targets, long now, boolean reduceMotion) {
		drop(now);
		boolean first = tracks.isEmpty();
		tracks.entrySet().removeIf(e -> !targets.containsKey(e.getKey()) && e.getValue().goneAt == Long.MAX_VALUE);
		for (Map.Entry<Integer, Rect> entry : new TreeMap<>(targets).entrySet()) {
			Track track = tracks.get(entry.getKey());
			if (track == null || track.goneAt != Long.MAX_VALUE) {
				track = new Track(entry.getValue());
				if (!first && !reduceMotion) {
					track.alphaFrom = 0;
					track.alphaTo = 0;
					track.fade(1, now, GLIDE_NANOS, now);
				}
				tracks.put(entry.getKey(), track);
			} else if (reduceMotion) {
				track.place(entry.getValue());
			} else {
				track.glide(entry.getValue(), now, GLIDE_NANOS, now);
			}
		}
	}

	/** The window changed size: every seat jumps to its new rect, with no glide and no fade. */
	public void snap(Map<Integer, Rect> targets) {
		tracks.keySet().retainAll(targets.keySet());
		targets.forEach((seat, rect) -> {
			Track track = new Track(rect);
			tracks.put(seat, track);
		});
	}

	/** A seat leaves: it fades out where it is over {@link #FADE_OUT}, then the others glide to {@code after}. */
	public void leave(int seat, Map<Integer, Rect> after, long now, boolean reduceMotion) {
		drop(now);
		Track leaving = tracks.get(seat);
		if (reduceMotion || leaving == null) {
			tracks.remove(seat);
			retarget(after, now, true);
			return;
		}
		leaving.fade(0, now, FADE_NANOS, now);
		leaving.goneAt = now + FADE_NANOS;
		long glideStart = now + FADE_NANOS;
		for (Map.Entry<Integer, Rect> entry : after.entrySet()) {
			Track track = tracks.get(entry.getKey());
			if (track == null) {
				tracks.put(entry.getKey(), new Track(entry.getValue()));
			} else if (entry.getKey() != seat) {
				track.glide(entry.getValue(), glideStart, GLIDE_NANOS, now);
			}
		}
	}

	/** What to draw now: seats in order, player 1 first. */
	public List<ViewFrame> frames(long now) {
		drop(now);
		List<ViewFrame> frames = new ArrayList<>();
		tracks.forEach((seat, track) -> frames.add(new ViewFrame(seat, track.rect(now), track.alpha(now))));
		return frames;
	}

	public boolean moving(long now) {
		drop(now);
		return tracks.values().stream().anyMatch(t -> t.moving(now));
	}

	/** Where a seat will be once motion ends (its viewport size, for the game rendering it); empty while leaving. */
	public Optional<Rect> target(int seat) {
		Track track = tracks.get(seat);
		return track == null || track.goneAt != Long.MAX_VALUE ? Optional.empty() : Optional.of(track.to);
	}

	private void drop(long now) {
		for (Iterator<Track> it = tracks.values().iterator(); it.hasNext(); ) {
			if (now >= it.next().goneAt) {
				it.remove();
			}
		}
	}
}
