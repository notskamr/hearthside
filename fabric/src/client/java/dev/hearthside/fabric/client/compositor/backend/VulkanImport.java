package dev.hearthside.fabric.client.compositor.backend;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.VK11.*;

import dev.hearthside.core.compositor.ImageDescription;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent3D;
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
	private static final ThreadLocal<Request> REQUEST = new ThreadLocal<>();

	private VulkanImport() {}

	/**
	 * One import: the shared memory, and how the image it backs was created by the other process.
	 *
	 * @param memory the shared memory to import
	 * @param image how the exporter created the image; the image Minecraft asks for here must be exactly that
	 */
	public record Request(SlotMemory memory, ImageDescription image) {}

	/** Makes the next Vulkan texture this thread creates an import of {@code memory}, an image created as {@code image}. */
	static void request(SlotMemory memory, ImageDescription image) {
		REQUEST.set(new Request(memory, image));
	}

	/** Whether the request was used, and clears it. */
	static boolean finish() {
		boolean unused = REQUEST.get() != null;
		REQUEST.remove();
		return !unused;
	}

	/** This thread's import request, taken so that no other texture uses it; null when there is none. */
	public static @Nullable Request take() {
		Request request = REQUEST.get();
		if (request != null) {
			REQUEST.remove();
		}
		return request;
	}

	/**
	 * In place of {@code vmaCreateImage}: creates the image Minecraft asked for, made importable (an external-memory
	 * image with mutable format), then imports the request's memory as its dedicated allocation and binds it. Writes
	 * the image into {@code image} and returns the {@code VkDeviceMemory}, which the caller frees after the image.
	 * Refused, before anything is created, when that image isn't exactly the one the memory was exported for. Leaves
	 * nothing behind when it throws.
	 */
	public static long createImage(VkDevice device, VkImageCreateInfo minecrafts, Request request, LongBuffer image) {
		SlotMemory memory = request.memory();
		if (Boolean.getBoolean("hearthside.e2e.perturbImportCreateInfo")) {
			// E2E: as if a Minecraft update created its textures with one more usage bit than the exporter's image.
			minecrafts.usage(minecrafts.usage() | VK_IMAGE_USAGE_STORAGE_BIT);
		}
		String difference = difference(minecrafts, request.image());
		if (difference != null) {
			throw new Failed("Minecraft creates this image differently from the shared one: " + difference);
		}
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

	/**
	 * How the image Minecraft asks for, once made importable (our external-memory structure and mutable format added),
	 * differs from the shared image, field by field as {@link ExportDevice} created it; null when they are the same
	 * image. Importing memory into an image created any other way is undefined behaviour (Review Focus 4), and
	 * {@code describe()} only says how Minecraft is expected to create it, not how it did.
	 */
	static @Nullable String difference(VkImageCreateInfo minecrafts, ImageDescription exported) {
		List<String> differences = new ArrayList<>();
		if (minecrafts.pNext() != NULL) {
			differences.add("Minecraft chains extension structures to it");
		}
		VkExtent3D extent = minecrafts.extent();
		compare(differences, "image type", minecrafts.imageType(), VK_IMAGE_TYPE_2D);
		compare(differences, "format", minecrafts.format(), exported.vkFormat());
		compare(differences, "width", extent.width(), exported.width());
		compare(differences, "height", extent.height(), exported.height());
		compare(differences, "depth", extent.depth(), 1);
		compare(differences, "mip levels", minecrafts.mipLevels(), 1);
		compare(differences, "array layers", minecrafts.arrayLayers(), 1);
		compare(differences, "samples", minecrafts.samples(), VK_SAMPLE_COUNT_1_BIT);
		compare(differences, "tiling", minecrafts.tiling(), exported.tiling());
		compare(differences, "usage", minecrafts.usage(), exported.usage());
		compare(differences, "flags", minecrafts.flags() | SharedImages.VK_CREATE_MUTABLE_FORMAT, exported.createFlags());
		compare(differences, "sharing mode", minecrafts.sharingMode(), VK_SHARING_MODE_EXCLUSIVE);
		compare(differences, "initial layout", minecrafts.initialLayout(), VK_IMAGE_LAYOUT_UNDEFINED);
		return differences.isEmpty() ? null : String.join(", ", differences);
	}

	private static void compare(List<String> differences, String field, int here, int exported) {
		if (here != exported) {
			differences.add(field + " 0x" + Integer.toHexString(here) + " here, 0x" + Integer.toHexString(exported) + " shared");
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
