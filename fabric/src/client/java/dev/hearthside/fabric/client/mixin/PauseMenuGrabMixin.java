package dev.hearthside.fabric.client.mixin;

import dev.hearthside.fabric.client.menu.PauseMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Grabbing the mouse closes the open screen. Vanilla's Back to Game grabs it right after closing to play, which, for
 * the pause screen Hearthside's pause menu opened, has just gone back to that menu instead: the menu stays open.
 */
@Mixin(MouseHandler.class)
abstract class PauseMenuGrabMixin {
	@Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
	private void hearthside$keepPauseMenu(CallbackInfo ci) {
		if (PauseMenuScreen.isOpen(Minecraft.getInstance())) {
			ci.cancel();
		}
	}
}
