package dev.hearthside.core.launch;

import dev.hearthside.core.config.Preset;
import dev.hearthside.core.config.SessionTuning;
import dev.hearthside.core.config.SplitScreenConfig;
import java.nio.file.Path;
import java.util.Objects;

/**
 * What differs between satellites.
 *
 * @param playerIndex 0-based player slot (player 2 is index 1)
 * @param controlPort loopback port of the host's control-channel listener
 * @param launchSecret proves to the host that this satellite was launched by it; passed by environment
 * @param presentation how this satellite's frames reach the screen
 * @param graphicsBackend the graphics API the satellite renders with: the host's own
 * @param heapMb the satellite's maximum heap in MB, from the host's split-screen config
 * @param renderDistanceCap the most chunks the satellite renders this run; {@code 0} means its own setting
 */
public record SatelliteLaunch(int playerIndex, int controlPort, Path gameDir, String guestName, String launchSecret,
		Presentation presentation, GraphicsBackend graphicsBackend, int heapMb, int renderDistanceCap) {
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
		Objects.requireNonNull(graphicsBackend, "graphicsBackend");
	}

	/** The default heap, and Balanced's render-distance cap (8), which every guest had before presets. */
	public SatelliteLaunch(int playerIndex, int controlPort, Path gameDir, String guestName, String launchSecret,
			Presentation presentation, GraphicsBackend graphicsBackend) {
		this(playerIndex, controlPort, gameDir, guestName, launchSecret, presentation, graphicsBackend,
				SplitScreenConfig.DEFAULTS.guestMemoryMb(),
				SessionTuning.of(Preset.BALANCED, SplitScreenConfig.DEFAULTS).renderDistanceCap());
	}

	public SatelliteLaunch(int playerIndex, int controlPort, Path gameDir, String guestName, String launchSecret,
			Presentation presentation) {
		this(playerIndex, controlPort, gameDir, guestName, launchSecret, presentation, GraphicsBackend.OPENGL);
	}

	public SatelliteLaunch(int playerIndex, int controlPort, Path gameDir, String guestName, String launchSecret) {
		this(playerIndex, controlPort, gameDir, guestName, launchSecret, Presentation.COMPOSITED);
	}
}
