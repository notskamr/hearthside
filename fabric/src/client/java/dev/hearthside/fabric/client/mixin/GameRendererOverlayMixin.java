package dev.hearthside.fabric.client.mixin;

import dev.hearthside.fabric.client.compositor.OverlayPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After the host's frame (world and GUI): Hearthside's overlay pass over every view, while compositing. */
@Mixin(GameRenderer.class)
abstract class GameRendererOverlayMixin {
	@Inject(method = "render()V", at = @At("TAIL"))
	private void hearthside$overlay(CallbackInfo ci) {
		OverlayPass.run(Minecraft.getInstance(), (GameRenderer) (Object) this);
	}
}
