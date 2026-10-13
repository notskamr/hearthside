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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * The host's split-screen settings, {@code <gameDir>/config/hearthside.json}: what each guest's game is given, how the
 * players share the screen, how much of the machine a party asks for, and what the player said to switching to Vulkan.
 *
 * <p>Each guest is a full Minecraft, so its heap is fixed rather than a share of the host's, and its render distance is
 * capped so a party fits on a laptop. Values outside their range are clamped; a missing, wrong-typed, unknown or
 * unreadable field falls back to its default, so a hand-edited file never stops a party from starting.
 *
 * <p>In the file the layout is six fields: {@code twoPlayerLayout} ({@code side_by_side} or {@code stacked}),
 * {@code threePlayerLayout} ({@code grid_with_panel}, {@code wide_top} or {@code tall_left}), {@code partyPanel}
 * (whether three players' free quarter shows the party map), {@code borders} ({@code colour}, {@code thin} or
 * {@code off}), {@code nameTags} and {@code layoutAnimation}.
 *
 * @param guestMemoryMb each guest's maximum heap ({@code -Xmx}), 768..8192 MB
 * @param guestRenderDistance the most chunks a guest renders, 2..32, never above the guest's own setting;
 *     {@link #RENDER_DISTANCE_FROM_PRESET} (in the file, {@code "preset"}) leaves it to the preset; {@code 0} means no
 *     cap (the guest's own setting), and in the file so does any negative number (a player writing {@code -1} for
 *     "none" gets none, not the harshest cap)
 * @param layout how the players share the one window
 * @param preset what each party session is tuned for ({@link SessionTuning}); {@code auto} chooses per session
 * @param presetForPlayer1 whether player 1's own game takes the preset too; otherwise only guests (and the world's
 *     simulation distance) do
 * @param vulkanOffer the answer to the offer to switch to Vulkan, so it is asked once
 * @param titleStyle which title screen player 1 sees, from the next time it opens
 */
