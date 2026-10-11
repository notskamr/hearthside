package dev.hearthside.fabric.client.compositor.backend;

import static org.lwjgl.vulkan.VK11.*;

import java.nio.LongBuffer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExternalMemoryImageCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImportMemoryWin32HandleInfoKHR;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryDedicatedAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

/**
 * Lets Minecraft's own {@code VulkanGpuTexture} be built on imported memory. {@link VulkanSharedImages} sets a request
 * on this thread around one {@code createTexture} call; while it is set, {@code VulkanGpuTextureImportMixin} replaces
 * that texture's VMA allocation with {@link #createImage}, so everything else about the texture (its views, layout
 * and destruction queue) stays Minecraft's.
 */
public final class VulkanImport {
	private static final ThreadLocal<SlotMemory> REQUEST = new ThreadLocal<>();

	private VulkanImport() {}

	/** Makes the next Vulkan texture this thread creates an import of {@code memory}. */
	static void request(SlotMemory memory) {
		REQUEST.set(memory);
	}

	/** Whether the request was used, and clears it. */
	static boolean finish() {
		boolean unused = REQUEST.get() != null;
		REQUEST.remove();
		return !unused;
	}

	/** This thread's import request, taken so that no other texture uses it; null when there is none. */
	public static @Nullable SlotMemory take() {
		SlotMemory memory = REQUEST.get();
		if (memory != null) {
			REQUEST.remove();
		}
		return memory;
	}

	/**
	 * In place of {@code vmaCreateImage}: creates the image Minecraft asked for, made importable (an external-memory
	 * image with mutable format), then imports {@code memory} as its dedicated allocation and binds it. Writes the
	 * image into {@code image} and returns the {@code VkDeviceMemory}, which the caller frees after the image. Leaves
	 * nothing behind when it throws.
	 */
	public static long createImage(VkDevice device, VkImageCreateInfo minecrafts, SlotMemory memory, LongBuffer image) {
		long vkImage = 0, deviceMemory = 0;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkExternalMemoryImageCreateInfo external = VkExternalMemoryImageCreateInfo.calloc(stack).sType$Default()
					.pNext(minecrafts.pNext()).handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
			minecrafts.pNext(external.address()).flags(minecrafts.flags() | SharedImages.VK_CREATE_MUTABLE_FORMAT);
			LongBuffer longs = stack.mallocLong(1);
			int created = vkCreateImage(device, minecrafts, null, longs);
			minecrafts.pNext(external.pNext()); // Minecraft keeps its create info; leave no pointer into this stack
			check(created, "vkCreateImage");
			vkImage = longs.get(0);

			// A dedicated import must be exactly the image's size, and the exporter's size and type: identical images
			// on one GPU agree, so a difference means the export isn't the image Minecraft would make here.
			VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
			vkGetImageMemoryRequirements(device, vkImage, requirements);
			if (memory.memoryType() < 0 || memory.memoryType() >= 32
					|| (requirements.memoryTypeBits() & (1 << memory.memoryType())) == 0
					|| requirements.size() != memory.allocationSize()) {
				throw new Failed("the image here needs " + requirements.size() + " bytes of memory types 0x"
						+ Integer.toHexString(requirements.memoryTypeBits()) + ", the export has " + memory.allocationSize()
						+ " bytes of type " + memory.memoryType());
			}
			VkMemoryDedicatedAllocateInfo dedicated = VkMemoryDedicatedAllocateInfo.calloc(stack).sType$Default()
					.image(vkImage);
			VkImportMemoryWin32HandleInfoKHR imported = VkImportMemoryWin32HandleInfoKHR.calloc(stack).sType$Default()
					.pNext(dedicated.address())
					.handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
			switch (memory.source()) {
				case SlotMemory.Source.ByName(String name) -> imported.name(stack.UTF16(name));
				case SlotMemory.Source.ByHandle(long handle) -> imported.handle(handle);
			}
			VkMemoryAllocateInfo allocation = VkMemoryAllocateInfo.calloc(stack).sType$Default()
					.pNext(imported.address()).allocationSize(memory.allocationSize()).memoryTypeIndex(memory.memoryType());
			check(vkAllocateMemory(device, allocation, null, longs), "vkAllocateMemory importing " + memory.source());
			deviceMemory = longs.get(0);
			check(vkBindImageMemory(device, vkImage, deviceMemory, 0), "vkBindImageMemory");
			image.put(0, vkImage);
			return deviceMemory;
		} catch (RuntimeException e) {
			destroy(device, vkImage, deviceMemory);
			throw e instanceof Failed ? e : new Failed(e.toString(), e);
		}
	}

	/** In place of {@code vmaDestroyImage} for an imported texture: the image first, then its memory. */
	public static void destroy(VkDevice device, long vkImage, long deviceMemory) {
		if (vkImage != 0) {
			vkDestroyImage(device, vkImage, null);
		}
		if (deviceMemory != 0) {
			vkFreeMemory(device, deviceMemory, null);
		}
	}

	private static void check(int result, String what) {
		if (result != VK_SUCCESS) {
			throw new Failed(what + " failed: " + result);
		}
	}

	/** An import call failed; it propagates out of the texture's constructor and becomes {@link ImportFailed}. */
	public static final class Failed extends RuntimeException {
		Failed(String message) {
			super(message);
		}

		Failed(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
