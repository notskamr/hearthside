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
		if (c.inContainerScreen()) {
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

	private static void add(List<Prompt> prompts, Optional<Control> control, String label) {
		control.ifPresent(c -> prompts.add(new Prompt(c, KEY + label)));
	}
}
