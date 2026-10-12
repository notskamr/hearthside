package dev.hearthside.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;

/**
 * The host's split-screen settings, {@code <gameDir>/config/hearthside.json}: what each guest's game is given.
 *
 * <p>Each guest is a full Minecraft, so its heap is fixed rather than a share of the host's, and its render distance is
 * capped so a party fits on a laptop. Values outside their range are clamped; a missing, wrong-typed or unreadable
 * field falls back to its default, so a hand-edited file never stops a party from starting.
 *
 * @param guestMemoryMb each guest's maximum heap ({@code -Xmx}), 768..8192 MB
 * @param guestRenderDistance the most chunks a guest renders, 2..32, never above the guest's own setting; {@code 0}
 *     means no cap (the guest's own setting)
 */
public record SplitScreenConfig(int guestMemoryMb, int guestRenderDistance) {
	public static final int MIN_GUEST_MEMORY_MB = 768;
	public static final int MAX_GUEST_MEMORY_MB = 8192;
	public static final int MIN_RENDER_DISTANCE = 2;
	public static final int MAX_RENDER_DISTANCE = 32;
	/** {@link #guestRenderDistance()} meaning "as the guest's own setting". */
	public static final int NO_RENDER_DISTANCE_CAP = 0;

	public static final SplitScreenConfig DEFAULTS = new SplitScreenConfig(1536, 8);

	private static final String MEMORY = "guestMemoryMb";
	private static final String RENDER_DISTANCE = "guestRenderDistance";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final BigDecimal INT_MIN = BigDecimal.valueOf(Integer.MIN_VALUE);
	private static final BigDecimal INT_MAX = BigDecimal.valueOf(Integer.MAX_VALUE);

	public SplitScreenConfig {
		guestMemoryMb = Math.clamp(guestMemoryMb, MIN_GUEST_MEMORY_MB, MAX_GUEST_MEMORY_MB);
		if (guestRenderDistance != NO_RENDER_DISTANCE_CAP) {
			guestRenderDistance = Math.clamp(guestRenderDistance, MIN_RENDER_DISTANCE, MAX_RENDER_DISTANCE);
		}
	}

	/** Whether guests' render distance is capped at all. */
	public boolean capsRenderDistance() {
		return guestRenderDistance != NO_RENDER_DISTANCE_CAP;
	}

	/** Reads a config; content that isn't valid gives the defaults, field by field. Only a failed read throws. */
	public static SplitScreenConfig read(Reader in) throws IOException {
		JsonElement root;
		try {
			root = JsonParser.parseReader(in);
		} catch (JsonIOException e) {
			throw e.getCause() instanceof IOException io ? io : new IOException(e);
		} catch (JsonParseException e) {
			return DEFAULTS;
		}
		if (!root.isJsonObject()) {
			return DEFAULTS;
		}
		JsonObject object = root.getAsJsonObject();
		return new SplitScreenConfig(intOr(object, MEMORY, DEFAULTS.guestMemoryMb()),
				intOr(object, RENDER_DISTANCE, DEFAULTS.guestRenderDistance()));
	}

	/** Writes both fields, pretty-printed. */
	public void write(Writer out) throws IOException {
		JsonObject object = new JsonObject();
		object.addProperty(MEMORY, guestMemoryMb);
		object.addProperty(RENDER_DISTANCE, guestRenderDistance);
		try {
			GSON.toJson(object, out);
		} catch (JsonIOException e) {
			throw e.getCause() instanceof IOException io ? io : new IOException(e);
		}
		out.write(System.lineSeparator());
		out.flush();
	}

	private static int intOr(JsonObject object, String name, int fallback) {
		if (!(object.get(name) instanceof JsonPrimitive value) || !value.isNumber()) {
			return fallback;
		}
		try {
			// A huge number still clamps (in the constructor) rather than falling back.
			return value.getAsBigDecimal().max(INT_MIN).min(INT_MAX).intValueExact();
		} catch (ArithmeticException | NumberFormatException e) {
			return fallback; // a fraction, or not a number after all
		}
	}
}
