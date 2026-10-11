package dev.hearthside.fabric.client.mixin.backend;

import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import dev.hearthside.fabric.client.compositor.backend.SharedImages;
import dev.hearthside.fabric.client.compositor.backend.VulkanSharedImages;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Asks Minecraft's Vulkan device for Win32 external memory, so shared slot images can be imported. The request is
 * optional, like Minecraft's own: a device without the extension still starts, and the adapter reports that it can't
 * import (Spec C section 3.5).
 */
@Mixin(VulkanFeatureSets.class)
abstract class VulkanFeatureSetsMixin {
	@Inject(method = "optionalFeatureSets", at = @At("RETURN"))
	private static void hearthside$askForExternalMemory(CallbackInfoReturnable<Set<FeatureSet>> cir) {
		if (SharedImages.windows()) {
			cir.getReturnValue().add(new FeatureSet(VulkanSharedImages.FEATURE_SET,
					Set.of(VulkanSharedImages.EXTERNAL_MEMORY_WIN32), Set.of()));
		}
	}
}