public record SplitScreenConfig(int guestMemoryMb, int guestRenderDistance, LayoutSettings layout, Preset preset,
		boolean presetForPlayer1, VulkanOffer vulkanOffer, TitleStyleSetting titleStyle) {
	public static final int MIN_GUEST_MEMORY_MB = 768;
	public static final int MAX_GUEST_MEMORY_MB = 8192;
	public static final int MIN_RENDER_DISTANCE = 2;
	public static final int MAX_RENDER_DISTANCE = 32;
	/** {@link #guestRenderDistance()} meaning "as the guest's own setting". */
	public static final int NO_RENDER_DISTANCE_CAP = 0;
	/** {@link #guestRenderDistance()} meaning "as the preset caps it", written {@code "preset"}. */
	public static final int RENDER_DISTANCE_FROM_PRESET = -1;

	public static final SplitScreenConfig DEFAULTS = new SplitScreenConfig(1536, RENDER_DISTANCE_FROM_PRESET,
			LayoutSettings.DEFAULTS, Preset.AUTO, false, VulkanOffer.PENDING, TitleStyleSetting.HEARTHSIDE);

	private static final String MEMORY = "guestMemoryMb";
	private static final String RENDER_DISTANCE = "guestRenderDistance";
	private static final String PRESET = "preset";
	private static final String PRESET_FOR_PLAYER1 = "presetForPlayer1";
	private static final String TWO_PLAYERS = "twoPlayerLayout";
	private static final String THREE_PLAYERS = "threePlayerLayout";
	private static final String PARTY_PANEL = "partyPanel";
	private static final String BORDERS = "borders";
	private static final String NAME_TAGS = "nameTags";
	private static final String LAYOUT_ANIMATION = "layoutAnimation";
	private static final String VULKAN_OFFER = "vulkanOffer";
	private static final String TITLE_STYLE = "titleStyle";
	private static final List<String> FIELDS = List.of(MEMORY, RENDER_DISTANCE, PRESET, PRESET_FOR_PLAYER1, TWO_PLAYERS,
			THREE_PLAYERS, PARTY_PANEL, BORDERS, NAME_TAGS, LAYOUT_ANIMATION, VULKAN_OFFER, TITLE_STYLE);
	/** {@code guestRenderDistance} as written: {@link #RENDER_DISTANCE_FROM_PRESET}. */
	private static final String FROM_PRESET = "preset";
	/** What every file held before presets existed, unless a player changed it (plan R6). */
	private static final int OLD_DEFAULT_RENDER_DISTANCE = 8;
	private static final System.Logger LOG = System.getLogger(SplitScreenConfig.class.getName());
	private static final Gson GSON =new GsonBuilder().setPrettyPrinting().create();
	private static final BigDecimal INT_MIN = BigDecimal.valueOf(Integer.MIN_VALUE);
	private static final BigDecimal INT_MAX = BigDecimal.valueOf(Integer.MAX_VALUE);

	public SplitScreenConfig {
		guestMemoryMb = Math.clamp(guestMemoryMb, MIN_GUEST_MEMORY_MB, MAX_GUEST_MEMORY_MB);
		if (guestRenderDistance == RENDER_DISTANCE_FROM_PRESET) {
			// kept: the preset decides
		} else if (guestRenderDistance <= NO_RENDER_DISTANCE_CAP) {
			guestRenderDistance = NO_RENDER_DISTANCE_CAP;
		} else {
			guestRenderDistance = Math.clamp(guestRenderDistance, MIN_RENDER_DISTANCE, MAX_RENDER_DISTANCE);
		}
		Objects.requireNonNull(layout, "layout");
		Objects.requireNonNull(preset, "preset");
		Objects.requireNonNull(vulkanOffer, "vulkanOffer");
		Objects.requireNonNull(titleStyle, "titleStyle");
	}

	/** Everything but the title-screen style, which is Hearthside's. */
	public SplitScreenConfig(int guestMemoryMb, int guestRenderDistance, LayoutSettings layout, Preset preset,
			boolean presetForPlayer1, VulkanOffer vulkanOffer) {
		this(guestMemoryMb, guestRenderDistance, layout, preset, presetForPlayer1, vulkanOffer, TitleStyleSetting.HEARTHSIDE);
	}

	/** The guests' settings, with everything else at its default. */
	public SplitScreenConfig(int guestMemoryMb, int guestRenderDistance) {
		this(guestMemoryMb, guestRenderDistance, LayoutSettings.DEFAULTS, Preset.AUTO, false, VulkanOffer.PENDING);
	}

	public SplitScreenConfig withLayout(LayoutSettings value) {
		return new SplitScreenConfig(guestMemoryMb, guestRenderDistance, value, preset, presetForPlayer1, vulkanOffer,
				titleStyle);
	}

	public SplitScreenConfig withVulkanOffer(VulkanOffer value) {
		return new SplitScreenConfig(guestMemoryMb, guestRenderDistance, layout, preset, presetForPlayer1, value, titleStyle);
	}

	public SplitScreenConfig withTitleStyle(TitleStyleSetting value) {
		return new SplitScreenConfig(guestMemoryMb, guestRenderDistance, layout, preset, presetForPlayer1, vulkanOffer, value);
	}

	public SplitScreenConfig withGuestMemoryMb(int value) {
		return new SplitScreenConfig(value, guestRenderDistance, layout, preset, presetForPlayer1, vulkanOffer, titleStyle);
	}

	public SplitScreenConfig withGuestRenderDistance(int value) {
		return new SplitScreenConfig(guestMemoryMb, value, layout, preset, presetForPlayer1, vulkanOffer, titleStyle);
	}

	public SplitScreenConfig withPreset(Preset value) {
		return new SplitScreenConfig(guestMemoryMb, guestRenderDistance, layout, value, presetForPlayer1, vulkanOffer,
				titleStyle);
	}

	public SplitScreenConfig withPresetForPlayer1(boolean value) {
		return new SplitScreenConfig(guestMemoryMb, guestRenderDistance, layout, preset, value, vulkanOffer, titleStyle);
	}

	/**
	 * The fields that differ from {@code before}, in the file's order, each as the file writes it: key to value (a string
	 * without its quotes), such as {@code twoPlayerLayout=stacked} or {@code nameTags=false}.
	 */
	public Map<String, String> changesFrom(SplitScreenConfig before) {
		JsonObject now = toJson(), then = before.toJson();
		Map<String, String> changes = new LinkedHashMap<>();
		for (String field : FIELDS) {
			if (!now.get(field).equals(then.get(field))) {
				changes.put(field, now.get(field).getAsString());
			}
		}
		return changes;
	}

	/** Whether guests' render distance is capped at all: by a number, or by the preset (if it caps). */
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
				.withPartyPanel(booleanOr(object, PARTY_PANEL, DEFAULTS.layout().partyPanel()))
				.withBorders(enumOr(object, BORDERS, DEFAULTS.layout().borders()))
				.withNameTags(booleanOr(object, NAME_TAGS, DEFAULTS.layout().nameTags()))
				.withLayoutAnimation(booleanOr(object, LAYOUT_ANIMATION, DEFAULTS.layout().layoutAnimation()));
		return new SplitScreenConfig(intOr(object, MEMORY, DEFAULTS.guestMemoryMb()), renderDistance(object), layout,
				enumOr(object, PRESET, DEFAULTS.preset()),
				booleanOr(object, PRESET_FOR_PLAYER1, DEFAULTS.presetForPlayer1()),
				enumOr(object, VULKAN_OFFER, DEFAULTS.vulkanOffer()),
				enumOr(object, TITLE_STYLE, DEFAULTS.titleStyle()));
	}

	/**
	 * {@code guestRenderDistance} as read: {@code "preset"}, a missing field or an invalid one leave it to the preset (the
	 * default), and a negative number means no cap. A file from before presets existed (it has no {@code preset} field)
	 * that holds the old default 8 follows the preset too (plan R6); any other number of chunks it has is kept.
	 */
	private static int renderDistance(JsonObject object) {
		OptionalInt chunks = intIn(object, RENDER_DISTANCE);
		if (chunks.isEmpty()) {
			return RENDER_DISTANCE_FROM_PRESET;
		}
		if (chunks.getAsInt() < 0) {
			return NO_RENDER_DISTANCE_CAP;
		}
		if (chunks.getAsInt() == OLD_DEFAULT_RENDER_DISTANCE && !object.has(PRESET)) {
			return RENDER_DISTANCE_FROM_PRESET;
		}
		return chunks.getAsInt();
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
	public static void replace(Path file, SplitScreenConfig config) throws IOException {
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
		try {
			GSON.toJson(toJson(), out);
		} catch (JsonIOException e) {
			throw e.getCause() instanceof IOException io ? io : new IOException(e);
		}
		out.write(System.lineSeparator());
		out.flush();
	}

	/** Every field as the file holds it, in the file's order. */
	private JsonObject toJson() {
		JsonObject object = new JsonObject();
		object.addProperty(MEMORY, guestMemoryMb);
		if (guestRenderDistance == RENDER_DISTANCE_FROM_PRESET) {
			object.addProperty(RENDER_DISTANCE, FROM_PRESET);
		} else {
			object.addProperty(RENDER_DISTANCE, guestRenderDistance);
		}
		object.addProperty(PRESET, name(preset));
		object.addProperty(PRESET_FOR_PLAYER1, presetForPlayer1);
		object.addProperty(TWO_PLAYERS, name(layout.two()));
		object.addProperty(THREE_PLAYERS, name(layout.three()));
		object.addProperty(PARTY_PANEL, layout.partyPanel());
		object.addProperty(BORDERS, name(layout.borders()));
		object.addProperty(NAME_TAGS, layout.nameTags());
		object.addProperty(LAYOUT_ANIMATION, layout.layoutAnimation());
		object.addProperty(VULKAN_OFFER, name(vulkanOffer));
		object.addProperty(TITLE_STYLE, name(titleStyle));
		return object;
	}

	private static int intOr(JsonObject object, String name, int fallback) {
		return intIn(object, name).orElse(fallback);
	}

	/** The whole number in a field; empty if it's missing or anything else. */
	private static OptionalInt intIn(JsonObject object, String name) {
		if (!(object.get(name) instanceof JsonPrimitive value) || !value.isNumber()) {
			return OptionalInt.empty();
		}
		try {
			// A huge number still clamps (in the constructor) rather than falling back.
			return OptionalInt.of(value.getAsBigDecimal().max(INT_MIN).min(INT_MAX).intValueExact());
		} catch (ArithmeticException | NumberFormatException e) {
			return OptionalInt.empty(); // a fraction, or not a number after all
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

	/** How a constant is spelled in the file: {@code SIDE_BY_SIDE} is {@code side_by_side}. */
	private static String name(Enum<?> constant) {
		return constant.name().toLowerCase(Locale.ROOT);
	}
}
