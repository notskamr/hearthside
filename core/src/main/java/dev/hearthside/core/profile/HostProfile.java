package dev.hearthside.core.profile;

import dev.hearthside.core.input.ControlScheme;
import java.util.Objects;

/** Player 1's own settings: their identity and skin come from their Microsoft account. */
public record HostProfile(ControlScheme controls, PersonalSettings settings, int color) {
	public static final HostProfile DEFAULTS = new HostProfile(ControlScheme.BEDROCK, PersonalSettings.DEFAULTS);

	public HostProfile {
		Objects.requireNonNull(controls, "controls");
		Objects.requireNonNull(settings, "settings");
		color &= 0xFFFFFF;
	}

	/** Player 1 in their default colour, blue. */
	public HostProfile(ControlScheme controls, PersonalSettings settings) {
		this(controls, settings, PlayerColors.DEFAULTS[0]);
	}
}
