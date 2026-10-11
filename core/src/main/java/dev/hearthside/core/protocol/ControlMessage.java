package dev.hearthside.core.protocol;

import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.layout.Rect;
import java.util.Objects;

/** Messages on the host↔satellite control channel. Type ids are listed on each record. */
public sealed interface ControlMessage {
	/** Type 1, satellite → host: first message after connecting. */
	record Hello(int protocolVersion, int playerIndex, String launchSecret) implements ControlMessage {
		public Hello {
			Objects.requireNonNull(launchSecret, "launchSecret");
		}
	}

	/** Type 2, host → satellite. {@code reason} and {@code loginToken} are empty when not applicable. */
	record HelloAck(int protocolVersion, boolean accepted, String reason, String loginToken) implements ControlMessage {
		public HelloAck {
			Objects.requireNonNull(reason, "reason");
			Objects.requireNonNull(loginToken, "loginToken");
		}
	}

	/** Type 3, host → satellite: the latest controller state for this satellite's player. */
	record Input(InputFrame frame) implements ControlMessage {
		public Input {
			Objects.requireNonNull(frame, "frame");
		}
	}

	/** Type 4, host → satellite: where this satellite's view goes on screen. */
	record SetViewport(Rect rect) implements ControlMessage {
		public SetViewport {
			Objects.requireNonNull(rect, "rect");
		}
	}

	/** Type 5, host → satellite. */
	record Lifecycle(LifecycleCommand command) implements ControlMessage {
		public Lifecycle {
			Objects.requireNonNull(command, "command");
		}
	}

	/** A message whose type this version does not know; produced only by decoding, never written. */
	record Unknown(int typeId) implements ControlMessage {}
}
