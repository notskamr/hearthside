package dev.hearthside.core.session;

import java.util.List;

/** The pause menu's status line (Spec E §3.2): that the world is frozen, or who is still playing in it. */
public final class PauseLine {
	private PauseLine() {}

	public static String of(PausePolicy.Decision decision) {
		if (decision.freeze()) {
			return "Everyone's paused — the world is frozen";
		}
		List<String> names = decision.stillPlaying();
		return switch (names.size()) {
			// Nobody here is playing, yet the world runs: someone from another machine, or the world is open to LAN.
			case 0 -> "The world keeps going";
			case 1 -> "The world keeps going — " + names.getFirst() + " is still playing";
			default -> "The world keeps going — " + String.join(", ", names.subList(0, names.size() - 1)) + " and "
					+ names.getLast() + " are still playing";
		};
	}
}
