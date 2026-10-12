package dev.hearthside.fabric.client.satellite;

import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.mixin.OptionsSubScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;

/**
 * A guest's Video Settings show Fullscreen (and Exclusive Fullscreen, which only matters there) greyed out, with a
 * tooltip that says why: {@link GuestFullscreen} refuses it anyway, and a button that does nothing would look broken.
 * Sodium's own Video Settings do the same through {@code SodiumGuestOptions}.
 */
public final class GuestVideoSettings {
	/** Why Fullscreen is greyed out on a guest's game. */
	public static final String TOOLTIP_KEY = "hearthside.options.fullscreen.guest";

	private GuestVideoSettings() {}

	/** On a guest's game. Each time Video Settings are built (they are rebuilt on a resize). */
	public static void register() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof VideoSettingsScreen) {
				greyOut(minecraft, screen);
			}
		});
	}

	private static void greyOut(Minecraft minecraft, Screen screen) {
		OptionsList list = ((OptionsSubScreenAccessor) screen).hearthside$list();
		if (list == null) {
			return;
		}
		Tooltip why = Tooltip.create(Component.translatable(TOOLTIP_KEY));
		for (OptionInstance<?> option : new OptionInstance<?>[] {minecraft.options.fullscreen(),
				minecraft.options.exclusiveFullscreen()}) {
			AbstractWidget widget = list.findOption(option);
			if (widget != null) {
				widget.active = false;
				widget.setTooltip(why);
			}
		}
		AbstractWidget fullscreen = list.findOption(minecraft.options.fullscreen());
		HearthsideMod.LOGGER.info("Hearthside guest Video Settings: Fullscreen active={}",
				fullscreen == null ? "absent" : fullscreen.active);
	}
}
