package dev.hearthside.fabric.client.satellite;

import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.mixin.OptionInstanceAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

/**
 * The host's render-distance cap on a guest's game ({@code -Dhearthside.renderDistanceCap}, 0 = none): the guest renders
 * at most that many chunks this run, never more than its own setting.
 *
 * <p>The cap is a session setting, not the guest's: whenever the game saves {@code options.txt} (closing an options
 * screen, for one), the guest's own value is written instead, so a later session with a higher cap, or none, gets it
 * back. If the player moves the slider themselves, their choice is kept and saved as usual.
 */
public final class SatelliteRenderDistance {
	public static final String CAP_PROPERTY = "hearthside.renderDistanceCap";

	/** The guest's own setting while the cap stands in for it; null when there's nothing to restore. Render thread. */
	private static Integer own;
	private static int capped;

	private SatelliteRenderDistance() {}

	/** Caps the render distance (once options are loaded) and logs the guest's heap and render distance. */
	static void apply(Minecraft minecraft) {
		int cap = Integer.getInteger(CAP_PROPERTY, 0);
		OptionInstance<Integer> renderDistance = minecraft.options.renderDistance();
		int mine = renderDistance.get();
		if (cap > 0 && cap < mine) {
			own = mine;
			capped = cap;
			renderDistance.set(cap);
			HearthsideMod.LOGGER.info("Hearthside capped the render distance at {} for this session (own setting {})", cap, mine);
		}
		HearthsideMod.LOGGER.info("Hearthside guest heap max={} MB render distance={}",
				Runtime.getRuntime().maxMemory() / (1024 * 1024), renderDistance.get());
	}

	/** Before {@code options.txt} is written: put the guest's own setting back, unless the player has changed it. */
	public static void beforeSave(Options options) {
		if (own == null) {
			return;
		}
		if (options.renderDistance().get() != capped) {
			own = null; // the player chose a value of their own this session: it saves as it is
			return;
		}
		// Straight into the field: set() would make the world re-render its chunks, twice for each save.
		((OptionInstanceAccessor) (Object) options.renderDistance()).hearthside$setValue(own);
	}

	/** After {@code options.txt} is written: the cap again. */
	public static void afterSave(Options options) {
		if (own != null) {
			((OptionInstanceAccessor) (Object) options.renderDistance()).hearthside$setValue(capped);
		}
	}
}
