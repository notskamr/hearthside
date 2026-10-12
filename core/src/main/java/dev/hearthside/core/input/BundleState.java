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
	HOLDING_BUNDLE_OVER_EMPTY;

	/**
	 * Where a bundle is, given the cursor and the slot under the pointer. Holding a bundle comes first.
	 *
	 * @param realSlot the slot is a real container's, so clicks on it reach the bundle (Creative's item picker handles
	 *        its own and never does)
	 */
	public static BundleState of(boolean carriedBundle, boolean carriedEmpty, boolean hoveredBundle, boolean hoveredEmpty,
			boolean realSlot) {
		if (!realSlot) {
			return NONE;
		}
		if (carriedBundle) {
			return hoveredEmpty ? HOLDING_BUNDLE_OVER_EMPTY : HOLDING_BUNDLE_OVER_STACK;
		}
		if (hoveredBundle) {
			return carriedEmpty ? OVER_BUNDLE_EMPTY_HAND : OVER_BUNDLE_HOLDING;
		}
		return NONE;
	}
}
