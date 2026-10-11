package dev.hearthside.core.profile;

import dev.hearthside.core.input.ControlScheme;
import java.util.Objects;
import java.util.UUID;

/**
 * A local guest player.
 *
 * @param preferredControllerGuid SDL GUID of the controller this guest last used, or {@code null}
 * @param controls this guest's controller bindings and settings
 */
public record GuestProfile(UUID id, String name, SkinRef skin, String preferredControllerGuid, ControlScheme controls) {
	public GuestProfile {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(skin, "skin");
		Objects.requireNonNull(controls, "controls");
		GuestNames.requireValid(name);
	}

	/** A guest with the Bedrock control preset. */
	public GuestProfile(UUID id, String name, SkinRef skin, String preferredControllerGuid) {
		this(id, name, skin, preferredControllerGuid, ControlScheme.BEDROCK);
	}

	public GuestProfile withController(String guid) {
		return new GuestProfile(id, name, skin, guid, controls);
	}

	public GuestProfile withControls(ControlScheme scheme) {
		return new GuestProfile(id, name, skin, preferredControllerGuid, scheme);
	}
}
