package dev.hearthside.core.compositor;

import java.util.Objects;
import java.util.Optional;

/** The transport chosen for a guest's frames and, for a copy, why. */
public record TransportDecision(Transport transport, Optional<CopyReason> reason) {
	private static final TransportDecision SHARED = new TransportDecision(Transport.GPU_SHARED, Optional.empty());

	public TransportDecision {
		Objects.requireNonNull(transport, "transport");
		Objects.requireNonNull(reason, "reason");
		if ((transport == Transport.COPY) != reason.isPresent()) {
			throw new IllegalArgumentException("A copy needs a reason, and only a copy has one: " + transport + " " + reason);
		}
	}

	public static TransportDecision shared() {
		return SHARED;
	}

	public static TransportDecision copy(CopyReason reason) {
		return new TransportDecision(Transport.COPY, Optional.of(reason));
	}
}
