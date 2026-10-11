package dev.hearthside.core.profile;

import dev.hearthside.core.input.ControlScheme;
import java.util.List;
import java.util.Objects;

/** Everything in the profiles file: the guests, and the host's own controller scheme. */
public record ProfileFile(List<GuestProfile> profiles, ControlScheme hostControls) {
	public static final ProfileFile EMPTY = new ProfileFile(List.of(), ControlScheme.BEDROCK);

	public ProfileFile {
		profiles = List.copyOf(profiles);
		Objects.requireNonNull(hostControls, "hostControls");
	}
}
