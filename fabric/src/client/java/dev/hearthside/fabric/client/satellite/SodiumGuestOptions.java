package dev.hearthside.fabric.client.satellite;

import dev.hearthside.fabric.HearthsideMod;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.client.gui.options.FullscreenMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Sodium replaces Video Settings with its own, whose Fullscreen is one setting (off, borderless or exclusive). On a
 * guest's game it is greyed out with the reason, as vanilla's is ({@link GuestVideoSettings}); its Fullscreen
 * Resolution already greys out while Fullscreen is off. Through Sodium's config API (the {@code sodium:config_api_user}
 * entrypoint), so it is only loaded where Sodium is installed; on player 1's game it adds nothing.
 */
public final class SodiumGuestOptions implements ConfigEntryPoint {
	private static final Identifier FULLSCREEN = Identifier.parse("sodium:general.fullscreen_mode");

	@Override
	public void registerConfigLate(ConfigBuilder builder) {
		// Late: Sodium builds its Video Settings (and so its Fullscreen) late; an overlay on nothing is an error.
		if (!GuestFullscreen.onGuest()) {
			return;
		}
		builder.registerOwnModOptions().registerOptionOverlay(FULLSCREEN, builder.createEnumOption(FULLSCREEN, FullscreenMode.class)
				.setEnabled(false)
				.setTooltip(Component.translatable(GuestVideoSettings.TOOLTIP_KEY)));
		HearthsideMod.LOGGER.info("Hearthside guest Video Settings: Sodium's Fullscreen greyed out");
	}
}
