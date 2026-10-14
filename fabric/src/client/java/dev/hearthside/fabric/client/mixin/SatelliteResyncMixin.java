package dev.hearthside.fabric.client.mixin;

import dev.hearthside.core.Role;
import dev.hearthside.fabric.client.HearthsideClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every screen change re-reads the OS cursor's position. In a satellite that cursor is player 1's, wherever it happens
 * to be: a screen would open with something under it lit as hovered. A satellite's mouse is only ever its controller's
 * pointer (or nowhere, on Hearthside's own screens).
 */
@Mixin(MouseHandler.class)
abstract class SatelliteResyncMixin {
	@Inject(method = "resyncMousePosition", at = @At("HEAD"), cancellable = true)
	private void hearthside$noOsCursor(CallbackInfo ci) {
		if (HearthsideClient.role() == Role.SATELLITE) {
			ci.cancel();
		}
	}
}
