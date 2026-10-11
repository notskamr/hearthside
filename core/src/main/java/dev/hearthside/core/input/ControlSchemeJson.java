package dev.hearthside.core.input;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads and writes {@link ControlScheme}s. Reading is tolerant by design (profile files are
 * hand-editable): unknown names are skipped and any missing or malformed field falls back to that
 * field's Bedrock value. It never throws.
 */
public final class ControlSchemeJson {
	private static final Gson GSON = new Gson();
	private static final ControlScheme DEFAULTS = ControlScheme.BEDROCK;

	private ControlSchemeJson() {}

	public static String toJson(ControlScheme scheme) {
		return GSON.toJson(toTree(scheme));
	}

	public static JsonObject toTree(ControlScheme scheme) {
		JsonObject root = new JsonObject();
		JsonObject gameplay = new JsonObject();
		scheme.gameplay().forEach((action, control) -> gameplay.addProperty(action.name(), control.name()));
		JsonObject menu = new JsonObject();
		scheme.menu().forEach((action, control) -> menu.addProperty(action.name(), control.name()));
		root.add("gameplay", gameplay);
		root.add("menu", menu);
		root.addProperty("lookSensitivity", scheme.lookSensitivity());
		root.addProperty("invertY", scheme.invertY());
		root.addProperty("deadZone", scheme.deadZone());
		root.addProperty("toggleSneak", scheme.toggleSneak());
		root.addProperty("autoJump", scheme.autoJump());
		root.addProperty("showButtonGuide", scheme.showButtonGuide());
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
					flag(root.get("showButtonGuide"), DEFAULTS.showButtonGuide()));
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
		sorted.forEach((name, value) -> {
			K action = parse(type, name);
			Control control = value instanceof JsonPrimitive p && p.isString() ? parse(Control.class, p.getAsString()) : null;
			if (action != null && control != null && used.add(control)) {
				result.put(action, control);
			}
		});
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
