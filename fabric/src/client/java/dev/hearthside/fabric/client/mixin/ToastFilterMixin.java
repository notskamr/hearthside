package dev.hearthside.fabric.client.mixin;

import dev.hearthside.core.Role;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.HearthsideClient;
import dev.hearthside.fabric.client.host.Party;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Toasts that mean nothing to the player they'd greet.
 * <ul>
 * <li>A guest's game plays offline on player 1's own machine, so Minecraft's "Chat messages can't be verified" warning
 * would greet every guest the moment they arrive; their game skips it.
 * <li>Minecraft's tutorial toasts ("Move with W, A, S and D", "Use your mouse to turn") are for keyboard and mouse
 * (Spec D section 3.5): a guest never sees them, nor player 1 while playing on a controller. The tutorial itself runs
 * on, and the player's tutorial progress is left alone.
 * </ul>
 */
@Mixin(ToastManager.class)
abstract class ToastFilterMixin {
	@Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
	private void hearthside$filter(Toast toast, CallbackInfo ci) {
		boolean satellite = HearthsideClient.role() == Role.SATELLITE;
		if (satellite && toast instanceof SystemToast system
				&& system.getToken() == SystemToast.SystemToastId.UNSECURE_SERVER_WARNING) {
			HearthsideMod.LOGGER.info("Hearthside skipped the chat-verification toast");
			ci.cancel();
		} else if (toast instanceof TutorialToast && (satellite || hostOnController())) {
			HearthsideMod.LOGGER.info("Hearthside skipped a keyboard tutorial toast for a controller player");
			ci.cancel();
		}
	}

	private static boolean hostOnController() {
		Party party = Party.current();
		return party != null && party.hostOnController();
	}
}
