package dev.hearthside.fabric.client.satellite;

import dev.hearthside.fabric.HearthsideMod;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.client.gui.options.FullscreenMode;
import net.fabricmc.loader.api.Version;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Sodium replaces Video Settings with its own, whose Fullscreen is one setting (off, borderless or exclusive). On a
 * guest's game it is greyed out with the reason, as vanilla's is ({@link GuestVideoSettings}); its Fullscreen
 * Resolution already greys out while Fullscreen is off. Through Sodium's config API (the {@code sodium:config_api_user}
 * entrypoint), so it is only loaded where Sodium is installed; on player 1's game it adds nothing.
 *
 * <p>The greying is only how it looks: {@code GuestFullscreenOptionMixin} and its siblings refuse fullscreen whatever
 * Sodium shows. So it is only done on a Sodium Hearthside was tested with ({@link SodiumVersion}), and anything going
 * wrong while it is set up leaves Sodium's options alone: Sodium turns an exception here into a crash, and a class or
 * method a newer Sodium lacks would end the game too.
 */
public final class SodiumGuestOptions implements ConfigEntryPoint {
	private static final Identifier FULLSCREEN = Identifier.parse("sodium:general.fullscreen_mode");
	private static final String LEFT_ALONE = "Hearthside left Sodium's video options alone: ";

	@Override
	public void registerConfigLate(ConfigBuilder builder) {
		// Late: Sodium builds its Video Settings (and so its Fullscreen) late; an overlay on nothing is an error.
		if (!GuestFullscreen.onGuest()) {
			return;
		}
		String version = "of an unknown version";
		try {
			Version sodium = SodiumVersion.installed();
			if (sodium != null) {
				version = sodium.getFriendlyString();
			}
			if (!SodiumVersion.tested(sodium)) {
				HearthsideMod.LOGGER.info("{}Sodium {} is untested (tested: Sodium {})", LEFT_ALONE, version, SodiumVersion.TESTED);
				return;
			}
			// Built before anything is registered, so a failure part way registers nothing.
			var fullscreen = builder.createEnumOption(FULLSCREEN, FullscreenMode.class)
					.setEnabled(false)
					.setTooltip(Component.translatable(GuestVideoSettings.TOOLTIP_KEY));
			builder.registerOwnModOptions().registerOptionOverlay(FULLSCREEN, fullscreen);
		} catch (Throwable t) {
			HearthsideMod.LOGGER.warn("{}Sodium {} failed ({})", LEFT_ALONE, version, t.toString(), t);
			return;
		}
		HearthsideMod.LOGGER.info("Hearthside guest Video Settings: Sodium's Fullscreen greyed out");
	}
}
