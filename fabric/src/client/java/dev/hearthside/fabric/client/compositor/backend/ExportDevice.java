package dev.hearthside.fabric.client.compositor.backend;

import static org.lwjgl.vulkan.KHRExternalMemoryWin32.*;
import static org.lwjgl.vulkan.VK11.*;

import dev.hearthside.core.compositor.ImageDescription;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExportMemoryAllocateInfo;
import org.lwjgl.vulkan.VkExportMemoryWin32HandleInfoKHR;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkExternalMemoryImageCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryDedicatedAllocateInfo;
import org.lwjgl.vulkan.VkMemoryGetWin32HandleInfoKHR;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceIDProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;

/**
 * A small Vulkan device of this process's own, on this game's GPU, that allocates every shared slot image and exports
 * its memory under a name (Spec C section 3.5). It works the same whichever backend the game runs, so an image is
 * always made one way and either backend can import it. One per process, created lazily and kept for the process's
 * life; every method may be called from any thread.
 */
public final class ExportDevice {
	/** {@code GENERIC_ALL}: the named memory may be opened for anything by other processes of this user. */
	private static final int GENERIC_ALL = 0x10000000;

	private static ExportDevice instance;
	/** Every exported slot still open: its handle → {image, memory} on the export device. */
	private static final Map<Long, long[]> live = new HashMap<>();

	private final UUID deviceUuid;
	private final VkDevice device;

	private ExportDevice(UUID deviceUuid, VkDevice device) {
		this.deviceUuid = deviceUuid;
		this.device = device;
	}

	/**
	 * The export device on the GPU with this device UUID, created on first use. Throws when no Vulkan device has that
	 * UUID or it can't export Win32 memory, or when this process already exports from another GPU.
	 */
	public static synchronized ExportDevice forGpu(UUID deviceUuid) {
		if (instance != null) {
			if (!instance.deviceUuid.equals(deviceUuid)) {
				throw new IllegalStateException("This process exports from GPU " + instance.deviceUuid + ", not " + deviceUuid);
			}
			return instance;
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkPhysicalDevice physical = matchingDevice(stack, deviceUuid);
			if (physical == null) {
				throw new IllegalStateException("No Vulkan device has this game's device UUID " + deviceUuid);
			}
			if (!hasExtension(stack, physical, VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME)) {
				vkDestroyInstance(physical.getInstance(), null);
				throw new IllegalStateException("The Vulkan device can't export Win32 memory");
			}
			VkDeviceQueueCreateInfo.Buffer queue = VkDeviceQueueCreateInfo.calloc(1, stack).sType$Default()
					.queueFamilyIndex(0).pQueuePriorities(stack.floats(1f));
			VkDeviceCreateInfo deviceInfo = VkDeviceCreateInfo.calloc(stack).sType$Default().pQueueCreateInfos(queue)
					.ppEnabledExtensionNames(stack.pointers(stack.UTF8(VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME)));
			PointerBuffer pointer = stack.mallocPointer(1);
			int result = vkCreateDevice(physical, deviceInfo, null, pointer);
			if (result != VK_SUCCESS) {
				vkDestroyInstance(physical.getInstance(), null);
				throw new IllegalStateException("vkCreateDevice failed: " + result);
			}
			instance = new ExportDevice(deviceUuid, new VkDevice(pointer.get(0), physical, deviceInfo));
			return instance;
		}
	}

