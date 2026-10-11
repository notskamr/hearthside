package dev.hearthside.core.input;

import java.util.Map;
import java.util.TreeMap;

/**
 * Who drives a shared screen (the lobby, world select) this frame (Spec A section 7.1: anyone can). Player 1's
 * controller while in use; otherwise the lowest-numbered seated player whose controller is in use; otherwise idle.
 */
public final class SharedScreenInput {
	/** Stick movement below this is drift, not use. */
	static final float ACTIVE_AXIS = 0.25f;

	private SharedScreenInput() {}

	/**
	 * @param frames this frame's input by player (0 = the host's own controller)
	 * @param guestMask buttons a guest's controller does not pass on (they mean something else on this screen)
	 */
	public static InputFrame drive(Map<Integer, InputFrame> frames, int guestMask) {
		InputFrame host = frames.get(0);
		if (host != null && isActive(host)) {
			return host;
		}
		for (Map.Entry<Integer, InputFrame> entry : new TreeMap<>(frames).entrySet()) {
			if (entry.getKey() == 0) {
				continue;
			}
			InputFrame f = entry.getValue();
			InputFrame masked = new InputFrame(f.sequence(), f.buttons() & ~guestMask, f.leftX(), f.leftY(), f.rightX(),
					f.rightY(), f.leftTrigger(), f.rightTrigger());
			if (isActive(masked)) {
				return masked;
			}
		}
		return host != null ? host : InputFrame.NEUTRAL;
	}

	/** Whether this controller is in use (a button held or a stick or trigger past drift). */
	public static boolean isActive(InputFrame f) {
		return f.buttons() != 0 || Math.abs(f.leftX()) > ACTIVE_AXIS || Math.abs(f.leftY()) > ACTIVE_AXIS
				|| Math.abs(f.rightX()) > ACTIVE_AXIS || Math.abs(f.rightY()) > ACTIVE_AXIS
				|| f.leftTrigger() > ACTIVE_AXIS || f.rightTrigger() > ACTIVE_AXIS;
	}
}
