package dev.hearthside.fabric.client.mixin;

import dev.hearthside.core.Role;
import dev.hearthside.fabric.client.HearthsideClient;
import dev.hearthside.fabric.client.satellite.SatelliteRenderDistance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A guest's render-distance cap lasts the session only: {@code options.txt} keeps the guest's own setting. The cap is
 * back before {@code save} tells the server the guest's settings ({@code broadcastOptions}, its last call), so the
 * server never hears the guest's own, higher, render distance and never loads chunks for it.
 */
@Mixin(Options.class)
abstract class SatelliteOptionsSaveMixin {
	@Inject(method = "save", at = @At("HEAD"))
	private void hearthside$saveOwnRenderDistance(CallbackInfo ci) {
		if (HearthsideClient.role() == Role.SATELLITE) {
			SatelliteRenderDistance.beforeSave((Options) (Object) this);
		}
	}

	@Inject(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;broadcastOptions()V"))
	private void hearthside$capBeforeBroadcast(CallbackInfo ci) {
		if (HearthsideClient.role() == Role.SATELLITE) {
			SatelliteRenderDistance.afterSave((Options) (Object) this);
		}
	}

	/** At the return too: a no-op once the cap is back, it covers any way out of {@code save} that skips the broadcast. */
	@Inject(method = "save", at = @At("RETURN"))
	private void hearthside$capAgain(CallbackInfo ci) {
		if (HearthsideClient.role() == Role.SATELLITE) {
			SatelliteRenderDistance.afterSave((Options) (Object) this);
		}
	}
}
