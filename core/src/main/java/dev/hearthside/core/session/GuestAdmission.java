package dev.hearthside.core.session;

import dev.hearthside.core.auth.GuestTokenAuthority;
import java.util.Locale;
import java.util.UUID;

/** Login decisions for the host's integrated server while a Hearthside session is open. */
public final class GuestAdmission {
	/** What to do with a login's Hello. */
	public enum Decision {
		/** A local guest: skip Mojang authentication, then require a valid one-time token. */
		GUEST,
		/** Leave it to vanilla, unchanged. */
		VANILLA,
		/** Disconnect it. */
		REFUSE
	}

	/**
	 * @param loopback the connection comes from this machine over TCP
	 * @param memoryConnection the host's own in-process connection
	 * @param firstHello this is the connection's first Hello packet
	 * @param lanOpen the world is opened to LAN
	 */
	public record LoginAttempt(String name, boolean loopback, boolean memoryConnection, boolean firstHello, boolean lanOpen) {}

	private GuestAdmission() {}

	public static Decision decide(LoginAttempt attempt, GuestRoster roster) {
		// Repeated Hellos and the host's own connection go to vanilla, whose state checks handle them.
		if (attempt.memoryConnection() || !attempt.firstHello()) {
			return Decision.VANILLA;
		}
		if (attempt.loopback() && roster.expectedUuidForName(attempt.name()).isPresent()) {
			return Decision.GUEST;
		}
		// Vanilla hands the world owner's profile, unauthenticated, to any connection using the owner's name.
		if (attempt.name().toLowerCase(Locale.ROOT).equals(roster.hostName().toLowerCase(Locale.ROOT))) {
			return Decision.REFUSE;
		}
		// Hearthside's loopback listener is for guests; anyone else needs the world opened to LAN.
		return attempt.lanOpen() ? Decision.VANILLA : Decision.REFUSE;
	}

	/** True only when {@code token} redeems to exactly {@code expectedGuest}. Redeeming consumes it. */
	public static boolean tokenMatches(String token, UUID expectedGuest, GuestTokenAuthority authority) {
		return authority.redeem(token).filter(expectedGuest::equals).isPresent();
	}
}
