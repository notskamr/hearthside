package dev.hearthside.core.profile;

import dev.hearthside.core.input.ControlScheme;
import java.util.Objects;

/**
 * Player 1's own settings: their identity and skin come from their Microsoft account.
 *
 * @param preferredControllerGuid the model GUID of the controller player 1 last took in the lobby, or {@code null}
 */
public record HostProfile(ControlScheme controls, PersonalSettings settings, int color, String preferredControllerGuid) {
	public static final HostProfile DEFAULTS = new HostProfile(ControlScheme.BEDROCK, PersonalSettings.DEFAULTS);

	public HostProfile {
		Objects.requireNonNull(controls, "controls");
		Objects.requireNonNull(settings, "settings");
		color &= 0xFFFFFF;
	}

	public HostProfile(ControlScheme controls, PersonalSettings settings, int color) {
		this(controls, settings, color, null);
	}

	/** Player 1 in their default colour, blue. */
	public HostProfile(ControlScheme controls, PersonalSettings settings) {
		this(controls, settings, PlayerColors.DEFAULTS[0]);
	}

	public HostProfile withController(String guid) {
		return new HostProfile(controls, settings, color, guid);
	}
}
