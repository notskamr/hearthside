package dev.hearthside.fabric.client.mixin;

import dev.hearthside.fabric.client.menu.PauseMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Vanilla's pause screen opened from Hearthside's pause menu (Game menu…) goes back to that menu when it closes to
 * play (Back to Game, Esc, B), as Options… does. Any other pause screen closes as vanilla's always has.
 */
@Mixin(Gui.class)
abstract class GameMenuReturnMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen hearthside$backToPauseMenu(Screen screen) {
		if (screen != null) {
			return screen;
		}
		return PauseMenuScreen.instead(Minecraft.getInstance(), ((Gui) (Object) this).screen());
	}
}
