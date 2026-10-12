package dev.hearthside.fabric.client.mixin;

import dev.hearthside.fabric.client.satellite.GuestFullscreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Toggle Fullscreen key (F11) does nothing on a guest's game. Refused here rather than at the option, or an open
 * Video Settings would show its Fullscreen button switched on.
 */
@Mixin(Minecraft.class)
abstract class GuestFullscreenToggleMixin {
	@Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true)
	private void hearthside$refuseToggle(CallbackInfo ci) {
		if (GuestFullscreen.onGuest()) {
			GuestFullscreen.refused("the Toggle Fullscreen key");
			ci.cancel();
		}
	}
}
