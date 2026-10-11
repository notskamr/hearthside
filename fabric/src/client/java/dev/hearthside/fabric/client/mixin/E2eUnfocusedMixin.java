package dev.hearthside.fabric.client.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.hearthside.core.Role;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Test only: with {@code HEARTHSIDE_E2E_GUEST_UNFOCUSED=1} in the environment, a satellite's window reports that it has
 * no focus, so the E2E exercises the unfocused path every run, whatever the OS does with focus.
 */
@Mixin(Window.class)
abstract class E2eUnfocusedMixin {
	private static final boolean FORCED = "1".equals(System.getenv("HEARTHSIDE_E2E_GUEST_UNFOCUSED"))
			&& Role.fromProperty(System.getProperty(Role.PROPERTY)) == Role.SATELLITE;

	@Inject(method = "isFocused", at = @At("HEAD"), cancellable = true)
	private void hearthside$forceUnfocused(CallbackInfoReturnable<Boolean> cir) {
		if (FORCED) {
			cir.setReturnValue(false);
		}
	}
}
