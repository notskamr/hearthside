package dev.hearthside.core.input;

import java.util.Set;

/**
 * What a player wants to do in a menu this frame.
 *
 * @param down menu actions whose control went down this frame
 * @param up menu actions whose control was released this frame
 * @param hopX D-pad hop: -1 left, 1 right
 * @param hopY D-pad hop: -1 up, 1 down
 * @param scroll positive = scroll up
 */
public record MenuIntent(float cursorDx, float cursorDy, Set<MenuAction> down, Set<MenuAction> up, int hopX, int hopY,
		float scroll) {
	public MenuIntent {
		down = Set.copyOf(down);
		up = Set.copyOf(up);
	}
}
