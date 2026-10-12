package dev.hearthside.core.input;

/** Where a bundle is in a container screen, for its hints: under the pointer, on the cursor, or neither. */
public enum BundleState {
	NONE,
	/** The pointer is over a bundle and nothing is on the cursor. */
	OVER_BUNDLE_EMPTY_HAND,
	/** The pointer is over a bundle and an item is on the cursor. */
	OVER_BUNDLE_HOLDING,
	/** A bundle is on the cursor, over a slot holding a stack. */
	HOLDING_BUNDLE_OVER_STACK,
	/** A bundle is on the cursor, over an empty slot. */
	HOLDING_BUNDLE_OVER_EMPTY
}
