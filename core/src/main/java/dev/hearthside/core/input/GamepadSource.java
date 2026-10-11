package dev.hearthside.core.input;

import java.util.List;

/** Reads every connected controller. Called once per rendered frame on the render thread. */
public interface GamepadSource {
	List<PadSnapshot> poll();

	/**
	 * Marks a controller as a player's where the hardware can show it: a light bar in the player's colour, player-number
	 * lights. Best effort; controllers without lights ignore it.
	 *
	 * @param player 0-based player index, or -1 for nobody's
	 * @param rgb the player's colour, 0xRRGGBB
	 */
	default void indicate(int instanceId, int player, int rgb) {}
}
