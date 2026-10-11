package dev.hearthside.fabric.client.mixin.backend;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import dev.hearthside.fabric.client.compositor.backend.SlotMemory;
import dev.hearthside.fabric.client.compositor.backend.VulkanImport;
import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Builds a Minecraft Vulkan texture on imported shared memory instead of a VMA allocation, only while
 * {@link VulkanImport} holds a request on this thread (Spec C section 3.5). The texture keeps a null VMA allocation and
 * frees its own image and memory when Minecraft destroys it.
 */
@Mixin(VulkanGpuTexture.class)
abstract class VulkanGpuTextureImportMixin {
	@Shadow
	@Final
	private VulkanDevice device;

	/** The imported {@code VkDeviceMemory} behind this texture; 0 for Minecraft's own textures. */
	@Unique
	private long hearthside$importedMemory;

	@Redirect(method = "<init>", at = @At(value = "INVOKE",
			target = "Lorg/lwjgl/util/vma/Vma;vmaCreateImage(JLorg/lwjgl/vulkan/VkImageCreateInfo;Lorg/lwjgl/util/vma/VmaAllocationCreateInfo;Ljava/nio/LongBuffer;Lorg/lwjgl/PointerBuffer;Lorg/lwjgl/util/vma/VmaAllocationInfo;)I"))
	private int hearthside$importInstead(long allocator, VkImageCreateInfo imageInfo,
			VmaAllocationCreateInfo allocationInfo, LongBuffer image, PointerBuffer allocation,
			VmaAllocationInfo allocationResult) {
		SlotMemory memory = VulkanImport.take();
		if (memory == null) {
			return Vma.vmaCreateImage(allocator, imageInfo, allocationInfo, image, allocation, allocationResult);
		}
		// The allocation handle stays 0; a failure throws out of the constructor and leaves nothing behind.
		hearthside$importedMemory = VulkanImport.createImage(device.vkDevice(), imageInfo, memory, image);
		return VK10.VK_SUCCESS;
	}

	@Redirect(method = "destroy", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/vma/Vma;vmaDestroyImage(JJJ)V"))
	private void hearthside$destroyImported(long allocator, long image, long allocation) {
		if (hearthside$importedMemory == 0) {
			Vma.vmaDestroyImage(allocator, image, allocation);
			return;
		}
		VulkanImport.destroy(device.vkDevice(), image, hearthside$importedMemory);
		hearthside$importedMemory = 0;
	}
}
