package dev.hearthside.core.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Chooses Bedrock-style on-screen button hints for the current situation. Only bound actions are
 * shown, with whatever control the player's scheme binds them to.
 */
public final class ButtonGuide {
	public static final int MAX_PROMPTS = 6;
	private static final String KEY = "hearthside.guide.";

	public record Prompt(Control control, String labelKey) {}

	private ButtonGuide() {}

	public static List<Prompt> prompts(GuideContext c, ControlScheme scheme) {
		List<Prompt> prompts = new ArrayList<>();
		if (!scheme.showButtonGuide()) {
			return prompts;
		}
		if (c.inContainerScreen() && c.bundle() != BundleState.NONE) {
			bundlePrompts(prompts, c.bundle(), scheme);
		} else if (c.inContainerScreen()) {
			add(prompts, scheme.controlFor(MenuAction.PICK_PLACE), c.holdingSlotItem() ? "place" : "take");
			add(prompts, scheme.controlFor(MenuAction.TAKE_HALF), c.holdingSlotItem() ? "place_one" : "take_half");
			if (!c.holdingSlotItem()) {
				add(prompts, scheme.controlFor(MenuAction.QUICK_MOVE), "quick_move");
			}
			add(prompts, scheme.controlFor(MenuAction.CLOSE), "close");
		} else if (c.inOtherScreen()) {
			add(prompts, scheme.controlFor(MenuAction.PICK_PLACE), "select");
			add(prompts, scheme.controlFor(MenuAction.CLOSE), "back");
		} else {
			if (c.lookingAtEntity()) {
				add(prompts, scheme.controlFor(Action.ATTACK), "attack");
			} else if (c.lookingAtBlock()) {
				add(prompts, scheme.controlFor(Action.ATTACK), "mine");
			}
			if (c.holdingUsable()) {
				add(prompts, scheme.controlFor(Action.USE), c.lookingAtBlock() ? "place" : "use");
			}
			add(prompts, scheme.controlFor(Action.JUMP), c.flying() ? "fly_up" : "jump");
			add(prompts, scheme.controlFor(Action.SNEAK), c.flying() ? "fly_down" : "sneak");
			add(prompts, scheme.controlFor(Action.INVENTORY), "inventory");
		}
		return prompts.size() > MAX_PROMPTS ? List.copyOf(prompts.subList(0, MAX_PROMPTS)) : prompts;
	}

	/**
	 * Bundles follow vanilla's clicks: over a bundle with an empty hand the look stick (a scroll) chooses an item and a
	 * right click takes it out; with an item held, a left click puts it in. Holding a bundle, a left click scoops a stack
	 * into it, and a right click over an empty slot takes one out.
	 */
	private static void bundlePrompts(List<Prompt> prompts, BundleState bundle, ControlScheme scheme) {
		Optional<Control> pick = scheme.controlFor(MenuAction.PICK_PLACE);
		Optional<Control> half = scheme.controlFor(MenuAction.TAKE_HALF);
		switch (bundle) {
			case OVER_BUNDLE_EMPTY_HAND -> {
				add(prompts, pick, "take");
				add(prompts, Optional.of(scheme.swapSticks() ? Control.LEFT_STICK : Control.RIGHT_STICK), "bundle_choose");
				add(prompts, half, "bundle_take_out");
				add(prompts, scheme.controlFor(MenuAction.QUICK_MOVE), "quick_move");
			}
			case OVER_BUNDLE_HOLDING, HOLDING_BUNDLE_OVER_STACK -> add(prompts, pick, "bundle_put_in");
			case HOLDING_BUNDLE_OVER_EMPTY -> {
				add(prompts, pick, "place");
				add(prompts, half, "bundle_take_out");
			}
			case NONE -> {}
		}
		add(prompts, scheme.controlFor(MenuAction.CLOSE), "close");
	}

	private static void add(List<Prompt> prompts, Optional<Control> control, String label) {
		control.ifPresent(c -> prompts.add(new Prompt(c, KEY + label)));
	}
}
