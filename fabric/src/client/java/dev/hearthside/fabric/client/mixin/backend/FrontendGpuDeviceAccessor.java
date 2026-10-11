package dev.hearthside.fabric.client.mixin.backend;

import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The backend device behind {@code RenderSystem.getDevice()}: a {@code GlDevice} or a {@code VulkanDevice}. */
@Mixin(FrontendGpuDevice.class)
public interface FrontendGpuDeviceAccessor {
	@Accessor("backend")
	GpuDeviceBackend hearthside$backend();
}
