package dev.hearthside.core.input;

/**
 * What the player is doing, for choosing which button hints to show.
 *
 * @param holdingSlotItem an item is on the cursor in a container screen
 */
public record GuideContext(boolean inContainerScreen, boolean inOtherScreen, boolean lookingAtBlock,
		boolean lookingAtEntity, boolean holdingUsable, boolean holdingSlotItem, boolean flying) {}
