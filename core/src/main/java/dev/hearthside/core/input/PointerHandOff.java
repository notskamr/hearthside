package dev.hearthside.core.input;

import java.time.Duration;
import java.util.ArrayDeque;

/**
 * Who drives player 1's screens: their controller's pointer or the real mouse, whichever was used last. A real mouse
 * move takes over only once it has gone {@value #TAKE_OVER_PIXELS} pixels within {@link #TAKE_OVER_WINDOW}, so a jittery
 * sensor or a bumped desk doesn't take a screen from the player on the couch. A real click always takes over, and has
 * to be re-aimed first if the pointer was driving: the game's mouse position is the pointer's, not the real arrow's.
 * The controller takes back on its next pointer input.
 *
 * <p>Times are {@link System#nanoTime()}; distances are window pixels. Not thread-safe.
 */
public final class PointerHandOff {
	/** How far a real mouse moves (net) within {@link #TAKE_OVER_WINDOW} to take over: further than this. */
	public static final int TAKE_OVER_PIXELS = 4;
	public static final Duration TAKE_OVER_WINDOW = Duration.ofMillis(250);

	private static final long WINDOW_NANOS = TAKE_OVER_WINDOW.toNanos();

	public enum Owner { PAD, MOUSE }

	private record Move(long at, double dx, double dy) {}

	private Owner owner = Owner.PAD;
	/** The mouse's moves within the window while the controller drives, and their sum. */
	private final ArrayDeque<Move> moves = new ArrayDeque<>();
	private double sumX, sumY;

	/** The controller moved the pointer or clicked with it: it drives. */
	public void padUsed(long now) {
		owner = Owner.PAD;
		forgetMoves();
	}

	/** The real mouse moved by (dx, dy). */
	public void mouseMoved(double dx, double dy, long now) {
		if (owner == Owner.MOUSE) {
			return;
		}
		moves.addLast(new Move(now, dx, dy));
		sumX += dx;
		sumY += dy;
		while (now - moves.getFirst().at() > WINDOW_NANOS) {
			Move old = moves.removeFirst();
			sumX -= old.dx();
			sumY -= old.dy();
		}
		if (Math.hypot(sumX, sumY) > TAKE_OVER_PIXELS) {
			owner = Owner.MOUSE;
			forgetMoves();
		}
	}

	/**
	 * A real mouse button (or the wheel) was used: the mouse drives. Returns whether the pointer was driving until now,
	 * so the click must first be moved to where the real arrow is.
	 */
	public boolean mouseClicked(long now) {
		boolean reAim = owner == Owner.PAD;
		owner = Owner.MOUSE;
		forgetMoves();
		return reAim;
	}

	/** The controller is gone (unplugged): the mouse drives. */
	public void padGone() {
		owner = Owner.MOUSE;
		forgetMoves();
	}

	public Owner owner() {
		return owner;
	}

	private void forgetMoves() {
		moves.clear();
		sumX = 0;
		sumY = 0;
	}
}
