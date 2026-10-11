package dev.hearthside.fabric.client.input;

/**
 * Marks mouse events Hearthside makes itself (the controller's virtual cursor). They are already in the game's own
 * coordinates, unlike real OS mouse events, which the host remaps into its viewport. Render thread only.
 */
public final class SyntheticMouse {
	private static int depth;

	private SyntheticMouse() {}

	public static boolean active() {
		return depth > 0;
	}

	public static void run(Runnable event) {
		depth++;
		try {
			event.run();
		} finally {
			depth--;
		}
	}
}
