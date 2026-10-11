package dev.hearthside.fabric.client.menu;

import dev.hearthside.core.input.InputFrame;

/**
 * A Hearthside screen that takes controllers itself instead of through the shared pointer: the world picker, world
 * creation, the controls screen and Manage Profiles. On shared screens the party's driver's frames come here.
 */
public interface PadScreen {
	/**
	 * @param player who is driving (0 = player 1)
	 * @return whether this frame was used
	 */
	boolean padInput(int player, InputFrame frame, long nanos);
}
