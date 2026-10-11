package dev.hearthside.fabric.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.hearthside.core.Role;
import dev.hearthside.fabric.client.HearthsideClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A satellite must never capture or move the one OS cursor that every window shares. */
@Mixin(InputConstants.class)
abstract class SatelliteGrabMixin {
	@Inject(method = {"grabMouse", "releaseMouse"}, at = @At("HEAD"), cancellable = true)
	private static void hearthside$leaveOsCursorAlone(Window window, double x, double y, CallbackInfo ci) {
		if (HearthsideClient.role() == Role.SATELLITE) {
			ci.cancel();
		}
	}
}
