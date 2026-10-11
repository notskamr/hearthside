package dev.hearthside.fabric.client.input;

import java.util.Optional;
import net.minecraft.world.phys.Vec2;

/** The analog move vector from this client's controller, read by {@code KeyboardInputMixin}. */
public final class PadMovement {
	private static volatile Vec2 current;

	private PadMovement() {}

	/** @param x positive = left, @param y positive = forward (Minecraft's {@code moveVector}) */
	public static void set(float x, float y) {
		current = new Vec2(x, y);
	}

	public static void clear() {
		current = null;
	}

	public static Optional<Vec2> current() {
		return Optional.ofNullable(current);
	}
}
