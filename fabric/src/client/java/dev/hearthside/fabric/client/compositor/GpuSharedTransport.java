package dev.hearthside.fabric.client.compositor;

import static org.lwjgl.opengl.EXTMemoryObject.*;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.vulkan.KHRExternalMemoryWin32.*;
import static org.lwjgl.vulkan.VK11.*;

import dev.hearthside.core.compositor.FrameNames;
import dev.hearthside.core.compositor.SlotTable;
import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

/**
 * Zero-copy frame slots: each slot is a Vulkan image whose memory is exported under a name, and imported into GL on
 * both sides (by handle in the guest, by name in the host). Both GL contexts then use the same GPU memory.
 */
public final class GpuSharedTransport implements FrameTransport {
	private static final int GENERIC_ALL = 0x10000000;

	private final int width, height;
	private final int[] memoryObjects, textures;
	// Guest only: the Vulkan objects that own the memory.
	private final VkDevice device;
	private final long[] images, memories, handles;

	private GpuSharedTransport(int width, int height, int slots, VkDevice device) {
		this.width = width;
		this.height = height;
		this.memoryObjects = new int[slots];
		this.textures = new int[slots];
		this.device = device;
		this.images = new long[slots];
		this.memories = new long[slots];
		this.handles = new long[slots];
	}

