package dev.hearthside.core.input;

import java.time.Duration;
import java.util.Optional;

/**
 * Drop on a controller, as on Bedrock: pressing throws one item at once; still holding at {@link #HOLD} throws the rest
 * of the stack, once per hold. Feed it the Drop control's state every frame. Not thread-safe.
 */
public final class DropGesture {
	public static final Duration HOLD = Duration.ofMillis(500);

	public enum Drop {
		/** One item from the selected stack. */
		ONE,
		/** The rest of the selected stack. */
		STACK
	}

	private boolean down;
	private long pressedAt;
	private boolean stackThrown;

	/** The control's state at {@code nanos}; what to throw now, if anything. */
	public Optional<Drop> step(boolean isDown, long nanos) {
		if (!isDown) {
			down = false;
			return Optional.empty();
		}
		if (!down) {
			down = true;
			pressedAt = nanos;
			stackThrown = false;
			return Optional.of(Drop.ONE);
		}
		if (!stackThrown && nanos - pressedAt >= HOLD.toNanos()) {
			stackThrown = true;
			return Optional.of(Drop.STACK);
		}
		return Optional.empty();
	}
}
