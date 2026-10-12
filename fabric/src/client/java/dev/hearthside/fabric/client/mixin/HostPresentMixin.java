package dev.hearthside.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.hearthside.fabric.client.compositor.Compositor;
import dev.hearthside.fabric.client.compositor.FrameExporter;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Minecraft's one blit of its finished frame to the window, on either backend. A compositing host hands over its
 * canvas, with every player's view drawn in, instead of player 1's frame (Spec C section 3.3). Satellites pass through
 * untouched: their exporter still takes the frame at the OpenGL present ({@code PresentMixin}).
 */
@Mixin(Minecraft.class)
abstract class HostPresentMixin {
	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE",
			target = "Lcom/mojang/renderpearl/api/device/GpuSurface;blitFromTexture(Lcom/mojang/renderpearl/api/commands/CommandEncoder;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
	private void hearthside$presentCanvas(GpuSurface surface, CommandEncoder encoder, GpuTextureView view,
			Operation<Void> original) {
		Compositor compositor = Compositor.active();
		GpuTextureView shown = view;
		if (compositor != null && FrameExporter.instance() == null) {
			GpuSurface.Configuration config = surface.currentConfiguration().orElse(null);
			if (config != null) {
				shown = compositor.compose(view, encoder, config.width(), config.height());
			}
		}
		original.call(surface, encoder, shown);
	}

	/**
	 * A minimised compositing host takes no window image (there is nothing to show it in), rather than letting the
	 * acquire fail and Minecraft log the surface exception on every minimise. Restoring reconfigures the surface as
	 * usual, and the next frame is composited again.
	 */
	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE",
			target = "Lcom/mojang/renderpearl/api/device/GpuSurface;acquireNextTexture()V"))
	private void hearthside$noImageWhileMinimised(GpuSurface surface, Operation<Void> original) {
		if (Compositor.active() != null && FrameExporter.instance() == null && minimised(((Minecraft) (Object) this).getWindow())) {
			return;
		}
		original.call(surface);
	}

	/**
	 * Minecraft's flag (its OpenGL surface refuses an image while it is set) or SDL's: each changes before the other, as
	 * Minecraft only hears of a minimise or restore when it next polls events.
	 */
	private static boolean minimised(Window window) {
		return window.isIconified() || (SDLVideo.SDL_GetWindowFlags(window.handle()) & SDLVideo.SDL_WINDOW_MINIMIZED) != 0;
	}
}
