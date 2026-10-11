package dev.hearthside.core.session;

import dev.hearthside.core.profile.GuestProfile;
import dev.hearthside.core.profile.GuestUuids;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Which guest occupies which player slot (1–3) in the host's session. A guest may never share a
 * name with the host: vanilla login would treat that name as the world's owner.
 */
public final class GuestRoster {
	public static final int MIN_GUEST_INDEX = 1;
	public static final int MAX_GUEST_INDEX = 3;

	private final String hostName;
	private final TreeMap<Integer, GuestProfile> slots = new TreeMap<>();

	public GuestRoster(String hostName) {
		this.hostName = Objects.requireNonNull(hostName, "hostName");
	}

	public synchronized void register(int playerIndex, GuestProfile profile) {
		if (playerIndex < MIN_GUEST_INDEX || playerIndex > MAX_GUEST_INDEX) {
			throw new IllegalArgumentException("Guest slot must be " + MIN_GUEST_INDEX + "-" + MAX_GUEST_INDEX + ", was " + playerIndex);
		}
		if (slots.containsKey(playerIndex)) {
			throw new IllegalArgumentException("Player slot " + playerIndex + " is already taken");
		}
		if (sameName(profile.name(), hostName)) {
			throw new IllegalArgumentException("A guest can't use the host's name (" + hostName + ")");
		}
		for (GuestProfile other : slots.values()) {
			if (sameName(other.name(), profile.name())) {
				throw new IllegalArgumentException(profile.name() + " is already playing");
			}
			if (other.id().equals(profile.id())) {
				throw new IllegalArgumentException("That profile is already playing as " + other.name());
			}
		}
		slots.put(playerIndex, profile);
	}

	public synchronized Optional<GuestProfile> remove(int playerIndex) {
		return Optional.ofNullable(slots.remove(playerIndex));
	}

	public synchronized Optional<GuestProfile> byIndex(int playerIndex) {
		return Optional.ofNullable(slots.get(playerIndex));
	}

	public synchronized Optional<UUID> expectedUuidForName(String name) {
		return slots.values().stream()
				.filter(p -> sameName(p.name(), name))
				.findFirst()
				.map(p -> GuestUuids.forProfile(p.id()));
	}

	public synchronized OptionalInt freeIndex() {
		for (int i = MIN_GUEST_INDEX; i <= MAX_GUEST_INDEX; i++) {
			if (!slots.containsKey(i)) {
				return OptionalInt.of(i);
			}
		}
		return OptionalInt.empty();
	}

	private static boolean sameName(String a, String b) {
		return a != null && b != null && a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
	}
}
