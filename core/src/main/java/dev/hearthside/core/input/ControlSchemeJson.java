package dev.hearthside.core.input;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads and writes {@link ControlScheme}s. Reading is tolerant by design (profile files are
 * hand-editable): unknown names are skipped and any missing or malformed field falls back to that
 * field's Bedrock value. It never throws.
 */
public final class ControlSchemeJson {
	private static final System.Logger LOG = System.getLogger(ControlSchemeJson.class.getName());
	private static final Gson GSON = new Gson();
	/** Value that explicitly unbinds an action. */
	public static final String NONE = "NONE";
	private static final ControlScheme DEFAULTS = ControlScheme.BEDROCK;

	private ControlSchemeJson() {}

	public static String toJson(ControlScheme scheme) {
		return GSON.toJson(toTree(scheme));
	}

	public static JsonObject toTree(ControlScheme scheme) {
		JsonObject root = new JsonObject();
		// Every action is written, unbound ones as NONE, so reading never re-fills them with defaults.
		JsonObject gameplay = new JsonObject();
		for (Action action : Action.values()) {
			gameplay.addProperty(action.name(), scheme.controlFor(action).map(Control::name).orElse(NONE));
		}
		JsonObject menu = new JsonObject();
		for (MenuAction action : MenuAction.values()) {
			menu.addProperty(action.name(), scheme.controlFor(action).map(Control::name).orElse(NONE));
		}
		root.add("gameplay", gameplay);
		root.add("menu", menu);
		root.addProperty("lookSensitivity", scheme.lookSensitivity());
		root.addProperty("invertY", scheme.invertY());
		root.addProperty("deadZone", scheme.deadZone());
		root.addProperty("toggleSneak", scheme.toggleSneak());
		root.addProperty("autoJump", scheme.autoJump());
		root.addProperty("showButtonGuide", scheme.showButtonGuide());
		root.addProperty("swapSticks", scheme.swapSticks());
		return root;
	}

	public static ControlScheme fromJson(String json) {
		try {
			return fromTree(JsonParser.parseString(json == null ? "" : json));
		} catch (RuntimeException e) {
			return DEFAULTS;
		}
	}

	public static ControlScheme fromTree(JsonElement element) {
		try {
			if (element == null || !element.isJsonObject()) {
				return DEFAULTS;
			}
			JsonObject root = element.getAsJsonObject();
			return new ControlScheme(
					bindings(root.get("gameplay"), Action.class, DEFAULTS.gameplay()),
					bindings(root.get("menu"), MenuAction.class, DEFAULTS.menu()),
					number(root.get("lookSensitivity"), DEFAULTS.lookSensitivity()),
					flag(root.get("invertY"), DEFAULTS.invertY()),
					number(root.get("deadZone"), DEFAULTS.deadZone()),
					flag(root.get("toggleSneak"), DEFAULTS.toggleSneak()),
					flag(root.get("autoJump"), DEFAULTS.autoJump()),
					flag(root.get("showButtonGuide"), DEFAULTS.showButtonGuide()),
					flag(root.get("swapSticks"), DEFAULTS.swapSticks()));
		} catch (RuntimeException e) {
			return DEFAULTS;
		}
	}

	/** Valid entries only; when two actions claim one control, the alphabetically first action keeps it. */
	private static <K extends Enum<K>> Map<K, Control> bindings(JsonElement element, Class<K> type, Map<K, Control> fallback) {
		if (element == null || !element.isJsonObject()) {
			return fallback;
		}
		Map<String, JsonElement> sorted = new TreeMap<>(element.getAsJsonObject().asMap());
		Map<K, Control> result = new EnumMap<>(type);
		EnumSet<Control> used = EnumSet.noneOf(Control.class);
		Set<K> unbound = new HashSet<>();
		List<String> dropped = new ArrayList<>();
		sorted.forEach((name, value) -> {
			K action = parse(type, name);
			String text = value instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
			if (action != null && NONE.equals(text)) {
				unbound.add(action);
				return;
			}
			Control control = text == null ? null : parse(Control.class, text);
			if (action != null && control != null && used.add(control)) {
				result.put(action, control);
			} else {
				dropped.add(name + "=" + value);
			}
		});
		// Anything not (validly) mentioned keeps its Bedrock control, as long as that control is free:
		// a typo must never leave a player without pause, jump or inventory.
		for (K action : type.getEnumConstants()) {
			Control fallbackControl = fallback.get(action);
			if (!result.containsKey(action) && !unbound.contains(action) && fallbackControl != null && used.add(fallbackControl)) {
				result.put(action, fallbackControl);
			}
		}
		if (!dropped.isEmpty()) {
			LOG.log(Level.WARNING, "Ignored controller bindings " + dropped + "; using defaults for those actions");
		}
		return result;
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String name) {
		try {
			return Enum.valueOf(type, name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static float number(JsonElement element, float fallback) {
		if (element instanceof JsonPrimitive p && p.isNumber()) {
			float value = p.getAsFloat();
			return Float.isFinite(value) ? value : fallback;
		}
		return fallback;
	}

	private static boolean flag(JsonElement element, boolean fallback) {
		return element instanceof JsonPrimitive p && p.isBoolean() ? p.getAsBoolean() : fallback;
	}
}
