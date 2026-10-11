package dev.hearthside.fabric.client.compositor.backend;

import static org.lwjgl.vulkan.VK11.*;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import dev.hearthside.core.compositor.ImageDescription;
import dev.hearthside.core.compositor.SharingFacts;
import dev.hearthside.fabric.client.mixin.backend.FrontendGpuDeviceAccessor;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkPhysicalDeviceIDProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;

/**
 * Shared images on Minecraft's Vulkan backend. An import is an ordinary Minecraft {@code VulkanGpuTexture} whose
 * allocation {@code VulkanGpuTextureImportMixin} swaps for the imported memory. Like every Minecraft texture it lives
 * in the {@code GENERAL} layout; {@link #beforeUse} and {@link #afterUse} move its ownership between the other process
 * ({@code VK_QUEUE_FAMILY_EXTERNAL}) and Minecraft's graphics queue around each use.
 */
public final class VulkanSharedImages implements SharedImages {
	/** The optional feature set {@code VulkanFeatureSetsMixin} adds to Minecraft's device. */
	public static final String FEATURE_SET = "hearthside_external_memory";
	public static final String EXTERNAL_MEMORY_WIN32 = "VK_KHR_external_memory_win32";

	private final GpuDevice device;
	private final VulkanDevice vulkan;
	private final SharingFacts facts;

	VulkanSharedImages(GpuDevice device) {
		this.device = device;
		this.vulkan = (VulkanDevice) ((FrontendGpuDeviceAccessor) device).hearthside$backend();
		this.facts = probe(vulkan);
	}

	private static SharingFacts probe(VulkanDevice vulkan) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkPhysicalDeviceIDProperties ids = VkPhysicalDeviceIDProperties.calloc(stack).sType$Default();
			VkPhysicalDeviceProperties2 props = VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(ids.address());
			vkGetPhysicalDeviceProperties2(vulkan.vkDevice().getPhysicalDevice(), props);
			byte[] device = new byte[VK_UUID_SIZE], driver = new byte[VK_UUID_SIZE];
			ids.deviceUUID().get(0, device);
			ids.driverUUID().get(0, driver);
			// Minecraft lists its device extensions with a " (D)" suffix (instance ones with " (I)").
			boolean extension = vulkan.getDeviceInfo().underlyingExtensions().contains(EXTERNAL_MEMORY_WIN32 + " (D)");
			return new SharingFacts(SharedImages.windows(), SharedImages.windows() && extension,
					ImageDescription.uuidOf(device), ImageDescription.uuidOf(driver));
		}
	}

	@Override
	public SharingFacts facts() {
		return facts;
	}

	@Override
	public GpuTexture importSlot(ImageDescription image, SlotMemory memory, String label) throws ImportFailed {
		if (!facts.canImport()) {
			throw new ImportFailed("Minecraft's Vulkan device here can't import shared memory");
		}
		if (!image.sameImage(describe(image.width(), image.height()))) {
			throw new ImportFailed("Not the image this game makes: " + image);
		}
		GpuTexture texture;
		boolean imported;
		VulkanImport.request(memory);
		try {
			texture = device.createTexture(label, SLOT_USAGE, GpuFormat.RGBA8_UNORM, image.width(), image.height(), 1, 1);
		} catch (VulkanImport.Failed e) {
			throw new ImportFailed("Importing " + memory.source() + ": " + e.getMessage(), e);
		} finally {
			imported = VulkanImport.finish();
		}
		if (!imported) {
			texture.close();
			throw new ImportFailed("Minecraft created the texture without the import hook");
		}
		return texture;
	}

	@Override
	public void beforeUse(GpuTexture texture, CommandEncoder encoder) {
		transfer(texture, encoder, true);
	}

	@Override
	public void afterUse(GpuTexture texture, CommandEncoder encoder) {
		transfer(texture, encoder, false);
	}

	/**
	 * Records a queue family ownership transfer of {@code texture} between the other process and Minecraft's graphics
	 * queue, in its own command buffer placed in order among Minecraft's commands. The layout stays {@code GENERAL}.
	 */
	private void transfer(GpuTexture texture, CommandEncoder encoder, boolean acquire) {
		VulkanCommandEncoder commands = (VulkanCommandEncoder) ((FrontendCommandEncoder) encoder).backend();
		int graphics = vulkan.graphicsQueue().queueFamilyIndex();
		int everything = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack).sType$Default()
					.oldLayout(VK_IMAGE_LAYOUT_GENERAL).newLayout(VK_IMAGE_LAYOUT_GENERAL)
					.srcAccessMask(acquire ? 0 : everything).dstAccessMask(acquire ? everything : 0)
					.srcQueueFamilyIndex(acquire ? VK_QUEUE_FAMILY_EXTERNAL : graphics)
					.dstQueueFamilyIndex(acquire ? graphics : VK_QUEUE_FAMILY_EXTERNAL)
					.image(((VulkanGpuTexture) texture).vkImage());
			barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
					.baseArrayLayer(0).layerCount(1);
			VkCommandBuffer buffer = commands.allocateAndBeginTransientCommandBuffer();
			vkCmdPipelineBarrier(buffer,
					acquire ? VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT : VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
					acquire ? VK_PIPELINE_STAGE_ALL_COMMANDS_BIT : VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
					0, null, null, barrier);
			int result = vkEndCommandBuffer(buffer);
			if (result != VK_SUCCESS) {
				throw new IllegalStateException("vkEndCommandBuffer failed: " + result);
			}
			commands.execute(buffer);
		}
	}
}
