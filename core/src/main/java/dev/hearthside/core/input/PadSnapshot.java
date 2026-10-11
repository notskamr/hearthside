package dev.hearthside.core.input;

import java.util.Objects;

/**
 * One connected controller as seen this frame.
 *
 * @param instanceId SDL's id for this connection; changes on every replug
 * @param guid identifies the controller model; identical controllers share it
 */
public record PadSnapshot(int instanceId, String guid, String name, PadStyle style, InputFrame frame) {
	public PadSnapshot {
		if (instanceId <= 0) {
			throw new IllegalArgumentException("instanceId must be positive, was " + instanceId);
		}
		Objects.requireNonNull(guid, "guid");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(style, "style");
		Objects.requireNonNull(frame, "frame");
	}
}
