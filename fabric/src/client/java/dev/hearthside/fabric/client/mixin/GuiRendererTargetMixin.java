package dev.hearthside.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.hearthside.fabric.client.compositor.OverlayPass;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** During Hearthside's overlay pass the GUI renderer draws into the overlay's target, not the host's own. */
@Mixin(GuiRenderer.class)
abstract class GuiRendererTargetMixin {
	@WrapOperation(method = "draw", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
	private RenderTarget hearthside$overlayTarget(GameRenderer gameRenderer, Operation<RenderTarget> original) {
		return OverlayPass.active() ? OverlayPass.target() : original.call(gameRenderer);
	}
}
