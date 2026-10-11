package dev.hearthside.fabric.client.input;

/**
 * Whether a controller is driving this client in the world right now. Vanilla ties some world input to a grabbed
 * mouse, which needs window focus; a split-screen window usually has no focus, so a controller stands in for it.
 */
public final class PadPlay {
	private static volatile boolean driving;

	private PadPlay() {}

	static void set(boolean nowDriving) {
		driving = nowDriving;
	}

	/** Read by {@code ContinueAttackMixin}. */
	public static boolean driving() {
		return driving;
	}
}
