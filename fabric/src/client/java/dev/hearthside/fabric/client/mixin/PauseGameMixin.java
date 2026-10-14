package dev.hearthside.fabric.client.mixin;

import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.menu.PauseMenuScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The pause key during split-screen opens each player's own pause menu (Spec E section 3.1) instead of vanilla's
 * pause screen, which stays as it is, one button away. Alone, and for a pause without a menu, it's vanilla's. The menu
 * is opened before vanilla's path is cancelled: if anything about it fails, vanilla's pause screen opens instead, and
 * does from then on.
 */
@Mixin(Minecraft.class)
abstract class PauseGameMixin {
	private static boolean hearthside$failed;

	@Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
	private void hearthside$ownPauseMenu(boolean suppressPauseMenu, CallbackInfo ci) {
		Minecraft minecraft = (Minecraft) (Object) this;
		if (hearthside$failed || suppressPauseMenu || minecraft.gui.screen() != null) {
			return;
		}
		PauseMenuScreen menu = null;
		try {
			if (!PauseMenuScreen.replacesVanilla(minecraft)) {
				return;
			}
			menu = new PauseMenuScreen();
			minecraft.gui.setScreen(menu);
		} catch (RuntimeException e) {
			hearthside$failed(minecraft, menu, e);
			return;
		}
		ci.cancel();
		// As vanilla's pauseGame does.
		if (minecraft.gameMode != null) {
			minecraft.gameMode.stopDestroyBlock();
		}
	}

	/** Vanilla's pause from here on: a half-opened menu is closed, so vanilla's pause screen can open in its place. */
	private static void hearthside$failed(Minecraft minecraft, PauseMenuScreen menu, RuntimeException e) {
		hearthside$failed = true;
		HearthsideMod.LOGGER.error("Hearthside couldn't open its pause menu; Minecraft's opens instead from now on", e);
		if (menu != null && minecraft.gui.screen() == menu) {
			try {
				minecraft.gui.setScreen(null);
			} catch (RuntimeException closing) {
				HearthsideMod.LOGGER.error("Hearthside couldn't close its half-opened pause menu", closing);
			}
		}
	}
}
