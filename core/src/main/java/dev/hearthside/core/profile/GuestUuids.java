package dev.hearthside.core.profile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Derives the in-game UUID of a guest from its profile id.
 *
 * <p>The result is a name-based (version 3) UUID, so it can never equal a Mojang account UUID
 * (version 4). The {@code hearthside:guest:} prefix keeps it distinct from vanilla offline-mode
 * UUIDs, which hash {@code "OfflinePlayer:" + name}.
 */
public final class GuestUuids {
	private static final String NAMESPACE = "hearthside:guest:";

	private GuestUuids() {}

	public static UUID forProfile(UUID profileId) {
		return UUID.nameUUIDFromBytes((NAMESPACE + profileId).getBytes(StandardCharsets.UTF_8));
	}
}