	/** Guest: allocates and exports {@code table.slots()} images; records each one's size in the table. */
	public static GpuSharedTransport export(String controlFile, SlotTable table, int width, int height) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkPhysicalDevice physical = GlInterop.matchingDevice(stack, GlInterop.glDeviceUuid(stack));
			if (physical == null) {
				throw new IllegalStateException("No Vulkan device matches the GL device");
			}
			VkDeviceQueueCreateInfo.Buffer queue = VkDeviceQueueCreateInfo.calloc(1, stack).sType$Default()
					.queueFamilyIndex(0).pQueuePriorities(stack.floats(1f));
			VkDeviceCreateInfo deviceInfo = VkDeviceCreateInfo.calloc(stack).sType$Default().pQueueCreateInfos(queue)
					.ppEnabledExtensionNames(stack.pointers(stack.UTF8(VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME)));
			PointerBuffer pointer = stack.mallocPointer(1);
			check(vkCreateDevice(physical, deviceInfo, null, pointer), "vkCreateDevice");
			VkDevice device = new VkDevice(pointer.get(0), physical, deviceInfo);
			GpuSharedTransport transport = new GpuSharedTransport(width, height, table.slots(), device);
			try {
				for (int slot = 0; slot < table.slots(); slot++) {
					transport.exportSlot(stack, physical, slot, FrameNames.gpuMemory(controlFile, table.generation(), slot), table);
				}
			} catch (RuntimeException e) {
				transport.close();
				throw e;
			}
			return transport;
		}
	}

	/** Host: imports the guest's slots by name, using the sizes the guest recorded in the table. */
	public static GpuSharedTransport importByName(String controlFile, SlotTable table, int width, int height) {
		GpuSharedTransport transport = new GpuSharedTransport(width, height, table.slots(), null);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			for (int slot = 0; slot < table.slots(); slot++) {
				drainGlErrors();
				int memory = glCreateMemoryObjectsEXT();
				transport.memoryObjects[slot] = memory;
				glMemoryObjectParameteriEXT(memory, GL_DEDICATED_MEMORY_OBJECT_EXT, GL_TRUE);
				String name = FrameNames.gpuMemory(controlFile, table.generation(), slot);
				glImportMemoryWin32NameEXT(memory, table.memorySize(slot), GL_HANDLE_TYPE_OPAQUE_WIN32_EXT,
						memAddress(stack.UTF16(name)));
				transport.textures[slot] = transport.textureOn(memory);
				glCheck("importing " + name);
			}
		} catch (RuntimeException e) {
			transport.close();
			throw e;
		}
		return transport;
	}

	private void exportSlot(MemoryStack outer, VkPhysicalDevice physical, int slot, String name, SlotTable table) {
		try (MemoryStack stack = outer.push()) {
			VkExternalMemoryImageCreateInfo external = VkExternalMemoryImageCreateInfo.calloc(stack).sType$Default()
					.handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
			VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack).sType$Default().pNext(external.address())
					.imageType(VK_IMAGE_TYPE_2D).format(VK_FORMAT_R8G8B8A8_UNORM).mipLevels(1).arrayLayers(1)
					.samples(VK_SAMPLE_COUNT_1_BIT).tiling(VK_IMAGE_TILING_OPTIMAL)
					.usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT
							| VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
					.sharingMode(VK_SHARING_MODE_EXCLUSIVE).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
			imageInfo.extent().set(width, height, 1);
			LongBuffer longs = stack.mallocLong(1);
			check(vkCreateImage(device, imageInfo, null, longs), "vkCreateImage");
			images[slot] = longs.get(0);

			VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
			vkGetImageMemoryRequirements(device, images[slot], requirements);
			VkPhysicalDeviceMemoryProperties memoryProps = VkPhysicalDeviceMemoryProperties.malloc(stack);
			vkGetPhysicalDeviceMemoryProperties(physical, memoryProps);
			int type = -1;
			for (int i = 0; i < memoryProps.memoryTypeCount(); i++) {
				if ((requirements.memoryTypeBits() & (1 << i)) != 0
						&& (memoryProps.memoryTypes(i).propertyFlags() & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0) {
					type = i;
					break;
				}
			}
			if (type < 0) {
				throw new IllegalStateException("No device-local memory type for a shared frame");
			}
			VkMemoryDedicatedAllocateInfo dedicated = VkMemoryDedicatedAllocateInfo.calloc(stack).sType$Default()
					.image(images[slot]);
			VkExportMemoryWin32HandleInfoKHR named = VkExportMemoryWin32HandleInfoKHR.calloc(stack).sType$Default()
					.pNext(dedicated.address()).dwAccess(GENERIC_ALL).name(stack.UTF16(name));
			VkExportMemoryAllocateInfo export = VkExportMemoryAllocateInfo.calloc(stack).sType$Default()
					.pNext(named.address()).handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
			VkMemoryAllocateInfo allocation = VkMemoryAllocateInfo.calloc(stack).sType$Default().pNext(export.address())
					.allocationSize(requirements.size()).memoryTypeIndex(type);
			check(vkAllocateMemory(device, allocation, null, longs), "vkAllocateMemory " + name);
			memories[slot] = longs.get(0);
			check(vkBindImageMemory(device, images[slot], memories[slot], 0), "vkBindImageMemory");
			VkMemoryGetWin32HandleInfoKHR handleInfo = VkMemoryGetWin32HandleInfoKHR.calloc(stack).sType$Default()
					.memory(memories[slot]).handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
			PointerBuffer handle = stack.mallocPointer(1);
			check(vkGetMemoryWin32HandleKHR(device, handleInfo, handle), "vkGetMemoryWin32HandleKHR");
			handles[slot] = handle.get(0);

			drainGlErrors();
			int memory = glCreateMemoryObjectsEXT();
			memoryObjects[slot] = memory;
			glMemoryObjectParameteriEXT(memory, GL_DEDICATED_MEMORY_OBJECT_EXT, GL_TRUE);
			glImportMemoryWin32HandleEXT(memory, requirements.size(), GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handles[slot]);
			textures[slot] = textureOn(memory);
			glCheck("importing own " + name);
			table.setMemorySize(slot, requirements.size());
		}
	}

	private int textureOn(int memory) {
		int previous = glGetInteger(GL_TEXTURE_BINDING_2D);
		int texture = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, texture);
		glTexStorageMem2DEXT(GL_TEXTURE_2D, 1, org.lwjgl.opengl.GL11C.GL_RGBA8, width, height, memory, 0);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glBindTexture(GL_TEXTURE_2D, previous);
		return texture;
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public int height() {
		return height;
	}

	@Override
	public int slots() {
		return textures.length;
	}

	@Override
	public int glTexture(int slot) {
		return textures[slot];
	}

	@Override
	public void afterDraw(int slot) {}

	@Override
	public void beforePublish(int slot) {}

	@Override
	public void beforeRead(int slot) {}

	@Override
	public void close() {
		for (int slot = 0; slot < textures.length; slot++) {
			if (textures[slot] != 0) {
				glDeleteTextures(textures[slot]);
			}
			if (memoryObjects[slot] != 0) {
				glDeleteMemoryObjectsEXT(memoryObjects[slot]);
			}
			if (device != null) {
				Kernel32.closeHandle(handles[slot]);
				if (images[slot] != 0) {
					vkDestroyImage(device, images[slot], null);
				}
				if (memories[slot] != 0) {
					vkFreeMemory(device, memories[slot], null);
				}
			}
		}
		if (device != null) {
			VkInstance instance = device.getPhysicalDevice().getInstance();
			vkDestroyDevice(device, null);
			vkDestroyInstance(instance, null);
		}
	}

	private static void check(int result, String what) {
		if (result != VK_SUCCESS) {
			throw new IllegalStateException(what + " failed: " + result);
		}
	}

	/** Minecraft may have left an error pending; clear it so it is not blamed on our next call. */
	static void drainGlErrors() {
		for (int i = 0; i < 16 && glGetError() != GL_NO_ERROR; i++) {
			// discard
		}
	}

	static void glCheck(String what) {
		int error = glGetError();
		if (error != GL_NO_ERROR) {
			throw new IllegalStateException(what + ": GL error 0x" + Integer.toHexString(error));
		}
	}
}
