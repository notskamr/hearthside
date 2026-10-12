package dev.hearthside.fabric.client.input;

/**
 * When player 1 last used the OS mouse (moved, clicked or scrolled it), so their controller's pointer and the mouse can
 * take turns on a screen: whichever was used last drives it. Hearthside's own synthetic events never count, nor does
 * the motion event SDL sends when Minecraft itself warps the cursor (grabbing it for play, releasing it for a screen).
 * Render thread.
 */
public final class RealMouse {
	/** How soon after a warp its echo arrives, at most. */
	private static final long WARP_ECHO_NANOS = 250_000_000L;

	private static volatile long lastUsed;
	private static double warpX = Double.NaN, warpY = Double.NaN;
	private static long warpedAt;

	private RealMouse() {}

	/** Minecraft moved the OS cursor to (x, y), window coordinates. */
	public static void warped(double x, double y) {
		warpX = x;
		warpY = y;
		warpedAt = System.nanoTime();
	}

	/** An OS mouse move arrived at (x, y), window coordinates. */
	public static void moved(double x, double y) {
		long now = System.nanoTime();
		if (now - warpedAt < WARP_ECHO_NANOS && Math.abs(x - warpX) < 1 && Math.abs(y - warpY) < 1) {
			warpX = Double.NaN; // the warp's own echo, once
			return;
		}
		lastUsed = now;
	}

	/** An OS mouse button or wheel was used. */
	public static void used() {
		lastUsed = System.nanoTime();
	}

	/** When the OS mouse was last used ({@link System#nanoTime()}), or 0. */
	public static long lastUsed() {
		return lastUsed;
	}
}
