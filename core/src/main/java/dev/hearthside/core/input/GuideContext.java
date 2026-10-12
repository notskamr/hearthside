package dev.hearthside.core.input;

/**
 * What the player is doing, for choosing which button hints to show.
 *
 * @param holdingSlotItem an item is on the cursor in a container screen
 * @param bundle where a bundle is, in a container screen
 */
public record GuideContext(boolean inContainerScreen, boolean inOtherScreen, boolean lookingAtBlock,
		boolean lookingAtEntity, boolean holdingUsable, boolean holdingSlotItem, boolean flying, BundleState bundle) {
	public GuideContext {
		bundle = bundle == null ? BundleState.NONE : bundle;
	}

	public GuideContext(boolean inContainerScreen, boolean inOtherScreen, boolean lookingAtBlock, boolean lookingAtEntity,
			boolean holdingUsable, boolean holdingSlotItem, boolean flying) {
		this(inContainerScreen, inOtherScreen, lookingAtBlock, lookingAtEntity, holdingUsable, holdingSlotItem, flying,
				BundleState.NONE);
	}
}
