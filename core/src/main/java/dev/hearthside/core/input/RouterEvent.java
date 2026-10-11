package dev.hearthside.core.input;

/** Something the host must act on after routing a frame of controller input. */
public sealed interface RouterEvent {
	/** An unassigned controller pressed START: add a player for it. */
	record JoinRequest(int instanceId) implements RouterEvent {}

	/** An unassigned controller pressed A while a claim was armed: give it to the host. */
	record ClaimRequest(int instanceId) implements RouterEvent {}

	/** A player's controller disconnected. */
	record PadLost(int player) implements RouterEvent {}

	/** A controller of the same model reconnected and went back to this player. */
	record PadRestored(int player) implements RouterEvent {}
}
