package dev.hearthside.core.session;

/**
 * Who may act on screens the whole party shares (Spec A §7.1): anyone may drive them, unless the "player 1 only"
 * setting is on, and only player 1 may do things that can't be undone or end the session.
 */
public final class SharedControl {
	public enum SharedAction {
		NAVIGATE, SELECT_WORLD, CREATE_WORLD, CONTINUE,
		/** Player 1 only. */
		DELETE_WORLD,
		/** Player 1 only. */
		REMOVE_PLAYER,
		/** Player 1 only. */
		END_SESSION;

		boolean destructive() {
			return this == DELETE_WORLD || this == REMOVE_PLAYER || this == END_SESSION;
		}
	}

	private final boolean hostOnly;

	/** @param hostOnly the "only player 1 drives shared screens" setting */
	public SharedControl(boolean hostOnly) {
		this.hostOnly = hostOnly;
	}

	/** @param playerIndex 0 is player 1 */
	public boolean canDo(int playerIndex, SharedAction action) {
		if (playerIndex == 0) {
			return true;
		}
		return !hostOnly && !action.destructive();
	}
}
