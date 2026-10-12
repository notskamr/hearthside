package dev.hearthside.fabric.client.mixin;

import dev.hearthside.fabric.client.satellite.GuestFullscreen;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A guest's Fullscreen option stays off: turning it on (from Video Settings, Sodium's settings or another mod) and
 * loading it on from {@code options.txt} are refused, so the window is never asked and the option never shows on.
 */
@Mixin(OptionInstance.class)
abstract class GuestFullscreenOptionMixin {
	@Shadow
	@Final
	private Component caption;

	@Inject(method = "set", at = @At("HEAD"), cancellable = true)
	private void hearthside$guestStaysWindowed(Object value, CallbackInfo ci) {
		if (Boolean.TRUE.equals(value) && GuestFullscreen.onGuest() && GuestFullscreen.isFullscreen(caption)) {
			GuestFullscreen.refused("the Fullscreen option");
			ci.cancel();
		}
	}
}
