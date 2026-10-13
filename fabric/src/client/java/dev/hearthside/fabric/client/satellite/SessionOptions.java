package dev.hearthside.fabric.client.satellite;

import dev.hearthside.core.config.FrameRatePacing;
import dev.hearthside.core.config.SessionOverrides;
import dev.hearthside.core.config.SessionTuning;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.mixin.FramerateLimitTrackerAccessor;
import dev.hearthside.fabric.client.mixin.OptionInstanceAccessor;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

/**
 * A preset's settings on this game's own (vanilla) options, for this session only (Spec D section 3.3): the
 * render-distance cap, light graphics and the frame-rate cap. A guest's game takes them from its launch flags; player
 * 1's takes them while it shares the screen, when the host's config asks for {@code presetForPlayer1}.
 *
 * <p>None of it is the player's: whenever the game saves {@code options.txt} (closing an options screen, for one), each
 * option's own value is written instead ({@code SessionOptionsSaveMixin}), and the session's comes back before the
 * server is told the settings. An option the player changes themselves this session is theirs from then on: it is saved
 * as it is, and never put back. Light graphics only ever make a setting lighter. Render thread.
 */
public final class SessionOptions {
	public static final String CAP_PROPERTY = "hearthside.renderDistanceCap";
	public static final String LIGHT_PROPERTY = "hearthside.lightGraphics";
	public static final String FPS_CAP_PROPERTY = "hearthside.fpsCap";

	// The options a session sets, named as in options.txt.
	private static final String RENDER_DISTANCE = "renderDistance";
	private static final String MAX_FPS = "maxFps";
	private static final String CUTOUT_LEAVES = "cutoutLeaves";
	private static final String CLOUDS = "renderClouds";
	private static final String PARTICLES = "particles";
	private static final String ENTITY_DISTANCE = "entityDistanceScaling";
	private static final String BIOME_BLEND = "biomeBlendRadius";
	/** Setting any of the others makes Minecraft call the graphics "Custom"; the player's own preset is what's saved. */
	private static final String GRAPHICS_PRESET = "graphicsPreset";
	private static final List<String> KEYS = List.of(RENDER_DISTANCE, MAX_FPS, CUTOUT_LEAVES, CLOUDS, PARTICLES,
			ENTITY_DISTANCE, BIOME_BLEND, GRAPHICS_PRESET);
	/** Light graphics' entity distance, of the player's own. */
	private static final double LIGHT_ENTITY_DISTANCE = 0.75;
	/** Minecraft's frame-rate slider: 260 is "Unlimited". */
	private static final int MIN_FPS = 10, MAX_FPS_SETTING = 260;

	/** The options this session has set: each one's own value and the session's. */
	private static SessionOverrides<String> overrides = new SessionOverrides<>();
	/** This session's frame-rate cap; 0 means the host's refresh rate. */
	private static int fpsCap;

	private SessionOptions() {}

	/**
	 * A guest's game, once its options are loaded: the host's tuning from its launch flags. Logs the guest's heap and
	 * render distance, and the session's graphics and frame-rate cap.
	 */
	static void applyForGuest(Minecraft minecraft) {
		Options options = minecraft.options;
		int cap = Integer.getInteger(CAP_PROPERTY, 0);
		boolean light = Boolean.getBoolean(LIGHT_PROPERTY);
		int mine = options.renderDistance().get();
		apply(options, cap, light, Math.max(0, Integer.getInteger(FPS_CAP_PROPERTY, 0)));
		if (options.renderDistance().get() < mine) {
			HearthsideMod.LOGGER.info("Hearthside capped the render distance at {} for this session (own setting {})",
					options.renderDistance().get(), mine);
		}
		// The frame-rate limit in effect, read back (0: no cap, the host's refresh rate).
		HearthsideMod.LOGGER.info("Hearthside session options light={} fpsCap={}", light,
				fpsCap > 0 ? framerateInEffect() : 0);
		HearthsideMod.LOGGER.info("Hearthside guest heap max={} MB render distance={}",
				Runtime.getRuntime().maxMemory() / (1024 * 1024), options.renderDistance().get());
	}

