package dev.hearthside.core.profile;

import dev.hearthside.core.input.ControlScheme;
import java.util.Objects;
import java.util.UUID;

/**
 * A local guest player.
 *
 * @param preferredControllerGuid SDL GUID of the controller this guest last used, or {@code null}
 * @param controls this guest's controller bindings and settings
 * @param color this player's colour, 0xRRGGBB
 * @param settings this player's own settings
 */
public record GuestProfile(UUID id, String name, SkinRef skin, String preferredControllerGuid, ControlScheme controls,
		int color, PersonalSettings settings) {
	public GuestProfile {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(skin, "skin");
		Objects.requireNonNull(controls, "controls");
		Objects.requireNonNull(settings, "settings");
		GuestNames.requireValid(name);
		color &= 0xFFFFFF;
	}

	public GuestProfile(UUID id, String name, SkinRef skin, String preferredControllerGuid, ControlScheme controls) {
		this(id, name, skin, preferredControllerGuid, controls, PlayerColors.DEFAULTS[0], PersonalSettings.DEFAULTS);
	}

	/** A guest with the Bedrock control preset. */
	public GuestProfile(UUID id, String name, SkinRef skin, String preferredControllerGuid) {
		this(id, name, skin, preferredControllerGuid, ControlScheme.BEDROCK);
	}

	public GuestProfile withController(String guid) {
		return new GuestProfile(id, name, skin, guid, controls, color, settings);
	}

	public GuestProfile withControls(ControlScheme scheme) {
		return new GuestProfile(id, name, skin, preferredControllerGuid, scheme, color, settings);
	}

	public GuestProfile withColor(int newColor) {
		return new GuestProfile(id, name, skin, preferredControllerGuid, controls, newColor, settings);
	}

	public GuestProfile withSettings(PersonalSettings newSettings) {
		return new GuestProfile(id, name, skin, preferredControllerGuid, controls, color, newSettings);
	}

	public GuestProfile withSkin(SkinRef newSkin) {
		return new GuestProfile(id, name, newSkin, preferredControllerGuid, controls, color, settings);
	}

	public GuestProfile withName(String newName) {
		return new GuestProfile(id, newName, skin, preferredControllerGuid, controls, color, settings);
	}
}
