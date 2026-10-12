package dev.hearthside.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.hearthside.core.layout.LayoutSettings;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.System.Logger.Level;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The host's split-screen settings, {@code <gameDir>/config/hearthside.json}: what each guest's game is given, and how
 * the players share the screen.
 *
 * <p>Each guest is a full Minecraft, so its heap is fixed rather than a share of the host's, and its render distance is
 * capped so a party fits on a laptop. Values outside their range are clamped; a missing, wrong-typed, unknown or
 * unreadable field falls back to its default, so a hand-edited file never stops a party from starting.
 *
 * <p>In the file the layout is three fields: {@code twoPlayerLayout} ({@code side_by_side} or {@code stacked}),
 * {@code threePlayerLayout} ({@code grid_with_panel}, {@code wide_top} or {@code tall_left}) and {@code partyPanel}
 * (whether three players' free quarter shows the party map).
 *
 * @param guestMemoryMb each guest's maximum heap ({@code -Xmx}), 768..8192 MB
 * @param guestRenderDistance the most chunks a guest renders, 2..32, never above the guest's own setting; {@code 0}
 *     means no cap (the guest's own setting), and so does any negative number (a player writing {@code -1} for "none"
 *     gets none, not the harshest cap)
 * @param layout how the players share the one window
 */
public record SplitScreenConfig(int guestMemoryMb, int guestRenderDistance, LayoutSettings layout) {
	public static final int MIN_GUEST_MEMORY_MB = 768;
	public static final int MAX_GUEST_MEMORY_MB = 8192;
	public static final int MIN_RENDER_DISTANCE = 2;
	public static final int MAX_RENDER_DISTANCE = 32;
	/** {@link #guestRenderDistance()} meaning "as the guest's own setting". */
	public static final int NO_RENDER_DISTANCE_CAP = 0;

	public static final SplitScreenConfig DEFAULTS = new SplitScreenConfig(1536, 8);

	private static final String MEMORY = "guestMemoryMb";
	private static final String RENDER_DISTANCE = "guestRenderDistance";
	private static final String TWO_PLAYERS = "twoPlayerLayout";
	private static final String THREE_PLAYERS = "threePlayerLayout";
	private static final String PARTY_PANEL = "partyPanel";
	private static final List<String> FIELDS = List.of(MEMORY, RENDER_DISTANCE, TWO_PLAYERS, THREE_PLAYERS, PARTY_PANEL);
	private static final System.Logger LOG = System.getLogger(SplitScreenConfig.class.getName());
	private static final Gson GSON =new GsonBuilder().setPrettyPrinting().create();
	private static final BigDecimal INT_MIN = BigDecimal.valueOf(Integer.MIN_VALUE);
	private static final BigDecimal INT_MAX = BigDecimal.valueOf(Integer.MAX_VALUE);

	public SplitScreenConfig {
		guestMemoryMb = Math.clamp(guestMemoryMb, MIN_GUEST_MEMORY_MB, MAX_GUEST_MEMORY_MB);
		if (guestRenderDistance <= NO_RENDER_DISTANCE_CAP) {
			guestRenderDistance = NO_RENDER_DISTANCE_CAP;
		} else {
			guestRenderDistance = Math.clamp(guestRenderDistance, MIN_RENDER_DISTANCE, MAX_RENDER_DISTANCE);
		}
		Objects.requireNonNull(layout, "layout");
	}

	/** The guests' settings with the default layout. */
	public SplitScreenConfig(int guestMemoryMb, int guestRenderDistance) {
		this(guestMemoryMb, guestRenderDistance, LayoutSettings.DEFAULTS);
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
		LayoutSettings layout = DEFAULTS.layout()
				.withTwo(enumOr(object, TWO_PLAYERS, DEFAULTS.layout().two()))
				.withThree(enumOr(object, THREE_PLAYERS, DEFAULTS.layout().three()))
				.withPartyPanel(booleanOr(object, PARTY_PANEL, DEFAULTS.layout().partyPanel()));
		return new SplitScreenConfig(intOr(object, MEMORY, DEFAULTS.guestMemoryMb()),
				intOr(object, RENDER_DISTANCE, DEFAULTS.guestRenderDistance()), layout);
	}

	/**
	 * Whether this is a settings object that lacks a field, such as a file from before a setting existed. Content that
	 * isn't a JSON object doesn't count: it is left as it is. Only a failed read throws.
	 */
	public static boolean lacksFields(Reader in) throws IOException {
		JsonElement root;
		try {
			root = JsonParser.parseReader(in);
		} catch (JsonIOException e) {
			throw e.getCause() instanceof IOException io ? io : new IOException(e);
		} catch (JsonParseException e) {
			return false;
		}
		return root.isJsonObject() && !root.getAsJsonObject().keySet().containsAll(FIELDS);
	}

	/**
	 * The settings in {@code file}. A missing file is created with every field at its default; a file that lacks some
	 * fields gets them, keeping its own values (an invalid one becomes its default), so players can find every setting.
	 * One that isn't a settings object at all gives the defaults and is left as it is. If the missing fields can't be
	 * added (the file is held open by another program, or the folder is read-only), that is logged and the values read
	 * are still used; the file stays as it was. Only a failed read, or a failed creation of a missing file, throws.
	 */
	public static SplitScreenConfig loadOrCreate(Path file) throws IOException {
		if (Files.notExists(file)) {
			Files.createDirectories(file.toAbsolutePath().getParent());
			try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
					StandardOpenOption.WRITE)) {
				DEFAULTS.write(out);
				return DEFAULTS;
			} catch (FileAlreadyExistsException e) {
				// created by someone else meanwhile: read theirs
			}
		}
		String content = Files.readString(file, StandardCharsets.UTF_8);
		SplitScreenConfig config = read(new StringReader(content));
		if (lacksFields(new StringReader(content))) {
			try {
				replace(file, config);
			} catch (IOException e) {
				LOG.log(Level.WARNING, "Could not add the missing settings to " + file + "; using the ones it has", e);
			}
		}
		return config;
	}

	/**
	 * Writes {@code config} to {@code file} through a temporary file beside it ({@code <name>.tmp}) and a move, so the
	 * file is never half-written. A temporary file this left is removed again if the write or the move fails.
	 */
	private static void replace(Path file, SplitScreenConfig config) throws IOException {
		Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
		boolean created = false;
		try {
			try (Writer out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				created = true;
				config.write(out);
			}
			try {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException | RuntimeException e) {
			if (created) {
				try {
					Files.deleteIfExists(temporary);
				} catch (IOException suppressed) {
					e.addSuppressed(suppressed);
				}
			}
			throw e;
		}
	}

	/** Writes every field, pretty-printed, so players can find them all in the file. */
	public void write(Writer out) throws IOException {
		JsonObject object = new JsonObject();
		object.addProperty(MEMORY, guestMemoryMb);
		object.addProperty(RENDER_DISTANCE, guestRenderDistance);
		object.addProperty(TWO_PLAYERS, name(layout.two()));
		object.addProperty(THREE_PLAYERS, name(layout.three()));
		object.addProperty(PARTY_PANEL, layout.partyPanel());
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

	private static boolean booleanOr(JsonObject object, String name, boolean fallback) {
		return object.get(name) instanceof JsonPrimitive value && value.isBoolean() ? value.getAsBoolean() : fallback;
	}

	/** The constant written as {@code name(constant)}, ignoring case; anything else gives the fallback. */
	private static <E extends Enum<E>> E enumOr(JsonObject object, String name, E fallback) {
		if (!(object.get(name) instanceof JsonPrimitive value) || !value.isString()) {
			return fallback;
		}
		for (E constant : fallback.getDeclaringClass().getEnumConstants()) {
			if (name(constant).equalsIgnoreCase(value.getAsString().strip())) {
				return constant;
			}
		}
		return fallback;
	}

	/** How a layout is spelled in the file: {@code SIDE_BY_SIDE} is {@code side_by_side}. */
	private static String name(Enum<?> constant) {
		return constant.name().toLowerCase(Locale.ROOT);
	}
}
