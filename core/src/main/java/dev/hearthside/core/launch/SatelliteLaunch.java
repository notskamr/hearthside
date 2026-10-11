package dev.hearthside.core.launch;

import java.nio.file.Path;
import java.util.Objects;

/**
 * What differs between satellites.
 *
 * @param playerIndex 0-based player slot (player 2 is index 1)
 * @param controlPort loopback port of the host's control-channel listener
 * @param launchSecret proves to the host that this satellite was launched by it; passed by environment
 * @param presentation how this satellite's frames reach the screen
 */
public record SatelliteLaunch(int playerIndex, int controlPort, Path gameDir, String guestName, String launchSecret,
		Presentation presentation) {
	/** How a satellite's view reaches the screen. */
	public enum Presentation {
		/** Hidden window; frames shared with the host's compositor through GPU memory where possible. */
		COMPOSITED,
		/** Hidden window; frames copied through shared memory (fallback, or forced for testing). */
		COMPOSITED_COPY,
		/** A real window tiled next to the host's (when frame sharing can't be set up). */
		TILED
	}

	public SatelliteLaunch {
		Objects.requireNonNull(presentation, "presentation");
	}

	public SatelliteLaunch(int playerIndex, int controlPort, Path gameDir, String guestName, String launchSecret) {
		this(playerIndex, controlPort, gameDir, guestName, launchSecret, Presentation.COMPOSITED);
	}
}
