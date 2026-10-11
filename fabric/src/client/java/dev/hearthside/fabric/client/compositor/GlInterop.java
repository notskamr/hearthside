package dev.hearthside.fabric.client.compositor;

import static org.lwjgl.vulkan.KHRExternalMemoryWin32.VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK11.*;

import dev.hearthside.core.compositor.ProbeFacts;
import dev.hearthside.fabric.HearthsideMod;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceIDProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;

/** Finds out whether this GPU can share frames between processes without copying. Render thread (needs the GL context). */
public final class GlInterop {
	private GlInterop() {}

	public static ProbeFacts probe(boolean forceCopy) {
		GLCapabilities caps = GL.getCapabilities();
		boolean glImport = caps.GL_EXT_memory_object && caps.GL_EXT_memory_object_win32;
		boolean deviceMatches = false, vkExport = false;
		if (glImport) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				VkPhysicalDevice device = matchingDevice(stack, glDeviceUuid(stack));
				if (device != null) {
					deviceMatches = true;
					vkExport = hasExtension(stack, device, VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME);
					vkDestroyInstance(device.getInstance(), null);
				}
			} catch (RuntimeException | LinkageError e) {
				HearthsideMod.LOGGER.warn("Hearthside could not probe Vulkan; using the copy transport", e);
			}
		}
		ProbeFacts facts = new ProbeFacts(glImport, deviceMatches, vkExport, forceCopy);
		HearthsideMod.LOGGER.info("Hearthside GPU sharing probe: {}", facts);
		return facts;
	}

	static ByteBuffer glDeviceUuid(MemoryStack stack) {
		ByteBuffer uuid = stack.malloc(16);
		EXTMemoryObject.glGetUnsignedBytei_vEXT(EXTMemoryObject.GL_DEVICE_UUID_EXT, 0, uuid);
		return uuid;
	}

	/** The Vulkan device with the GL context's device UUID, on a new instance the caller destroys; null if none. */
	static VkPhysicalDevice matchingDevice(MemoryStack stack, ByteBuffer glUuid) {
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
			if (ids.deviceUUID().equals(glUuid)) {
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
}
