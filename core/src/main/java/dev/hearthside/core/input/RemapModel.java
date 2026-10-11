package dev.hearthside.core.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The controls screen's state (Spec A section 5.4): the player's controls as edited, the presets to cycle through,
 * and the action waiting for a button. A binding is made by swapping, so nothing is lost by accident. Cycling the
 * presets keeps the player's settings and can always come back to their own layout. Not thread-safe.
 */
public final class RemapModel {
	private static final Map<Action, String> LABELS = Map.ofEntries(
			Map.entry(Action.JUMP, "Jump"),
			Map.entry(Action.SNEAK, "Sneak"),
			Map.entry(Action.SPRINT, "Sprint"),
			Map.entry(Action.ATTACK, "Attack"),
			Map.entry(Action.USE, "Use / place"),
			Map.entry(Action.DROP, "Drop"),
			Map.entry(Action.INVENTORY, "Inventory"),
			Map.entry(Action.CRAFTING, "Crafting"),
			Map.entry(Action.HOTBAR_PREV, "Previous item"),
			Map.entry(Action.HOTBAR_NEXT, "Next item"),
			Map.entry(Action.PERSPECTIVE, "Camera"),
			Map.entry(Action.SWAP_OFFHAND, "Swap hands"),
			Map.entry(Action.PICK_BLOCK, "Pick block"),
			Map.entry(Action.CHAT, "Chat"),
			Map.entry(Action.PLAYER_LIST, "Player list"),
			Map.entry(Action.PAUSE, "Pause"));

	/** The player's own layout as edited; the presets are laid over it. */
	private ControlScheme yours;
	/** 0 = yours, otherwise the n-th of {@link #choices()}. */
	private int choice;
	private Action listening;

	public RemapModel(ControlScheme start) {
		this.yours = start;
	}

	/** What a player calls an action. */
	public static String label(Action action) {
		return LABELS.get(action);
	}

	/** The controls as they stand: the player's own, or the preset being tried, with the player's settings. */
	public ControlScheme scheme() {
		List<ControlScheme> choices = choices();
		return choices.get(Math.min(choice, choices.size() - 1));
	}

	/** The preset's name, or "Custom" when the layout matches none. */
	public String name() {
		return ControlScheme.presetName(scheme()).orElse("Custom");
	}

	public void nextPreset() {
		choice = Math.floorMod(choice + 1, choices().size());
	}

	public void previousPreset() {
		choice = Math.floorMod(choice - 1, choices().size());
	}

	/** Waits for the button to bind to {@code action}. */
	public void listen(Action action) {
		listening = action;
	}

	public void cancel() {
		listening = null;
	}

	/** The action waiting for a button, or null. */
	public Action listening() {
		return listening;
	}

	/** A control was pressed: bound to the waiting action (by swapping), if one is waiting. */
	public boolean press(Control control) {
		if (listening == null) {
			return false;
		}
		yours = scheme().withSwap(listening, control);
		choice = 0;
		listening = null;
		return true;
	}

	/** What a control does with the controls as they stand. */
	public Optional<Action> actionOf(Control control) {
		return scheme().gameplay().entrySet().stream().filter(e -> e.getValue() == control).map(Map.Entry::getKey)
				.findFirst();
	}

	/** The player's own layout, then each preset with a different layout, all with the player's settings. */
	private List<ControlScheme> choices() {
		List<ControlScheme> choices = new ArrayList<>();
		choices.add(yours);
		for (ControlScheme.Named preset : ControlScheme.PRESETS) {
			if (!ControlScheme.sameLayout(preset.scheme(), yours)) {
				choices.add(yours.withLayoutOf(preset.scheme()));
			}
		}
		return choices;
	}
}
