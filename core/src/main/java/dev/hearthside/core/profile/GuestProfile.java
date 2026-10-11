package dev.hearthside.core.profile;

import java.util.Objects;
import java.util.UUID;

/**
 * A local guest player.
 *
 * @param preferredControllerGuid SDL GUID of the controller this guest last used, or {@code null}
 */
public record GuestProfile(UUID id, String name, SkinRef skin, String preferredControllerGuid) {
	public GuestProfile {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(skin, "skin");
		GuestNames.requireValid(name);
	}

	public GuestProfile withController(String guid) {
		return new GuestProfile(id, name, skin, guid);
	}
}