	/**
	 * Allocates one image as {@code image} describes, in a dedicated allocation from device-local memory, and exports
	 * the memory as an opaque Win32 handle under {@code name}.
	 */
	public synchronized ExportedSlot export(ImageDescription image, String name) {
		long vkImage = 0, memory = 0, handle = 0;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkExternalMemoryImageCreateInfo external = VkExternalMemoryImageCreateInfo.calloc(stack).sType$Default()
					.handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
			VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack).sType$Default().pNext(external.address())
					.flags(image.createFlags()).imageType(VK_IMAGE_TYPE_2D).format(image.vkFormat()).mipLevels(1)
					.arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT).tiling(image.tiling()).usage(image.usage())
					.sharingMode(VK_SHARING_MODE_EXCLUSIVE).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
			imageInfo.extent().set(image.width(), image.height(), 1);
			LongBuffer longs = stack.mallocLong(1);
			check(vkCreateImage(device, imageInfo, null, longs), "vkCreateImage");
			vkImage = longs.get(0);

			VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
			vkGetImageMemoryRequirements(device, vkImage, requirements);
			int type = deviceLocalType(stack, requirements.memoryTypeBits());
			VkMemoryDedicatedAllocateInfo dedicated = VkMemoryDedicatedAllocateInfo.calloc(stack).sType$Default()
					.image(vkImage);
			VkExportMemoryWin32HandleInfoKHR named = VkExportMemoryWin32HandleInfoKHR.calloc(stack).sType$Default()
					.pNext(dedicated.address()).dwAccess(GENERIC_ALL).name(stack.UTF16(name));
			VkExportMemoryAllocateInfo export = VkExportMemoryAllocateInfo.calloc(stack).sType$Default()
					.pNext(named.address()).handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
			VkMemoryAllocateInfo allocation = VkMemoryAllocateInfo.calloc(stack).sType$Default().pNext(export.address())
					.allocationSize(requirements.size()).memoryTypeIndex(type);
			check(vkAllocateMemory(device, allocation, null, longs), "vkAllocateMemory " + name);
			memory = longs.get(0);
			check(vkBindImageMemory(device, vkImage, memory, 0), "vkBindImageMemory");
			VkMemoryGetWin32HandleInfoKHR handleInfo = VkMemoryGetWin32HandleInfoKHR.calloc(stack).sType$Default()
					.memory(memory).handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
			PointerBuffer pointer = stack.mallocPointer(1);
			check(vkGetMemoryWin32HandleKHR(device, handleInfo, pointer), "vkGetMemoryWin32HandleKHR");
			handle = pointer.get(0);
			synchronized (ExportDevice.class) {
				live.put(handle, new long[] {vkImage, memory});
			}
			return new ExportedSlot(handle, requirements.size(), type);
		} catch (RuntimeException e) {
			destroy(handle, vkImage, memory);
			throw e;
		}
	}

	/** Closes the slot's handle and frees its image and memory; a slot closed twice is freed once. */
	static synchronized void release(ExportedSlot slot) {
		long[] objects = live.remove(slot.handle());
		if (objects != null) {
			instance.destroy(slot.handle(), objects[0], objects[1]);
		}
	}

	private void destroy(long handle, long vkImage, long memory) {
		Win32Handles.close(handle);
		if (vkImage != 0) {
			vkDestroyImage(device, vkImage, null);
		}
		if (memory != 0) {
			vkFreeMemory(device, memory, null);
		}
	}

	private int deviceLocalType(MemoryStack stack, int allowedTypes) {
		VkPhysicalDeviceMemoryProperties memoryProps = VkPhysicalDeviceMemoryProperties.malloc(stack);
		vkGetPhysicalDeviceMemoryProperties(device.getPhysicalDevice(), memoryProps);
		for (int i = 0; i < memoryProps.memoryTypeCount(); i++) {
			if ((allowedTypes & (1 << i)) != 0
					&& (memoryProps.memoryTypes(i).propertyFlags() & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0) {
				return i;
			}
		}
		throw new IllegalStateException("No device-local memory type for a shared frame");
	}

	/** The Vulkan device with this device UUID, on a new instance the caller destroys; null if none. */
	private static VkPhysicalDevice matchingDevice(MemoryStack stack, UUID deviceUuid) {
		ByteBuffer wanted = stack.bytes(ImageDescription.bytesOf(deviceUuid));
		VkApplicationInfo app = VkApplicationInfo.calloc(stack).sType$Default().apiVersion(VK_API_VERSION_1_1);
		VkInstanceCreateInfo info = VkInstanceCreateInfo.calloc(stack).sType$Default().pApplicationInfo(app);
		PointerBuffer pointer = stack.mallocPointer(1);
		if (vkCreateInstance(info, null, pointer) != VK_SUCCESS) {
			return null;
		}
		VkInstance instance = new VkInstance(pointer.get(0), info);
		IntBuffer count = stack.mallocInt(1);
		vkEnumeratePhysicalDevices(instance, count, null);
		PointerBuffer devices = stack.mallocPointer(count.get(0));
		vkEnumeratePhysicalDevices(instance, count, devices);
		for (int i = 0; i < devices.capacity(); i++) {
			VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), instance);
			VkPhysicalDeviceIDProperties ids = VkPhysicalDeviceIDProperties.calloc(stack).sType$Default();
			VkPhysicalDeviceProperties2 props = VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(ids.address());
			vkGetPhysicalDeviceProperties2(candidate, props);
			if (ids.deviceUUID().equals(wanted)) {
				return candidate;
			}
		}
		vkDestroyInstance(instance, null);
		return null;
	}

	private static boolean hasExtension(MemoryStack stack, VkPhysicalDevice device, String name) {
		IntBuffer count = stack.mallocInt(1);
		vkEnumerateDeviceExtensionProperties(device, (String) null, count, null);
		// Heap, not stack: drivers list hundreds of extensions.
		try (VkExtensionProperties.Buffer extensions = VkExtensionProperties.calloc(count.get(0))) {
			vkEnumerateDeviceExtensionProperties(device, (String) null, count, extensions);
			for (VkExtensionProperties extension : extensions) {
				if (extension.extensionNameString().equals(name)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void check(int result, String what) {
		if (result != VK_SUCCESS) {
			throw new IllegalStateException(what + " failed: " + result);
		}
	}
}
