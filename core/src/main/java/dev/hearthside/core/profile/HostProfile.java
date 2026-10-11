package dev.hearthside.core.profile;

import dev.hearthside.core.input.ControlScheme;
import java.util.Objects;

/** Player 1's own settings: their identity and skin come from their Microsoft account. */
public record HostProfile(ControlScheme controls, PersonalSettings settings) {
	public static final HostProfile DEFAULTS = new HostProfile(ControlScheme.BEDROCK, PersonalSettings.DEFAULTS);

	public HostProfile {
		Objects.requireNonNull(controls, "controls");
		Objects.requireNonNull(settings, "settings");
	}
}
