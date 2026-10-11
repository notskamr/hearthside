package dev.hearthside.core.session;

import dev.hearthside.core.auth.GuestTokenAuthority;
import java.util.UUID;

/** Login decisions for the host's integrated server. */
public final class GuestAdmission {
	/** How a connecting player is authenticated. */
	public enum Path {
		/** A local guest: skip Mojang authentication, then require a valid one-time token. */
		GUEST,
		/** Anyone else: vanilla behaviour, unchanged. */
		VANILLA
	}

	private GuestAdmission() {}

	/** Only a loopback connection using a name the roster expects takes the guest path. */
	public static Path pathFor(String requestedName, boolean loopback, GuestRoster roster) {
		return loopback && roster.expectedUuidForName(requestedName).isPresent() ? Path.GUEST : Path.VANILLA;
	}

	/** True only when {@code token} redeems to exactly {@code expectedGuest}. Redeeming consumes it. */
	public static boolean tokenMatches(String token, UUID expectedGuest, GuestTokenAuthority authority) {
		return authority.redeem(token).filter(expectedGuest::equals).isPresent();
	}
}
