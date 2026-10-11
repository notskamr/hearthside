package dev.hearthside.fabric.client.mixin;

import com.mojang.blaze3d.platform.SDLEventHandler;
import dev.hearthside.core.Role;
import dev.hearthside.fabric.client.HearthsideClient;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A satellite belongs to one player's controller: the real keyboard and mouse (shared by every
 * window on the PC) must never reach it. Window events still flow.
 */
@Mixin(SDLEventHandler.class)
abstract class SatelliteInputMixin {
	@Inject(method = {"handleKeyEvent", "handleTextInputEvent", "handleTextEditingEvent", "handleMouseMotionEvent",
			"handleMouseButtonEvent", "handleMouseWheelEvent"}, at = @At("HEAD"), cancellable = true)
	private void hearthside$ignoreOsInput(SDL_Event event, CallbackInfo ci) {
		if (HearthsideClient.role() == Role.SATELLITE) {
			ci.cancel();
		}
	}
}
