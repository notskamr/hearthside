package dev.hearthside.fabric.client.mixin;

import dev.hearthside.fabric.client.menu.PauseMenuScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The pause key during split-screen opens each player's own pause menu (Spec E section 3.1) instead of vanilla's
 * pause screen, which stays as it is, one button away. Alone, and for a pause without a menu, it's vanilla's.
 */
@Mixin(Minecraft.class)
abstract class PauseGameMixin {
	@Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
	private void hearthside$ownPauseMenu(boolean suppressPauseMenu, CallbackInfo ci) {
		Minecraft minecraft = (Minecraft) (Object) this;
		if (suppressPauseMenu || minecraft.gui.screen() != null || !PauseMenuScreen.replacesVanilla(minecraft)) {
			return;
		}
		ci.cancel();
		minecraft.gui.setScreen(new PauseMenuScreen());
		// As vanilla's pauseGame does.
		if (minecraft.gameMode != null) {
			minecraft.gameMode.stopDestroyBlock();
		}
	}
}