	/** Player 1's game takes the session's render-distance cap, light graphics and frame-rate cap (plan R4). */
	public static void applyForPlayer1(Options options, SessionTuning tuning) {
		apply(options, tuning.renderDistanceCap(), tuning.lightGraphics(), tuning.fpsCap());
		HearthsideMod.LOGGER.info("Hearthside player 1 takes the preset: render distance {} light={} fpsCap={}",
				options.renderDistance().get(), tuning.lightGraphics(),
				tuning.fpsCap() > 0 ? framerateInEffect() : 0);
	}

	private static void apply(Options options, int renderDistanceCap, boolean light, int frameRateCap) {
		fpsCap = frameRateCap;
		keepingPreset(options, () -> {
			if (renderDistanceCap > 0 && renderDistanceCap < options.renderDistance().get()) {
				session(options.renderDistance(), RENDER_DISTANCE, renderDistanceCap);
			}
			if (light) {
				session(options.cutoutLeaves(), CUTOUT_LEAVES, false);
				session(options.cloudStatus(), CLOUDS, CloudStatus.OFF);
				if (options.particles().get() == ParticleStatus.ALL) {
					session(options.particles(), PARTICLES, ParticleStatus.DECREASED);
				}
				if (options.entityDistanceScaling().get() > LIGHT_ENTITY_DISTANCE) {
					session(options.entityDistanceScaling(), ENTITY_DISTANCE, LIGHT_ENTITY_DISTANCE);
				}
				session(options.biomeBlendRadius(), BIOME_BLEND, 0);
			}
			if (fpsCap > 0 && options.framerateLimit().get() > fpsCap) {
				session(options.framerateLimit(), MAX_FPS, fpsCap);
			}
		});
	}

	/**
	 * A guest's game paces itself to the host's display ({@code refreshHz}), or to the session's lower frame-rate cap. For
	 * the session only, like the rest. Each new viewport sets it again; once the player has chosen their own Max
	 * Framerate this session, it paces to that instead (never above the display's rate).
	 */
	public static void limitFramerate(Options options, int refreshHz) {
		releaseIfChanged(MAX_FPS, options.framerateLimit().get());
		int chosen = overrides.playersChoice(MAX_FPS) instanceof Integer fps ? fps : FrameRatePacing.NO_CHOICE;
		int limit = Math.clamp(FrameRatePacing.limit(refreshHz, fpsCap, chosen), MIN_FPS, MAX_FPS_SETTING);
		int before = framerateInEffect();
		keepingPreset(options, () -> session(options.framerateLimit(), MAX_FPS, limit));
		if (framerateInEffect() != before) {
			HearthsideMod.LOGGER.info("Hearthside frame-rate limit {}", framerateInEffect());
		}
	}

	/**
	 * Player 1's session is over: each option the session still holds goes back to their own value (one they changed
	 * themselves stays as it is), and nothing is overridden any more.
	 */
	public static void restore(Options options) {
		if (!overrides.active()) {
			return;
		}
		SessionOverrides<String> ending = overrides;
		overrides = new SessionOverrides<>();
		fpsCap = 0;
		// The graphics preset last: putting the others back calls the graphics "Custom" again.
		for (String key : KEYS) {
			OptionInstance<?> option = option(options, key);
			Object current = option.get();
			Object own = ending.valueToSave(key, current);
			if (!Objects.equals(own, current)) {
				put(option, key, own);
			}
		}
		HearthsideMod.LOGGER.info("Hearthside player 1's own options are back");
	}

	/** Whether this session overrides any option (then {@code options.txt} is written with the player's own values). */
	public static boolean active() {
		return overrides.active();
	}

	/** Before {@code options.txt} is written: each option's own value, unless the player has changed it this session. */
	public static void beforeSave(Options options) {
		if (!overrides.active()) {
			return;
		}
		for (String key : KEYS) {
			OptionInstance<?> option = option(options, key);
			Object current = option.get();
			Object saved = overrides.valueToSave(key, current);
			if (!Objects.equals(saved, current)) {
				// Straight into the field: set() would make the world re-render its chunks, twice for each save.
				((OptionInstanceAccessor) (Object) option).hearthside$setValue(saved);
			}
		}
	}

