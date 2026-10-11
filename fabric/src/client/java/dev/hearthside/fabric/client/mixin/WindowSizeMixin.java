package dev.hearthside.fabric.client.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.hearthside.fabric.client.compositor.VirtualSizing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every place Minecraft writes the real window size into its size fields: put the virtual size back afterwards. */
@Mixin(Window.class)
abstract class WindowSizeMixin {
	@Inject(method = {"onResize", "onFramebufferResize", "refreshFramebufferSize", "syncWindow"}, at = @At("RETURN"))
	private void hearthside$keepVirtualSize(CallbackInfo ci) {
		VirtualSizing.reapply((Window) (Object) this);
	}
}
