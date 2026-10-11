package dev.hearthside.core.profile;

import java.util.Collection;

/** The colours that mark each player: their card, view border, name tag, cursor and highlights. */
public final class PlayerColors {
	/** Blue, orange-red, green, violet (0xRRGGBB). */
	public static final int[] DEFAULTS = {0x3f7fd0, 0xd0603f, 0x3fa060, 0x9a5fd0};

	private PlayerColors() {}

	/** The first default colour nobody has taken, or the first default if all are taken. */
	public static int firstFree(Collection<Integer> taken) {
		for (int color : DEFAULTS) {
			if (!taken.contains(color)) {
				return color;
			}
		}
		return DEFAULTS[0];
	}
}
