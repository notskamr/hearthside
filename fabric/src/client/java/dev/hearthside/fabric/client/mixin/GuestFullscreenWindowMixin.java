package dev.hearthside.fabric.client.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.hearthside.fabric.client.satellite.GuestFullscreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A guest's window is never made fullscreen: a request to the window itself is refused, and so is one it was created
 * with (the game's {@code --fullscreen} argument); applying the mode is where both would take effect.
 */
@Mixin(Window.class)
abstract class GuestFullscreenWindowMixin {
	@Shadow
	private boolean fullscreenRequested;

	@Inject(method = "setFullscreen", at = @At("HEAD"), cancellable = true)
	private void hearthside$refuseRequest(boolean fullscreen, CallbackInfo ci) {
		if (fullscreen && GuestFullscreen.onGuest()) {
			GuestFullscreen.refused("a request to the window");
			ci.cancel();
		}
	}

	@Inject(method = "setMode", at = @At("HEAD"))
	private void hearthside$staysWindowed(CallbackInfo ci) {
		if (fullscreenRequested && GuestFullscreen.onGuest()) {
			GuestFullscreen.refused("the window was to open fullscreen");
			fullscreenRequested = false;
		}
	}
}
