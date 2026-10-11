package dev.hearthside.fabric.server;

import java.util.UUID;

/** Added to the server login listener by {@code GuestLoginMixin}: the guest this login claims to be, if any. */
public interface GuestLoginState {
	UUID hearthside$guestUuid();

	void hearthside$setGuestUuid(UUID id);
}
