package dev.hearthside.fabric.client.mixin;

import com.mojang.renderpearl.backend.opengl.GlSurface;
import dev.hearthside.fabric.client.compositor.FrameExporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A compositing satellite never swaps its hidden window; its frames went to the host. */
@Mixin(GlSurface.class)
abstract class SatelliteSwapMixin {
	@Inject(method = "present", at = @At("HEAD"), cancellable = true)
	private void hearthside$noSwapWhenExporting(CallbackInfo ci) {
		if (FrameExporter.instance() != null) {
			ci.cancel();
		}
	}
}
