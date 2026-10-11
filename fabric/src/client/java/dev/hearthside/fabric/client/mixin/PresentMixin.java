package dev.hearthside.fabric.client.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import dev.hearthside.fabric.client.compositor.FrameExporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft's OpenGL blit of its finished frame to the window. A compositing satellite hands the frame to the host
 * instead (its window is hidden and never swapped). The host side is {@code HostPresentMixin}, on both backends.
 */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
abstract class PresentMixin {
	@Inject(method = "presentTexture", at = @At("HEAD"), cancellable = true)
	private void hearthside$export(long windowHandle, GpuTextureView textureView, int swapchainWidth,
			int swapchainHeight, CallbackInfo ci) {
		FrameExporter exporter = FrameExporter.instance();
		if (exporter != null) {
			GlStateManager._disableScissorTest();
			exporter.export(((GlTexture) textureView.texture()).glId(), textureView.getWidth(0), textureView.getHeight(0));
			ci.cancel();
		}
	}
}
