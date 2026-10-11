package dev.hearthside.core.input;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

/**
 * One player's controller bindings and settings. Sticks are fixed (left moves, right looks); every
 * other control can be bound to at most one in-world action and at most one menu action.
 */
public record ControlScheme(Map<Action, Control> gameplay, Map<MenuAction, Control> menu, float lookSensitivity,
		boolean invertY, float deadZone, boolean toggleSneak, boolean autoJump, boolean showButtonGuide) {
	public static final float MIN_SENSITIVITY = 0.1f, MAX_SENSITIVITY = 3.0f;
	public static final float MIN_DEAD_ZONE = 0.05f, MAX_DEAD_ZONE = 0.5f;

	/** Bedrock Edition's default Xbox layout; Java-only actions sit on buttons Bedrock leaves free. */
	public static final ControlScheme BEDROCK = new ControlScheme(
			Map.ofEntries(
					Map.entry(Action.JUMP, Control.A),
					Map.entry(Action.SNEAK, Control.B),
					Map.entry(Action.CRAFTING, Control.X),
					Map.entry(Action.INVENTORY, Control.Y),
					Map.entry(Action.ATTACK, Control.RIGHT_TRIGGER),
					Map.entry(Action.USE, Control.LEFT_TRIGGER),
					Map.entry(Action.HOTBAR_PREV, Control.LEFT_BUMPER),
					Map.entry(Action.HOTBAR_NEXT, Control.RIGHT_BUMPER),
					Map.entry(Action.SPRINT, Control.LEFT_STICK),
					Map.entry(Action.DROP, Control.DPAD_DOWN),
					Map.entry(Action.PERSPECTIVE, Control.DPAD_UP),
					Map.entry(Action.CHAT, Control.DPAD_RIGHT),
					Map.entry(Action.SWAP_OFFHAND, Control.DPAD_LEFT),
					Map.entry(Action.PAUSE, Control.START),
					Map.entry(Action.PLAYER_LIST, Control.BACK)),
			Map.of(
					MenuAction.PICK_PLACE, Control.A,
					MenuAction.TAKE_HALF, Control.X,
					MenuAction.QUICK_MOVE, Control.Y,
					MenuAction.CLOSE, Control.B),
			1.0f, false, 0.15f, false, true, true);

	public ControlScheme {
		gameplay = copy(gameplay, Action.class);
		menu = copy(menu, MenuAction.class);
		requireDistinct(gameplay, "in-world action");
		requireDistinct(menu, "menu action");
		lookSensitivity = Math.clamp(lookSensitivity, MIN_SENSITIVITY, MAX_SENSITIVITY);
		deadZone = Math.clamp(deadZone, MIN_DEAD_ZONE, MAX_DEAD_ZONE);
	}

	public Optional<Control> controlFor(Action action) {
		return Optional.ofNullable(gameplay.get(action));
	}

	public Optional<Control> controlFor(MenuAction action) {
		return Optional.ofNullable(menu.get(action));
	}

	/** Binds {@code control} to {@code action}; whatever in-world action used that control loses it. */
	public ControlScheme withBinding(Action action, Control control) {
		Map<Action, Control> next = new EnumMap<>(Action.class);
		next.putAll(gameplay);
		next.values().removeIf(control::equals);
		next.put(action, control);
		return new ControlScheme(next, menu, lookSensitivity, invertY, deadZone, toggleSneak, autoJump, showButtonGuide);
	}

	private static <K extends Enum<K>> Map<K, Control> copy(Map<K, Control> source, Class<K> type) {
		Map<K, Control> map = new EnumMap<>(type);
		map.putAll(source);
		return Collections.unmodifiableMap(map);
	}

	private static void requireDistinct(Map<?, Control> bindings, String kind) {
		EnumSet<Control> seen = EnumSet.noneOf(Control.class);
		for (Control control : bindings.values()) {
			if (!seen.add(control)) {
				throw new IllegalArgumentException(control + " is bound to more than one " + kind);
			}
		}
	}
}
