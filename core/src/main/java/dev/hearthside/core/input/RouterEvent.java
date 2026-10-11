package dev.hearthside.core.input;

/** Something the host must act on after routing a frame of controller input. */
public sealed interface RouterEvent {
	/** An unassigned controller pressed A or B: input for the join flow. */
	record UnassignedPress(int instanceId, PadButton button) implements RouterEvent {}

	/** An unassigned controller pressed A while a claim was armed: give it to the host. */
	record ClaimRequest(int instanceId) implements RouterEvent {}

	/** A player's controller disconnected. */
	record PadLost(int player) implements RouterEvent {}

	/**
	 * A controller of the same model reconnected and went back to this player; SDL gives it a new id, so
	 * {@code instanceId} is the controller's id from now on.
	 */
	record PadRestored(int player, int instanceId) implements RouterEvent {}
}