	/**
	 * After {@code options.txt} is written, before the server is told the settings: the session's values again, so the
	 * host's server only ever loads and sends chunks for the cap. Calling it twice changes nothing.
	 */
	public static void afterSave(Options options) {
		for (String key : KEYS) {
			Object session = overrides.valueAfterSave(key);
			if (session != null) {
				((OptionInstanceAccessor) (Object) option(options, key)).hearthside$setValue(session);
			}
		}
	}

	/**
	 * Sets an option for the session. If the player changed it themselves since the session last set it, theirs is now
	 * the own value kept for saving.
	 */
	private static <T> void session(OptionInstance<T> option, String key, T value) {
		T current = option.get();
		releaseIfChanged(key, current);
		if (Objects.equals(current, value) && overrides.valueAfterSave(key) == null) {
			return; // already the player's own value
		}
		put(option, key, value);
		overrides.apply(key, current, option.get());
	}

	/**
	 * Sets an option as the player would, with two exceptions that go straight into the option's field. The graphics
	 * preset: set() would apply the whole preset over the player's own options. The frame rate: its slider (vanilla's,
	 * and Sodium's, which puts a value off its steps back to its default of 60) holds only tens, so set(45) would give 40
	 * and a 144 Hz display 140. The option holds the rate rounded down to tens, and Minecraft's frame-rate limiter, all
	 * the slider's own listener ever sets, the exact rate.
	 */
	private static void put(OptionInstance<?> option, String key, Object value) {
		if (key.equals(MAX_FPS)) {
			int rate = (Integer) value;
			((OptionInstanceAccessor) (Object) option).hearthside$setValue(Math.max(MIN_FPS, rate / 10 * 10));
			Minecraft.getInstance().getFramerateLimitTracker().setFramerateLimit(rate);
		} else if (key.equals(GRAPHICS_PRESET)) {
			((OptionInstanceAccessor) (Object) option).hearthside$setValue(value);
		} else {
			set(option, value);
		}
	}

	/** Runs {@code change}; if it moved the graphics preset (to Custom), the player's own preset is what's saved. */
	private static void keepingPreset(Options options, Runnable change) {
		OptionInstance<GraphicsPreset> preset = options.graphicsPreset();
		GraphicsPreset own = preset.get();
		releaseIfChanged(GRAPHICS_PRESET, own);
		change.run();
		if (preset.get() != own || overrides.valueAfterSave(GRAPHICS_PRESET) != null) {
			overrides.apply(GRAPHICS_PRESET, own, preset.get());
		}
	}

	/** The player changed {@code key} away from the session's value: it is theirs from now on. */
	private static void releaseIfChanged(String key, Object current) {
		Object session = overrides.valueAfterSave(key);
		if (session != null && !session.equals(current)) {
			overrides.valueToSave(key, current);
		}
	}

	/** The frame-rate limit Minecraft paces to (before throttling for a menu or an idle player), read back. */
	private static int framerateInEffect() {
		return ((FramerateLimitTrackerAccessor) Minecraft.getInstance().getFramerateLimitTracker()).hearthside$framerateLimit();
	}

	@SuppressWarnings("unchecked")
	private static <T> void set(OptionInstance<T> option, Object value) {
		option.set((T) value);
	}

	private static OptionInstance<?> option(Options options, String key) {
		return switch (key) {
			case RENDER_DISTANCE -> options.renderDistance();
			case MAX_FPS -> options.framerateLimit();
			case CUTOUT_LEAVES -> options.cutoutLeaves();
			case CLOUDS -> options.cloudStatus();
			case PARTICLES -> options.particles();
			case ENTITY_DISTANCE -> options.entityDistanceScaling();
			case BIOME_BLEND -> options.biomeBlendRadius();
			case GRAPHICS_PRESET -> options.graphicsPreset();
			default -> throw new IllegalArgumentException(key);
		};
	}
}
