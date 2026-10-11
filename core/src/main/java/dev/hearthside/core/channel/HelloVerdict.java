package dev.hearthside.core.channel;

import dev.hearthside.core.protocol.ControlMessage;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The host's answer to a satellite's Hello.
 *
 * @param onAccepted runs with the new connection before it starts reading; {@code null} when rejected
 */
public record HelloVerdict(ControlMessage.HelloAck ack, Consumer<ControlConnection> onAccepted) {
	public HelloVerdict {
		Objects.requireNonNull(ack, "ack");
		if (ack.accepted() != (onAccepted != null)) {
			throw new IllegalArgumentException("onAccepted must be set exactly when the Hello is accepted");
		}
	}
}
