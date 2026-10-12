package dev.hearthside.fabric.client.mixin;

import dev.hearthside.fabric.client.satellite.SessionOptions;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A preset's options last the session only, on a guest's game or player 1's: {@code options.txt} keeps the player's
 * own. The session's values are back before {@code save} tells the server the settings ({@code broadcastOptions}, its
 * last call), so the server never hears a guest's own, higher, render distance and never loads chunks for it.
 */
@Mixin(Options.class)
abstract class SessionOptionsSaveMixin {
	@Inject(method = "save", at = @At("HEAD"))
	private void hearthside$saveOwnOptions(CallbackInfo ci) {
		if (SessionOptions.active()) {
			SessionOptions.beforeSave((Options) (Object) this);
		}
	}

	@Inject(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;broadcastOptions()V"))
	private void hearthside$sessionBeforeBroadcast(CallbackInfo ci) {
		if (SessionOptions.active()) {
			SessionOptions.afterSave((Options) (Object) this);
		}
	}

	/** At the return too: a no-op once the session's values are back, it covers any way out of {@code save} that skips the broadcast. */
	@Inject(method = "save", at = @At("RETURN"))
	private void hearthside$sessionAgain(CallbackInfo ci) {
		if (SessionOptions.active()) {
			SessionOptions.afterSave((Options) (Object) this);
		}
	}
}
