package dev.hearthside.core.input;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One player's controller bindings and settings. The left stick moves and the right looks, or the other way round
 * with {@code swapSticks} (Southpaw); every other control can be bound to at most one in-world action and at most one
 * menu action.
 */
public record ControlScheme(Map<Action, Control> gameplay, Map<MenuAction, Control> menu, float lookSensitivity,
		boolean invertY, float deadZone, boolean toggleSneak, boolean autoJump, boolean showButtonGuide,
		boolean swapSticks) {
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

	/** Java-like: the off-hand on X and pick block on the D-pad's left, as Java players expect; no recipe button. */
	public static final ControlScheme JAVA_LIKE = BEDROCK.withGameplay(map -> {
		map.remove(Action.CRAFTING);
		map.put(Action.SWAP_OFFHAND, Control.X);
		map.put(Action.PICK_BLOCK, Control.DPAD_LEFT);
	}, false);

	/** Southpaw: the sticks swapped (right moves, left looks), and the triggers and bumpers mirrored. */
	public static final ControlScheme SOUTHPAW = BEDROCK.withGameplay(map -> {
		map.put(Action.ATTACK, Control.LEFT_TRIGGER);
		map.put(Action.USE, Control.RIGHT_TRIGGER);
		map.put(Action.HOTBAR_PREV, Control.RIGHT_BUMPER);
		map.put(Action.HOTBAR_NEXT, Control.LEFT_BUMPER);
	}, true);

	/** A preset with the name players see. */
	public record Named(String name, ControlScheme scheme) {}

	public static final List<Named> PRESETS = List.of(new Named("Bedrock", BEDROCK), new Named("Java-like", JAVA_LIKE),
			new Named("Southpaw", SOUTHPAW));

	public ControlScheme(Map<Action, Control> gameplay, Map<MenuAction, Control> menu, float lookSensitivity,
			boolean invertY, float deadZone, boolean toggleSneak, boolean autoJump, boolean showButtonGuide) {
		this(gameplay, menu, lookSensitivity, invertY, deadZone, toggleSneak, autoJump, showButtonGuide, false);
	}

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
		return new ControlScheme(next, menu, lookSensitivity, invertY, deadZone, toggleSneak, autoJump, showButtonGuide,
				swapSticks);
	}

	/**
	 * Binds {@code control} to {@code action} by swapping: the action that had the control takes this action's old one
	 * (or none, if it had none), so nothing is lost by accident and no control serves two actions.
	 */
	public ControlScheme withSwap(Action action, Control control) {
		Control old = gameplay.get(action);
		Map<Action, Control> next = new EnumMap<>(Action.class);
		next.putAll(gameplay);
		for (Map.Entry<Action, Control> entry : gameplay.entrySet()) {
			if (entry.getValue() == control && entry.getKey() != action) {
				if (old != null) {
					next.put(entry.getKey(), old);
				} else {
					next.remove(entry.getKey());
				}
			}
		}
		next.put(action, control);
		return new ControlScheme(next, menu, lookSensitivity, invertY, deadZone, toggleSneak, autoJump, showButtonGuide,
				swapSticks);
	}

	/** This scheme's settings with its in-world bindings changed by {@code change}, sticks as given. */
	private ControlScheme withGameplay(java.util.function.Consumer<Map<Action, Control>> change, boolean sticksSwapped) {
		Map<Action, Control> next = new EnumMap<>(Action.class);
		next.putAll(gameplay);
		change.accept(next);
		return new ControlScheme(next, menu, lookSensitivity, invertY, deadZone, toggleSneak, autoJump, showButtonGuide,
				sticksSwapped);
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
