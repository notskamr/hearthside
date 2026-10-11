package dev.hearthside.core.profile;

import dev.hearthside.core.input.ControlScheme;
import java.util.List;
import java.util.Objects;

/** Everything in the profiles file: the guests, and player 1's own profile. */
public record ProfileFile(List<GuestProfile> profiles, HostProfile host) {
	public static final ProfileFile EMPTY = new ProfileFile(List.of(), HostProfile.DEFAULTS);

	public ProfileFile {
		profiles = List.copyOf(profiles);
		Objects.requireNonNull(host, "host");
	}

	/** A file whose host profile has these controls and default settings. */
	public ProfileFile(List<GuestProfile> profiles, ControlScheme hostControls) {
		this(profiles, new HostProfile(hostControls, PersonalSettings.DEFAULTS));
	}

	public ControlScheme hostControls() {
		return host.controls();
	}
}
