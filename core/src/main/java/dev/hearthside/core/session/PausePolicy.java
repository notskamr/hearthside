package dev.hearthside.core.session;

import java.util.List;

/**
 * When the world freezes (Spec A §7.2): pause menus are personal, and the world only stops when every local player
 * has paused (a dropped controller counts as paused) and nobody from another machine is connected.
 */
public final class PausePolicy {
	private PausePolicy() {}

	/** One player on this PC; index 0 is player 1. */
	public record LocalPlayer(int index, boolean paused, boolean disconnected, String name) {}

	/** @param stillPlaying names of players not paused, for "Paused — waiting for …" */
	public record Decision(boolean freeze, List<String> stillPlaying) {
		public Decision {
			stillPlaying = List.copyOf(stillPlaying);
		}
	}

	public static Decision decide(List<LocalPlayer> players, boolean remotePlayersConnected) {
		List<String> stillPlaying = players.stream()
				.filter(p -> !p.paused() && !p.disconnected())
				.map(LocalPlayer::name)
				.toList();
		// With one player Minecraft's own pause rules apply; we only add the "everyone paused" freeze.
		boolean freeze = players.size() >= 2 && stillPlaying.isEmpty() && !remotePlayersConnected;
		return new Decision(freeze, stillPlaying);
	}
}
