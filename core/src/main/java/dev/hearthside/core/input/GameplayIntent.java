package dev.hearthside.core.input;

import java.util.Set;

/**
 * What a player wants to do in the world this frame.
 *
 * @param moveX positive = strafe left (Minecraft's {@code moveVector.x})
 * @param moveY positive = forward
 * @param pitchDegrees positive = look down
 * @param held actions whose control is down now
 * @param pressed actions whose control went down this frame
 */
public record GameplayIntent(float moveX, float moveY, float yawDegrees, float pitchDegrees, Set<Action> held,
		Set<Action> pressed, int hotbarDelta) {
	public GameplayIntent {
		held = Set.copyOf(held);
		pressed = Set.copyOf(pressed);
	}
}
