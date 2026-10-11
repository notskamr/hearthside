package dev.hearthside.core.protocol;

/** Append-only: the ordinal is the wire format. */
public enum LifecycleCommand {
	QUIT,
	PAUSE,
	RESUME,
	/** Leave the world (back to its idle title), as its own pause menu would. */
	DISCONNECT
}
