package dev.hearthside.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.hearthside.fabric.client.compositor.FrameExporter;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A compositing satellite's frame, on either backend (Spec C section 3.4). It takes no image from its hidden window, so
 * Minecraft's surface is never acquired and Minecraft skips its own blit and present; at the blit, the exporter hands
 * the finished frame to the host instead. The host's side of the same calls is {@code HostPresentMixin}.
 */
@Mixin(Minecraft.class)
abstract class SatelliteFrameMixin {
	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE",
			target = "Lcom/mojang/renderpearl/api/device/GpuSurface;acquireNextTexture()V"))
	private void hearthside$noWindowImage(GpuSurface surface, Operation<Void> original) {
		if (FrameExporter.instance() != null) {
			return;
		}
		original.call(surface);
	}

	@Inject(method = "renderFrame", at = @At(value = "INVOKE_STRING",
			target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V", args = "ldc=swapchainBlit"))
	private void hearthside$export(boolean advanceGameTime, CallbackInfo ci) {
		FrameExporter exporter = FrameExporter.instance();
		if (exporter != null) {
			GpuTextureView color = ((Minecraft) (Object) this).gameRenderer.mainRenderTarget().getColorTextureView();
			if (color != null) {
				exporter.export(color);
			}
		}
	}
}
