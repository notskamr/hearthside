package dev.hearthside.core.launch;

import java.nio.file.Path;

/**
 * What differs between satellites.
 *
 * @param playerIndex 0-based player slot (player 2 is index 1)
 * @param controlPort loopback port of the host's control-channel listener
 * @param launchSecret proves to the host that this satellite was launched by it; passed by environment
 */
public record SatelliteLaunch(int playerIndex, int controlPort, Path gameDir, String guestName, String launchSecret) {}
