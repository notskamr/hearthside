package dev.hearthside.fabric.server;

import dev.hearthside.core.auth.GuestTokenAuthority;
import dev.hearthside.core.session.GuestRoster;
import java.util.Optional;

/** Hands the active host session's guest data to server-side login code. */
public final class HostServerBridge {
	public record Active(GuestRoster roster, GuestTokenAuthority tokens) {}

	private static volatile Active active;

	private HostServerBridge() {}

	public static void activate(Active session) {
		active = session;
	}

	public static void deactivate() {
		active = null;
	}

	public static Optional<Active> active() {
		return Optional.ofNullable(active);
	}
}
