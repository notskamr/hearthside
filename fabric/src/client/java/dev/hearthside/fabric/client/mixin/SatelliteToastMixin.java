package dev.hearthside.fabric.client.mixin;

import dev.hearthside.core.Role;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.HearthsideClient;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A guest's game plays offline on player 1's own machine, so Minecraft's "Chat messages can't be verified" warning
 * would greet every guest the moment they arrive; it means nothing to them, so their game skips it.
 */
@Mixin(ToastManager.class)
abstract class SatelliteToastMixin {
	@Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
	private void hearthside$quiet(Toast toast, CallbackInfo ci) {
		if (HearthsideClient.role() == Role.SATELLITE && toast instanceof SystemToast system
				&& system.getToken() == SystemToast.SystemToastId.UNSECURE_SERVER_WARNING) {
			HearthsideMod.LOGGER.info("Hearthside skipped the chat-verification toast");
			ci.cancel();
		}
	}
}
