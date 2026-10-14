package dev.hearthside.core.session;

import java.util.List;

/**
 * Player 1's game's view of every seat, turned into {@link PausePolicy}'s input (Spec E §3.2, plan R5): only players in
 * the world count, so a newcomer still loading or choosing a profile neither blocks the freeze nor is named as playing.
 */
public final class PauseInputs {
	private PauseInputs() {}

	/**
	 * One seat on this PC; index 0 is player 1.
	 *
	 * @param inWorld whether their game is in the world (a guest's view is live)
	 * @param paused whether a pausing screen is open in their game
	 * @param disconnected whether their controller dropped (counts as paused)
	 */
	public record Seat(int index, String name, boolean inWorld, boolean paused, boolean disconnected) {}

	/**
	 * Whether the world freezes, and who is still playing. Never while the world is open to LAN. With only one player in
	 * the world it's vanilla's single-player rule: their pause freezes it. Nobody in the world never freezes.
	 */
	public static PausePolicy.Decision decide(List<Seat> seats, boolean lanOpen) {
		List<PausePolicy.LocalPlayer> players = seats.stream()
				.filter(Seat::inWorld)
				.map(s -> new PausePolicy.LocalPlayer(s.index(), s.paused(), s.disconnected(), s.name()))
				.toList();
		PausePolicy.Decision decision = PausePolicy.decide(players, lanOpen);
		if (players.size() == 1 && !lanOpen) {
			// PausePolicy leaves one player to Minecraft; here that player is player 1 with every guest still loading.
			return new PausePolicy.Decision(decision.stillPlaying().isEmpty(), decision.stillPlaying());
		}
		return decision;
	}
}
