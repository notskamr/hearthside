package dev.hearthside.core.session;

/** What a player's view shows (Spec A sections 3.1, 3.2, 3.3). */
public enum ViewPhase {
	/** Their card with the profile carousel, inside their new view. */
	PICKING,
	/** Their card saying "Joining…", until their game is in the world. */
	JOINING,
	/** Their game's own frames. */
	LIVE,
	/** Their game restarted: the card over their last frame until it is back. */
	RECONNECTING,
	/** Its game kept crashing and won't be restarted: the card says it couldn't start, over its last frame. */
	FAILED
}
