package dev.hearthside.fabric.client.compositor.backend;

import static org.lwjgl.vulkan.KHRExternalMemoryWin32.VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK12.*;

import com.mojang.renderpearl.backend.vulkan.VulkanUtils;
import dev.hearthside.fabric.HearthsideMod;
import java.nio.IntBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceDriverProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

/**
 * Whether Vulkan on this PC could share frames between games (Spec D section 3.1): the device Minecraft's Vulkan backend
 * would pick supports {@code VK_KHR_external_memory_win32}. A quick look with a Vulkan instance of its own, destroyed
 * at once; asked once per game. Any failure, a missing Vulkan runtime included, is "no".
 */
public final class VulkanProbe {
	/** {@code VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU}. */
	private static final int DISCRETE = 2;

	private static Boolean answer;

	private VulkanProbe() {}

	/** Render thread or any other; the first call probes, later ones repeat its answer. */
	public static synchronized boolean canShareFrames() {
		if (Boolean.getBoolean("hearthside.e2e.fakeVulkanShares")) {
			return true;
		}
		// E2E: a game without a Vulkan runtime is asked afresh, so a step can fake one after another step probed.
		boolean noLoader = Boolean.getBoolean("hearthside.e2e.noVulkanLoader")
				|| "1".equals(System.getenv(ExportDevice.NO_VULKAN_LOADER_ENV));
		if (answer != null && !noLoader) {
			return answer;
		}
		boolean shares;
		try {
			if (noLoader) {
				// E2E: what LWJGL throws on a machine without a Vulkan runtime.
				throw new UnsatisfiedLinkError("E2E: no Vulkan runtime on this machine");
			}
			shares = probe();
		} catch (Throwable t) {
			HearthsideMod.LOGGER.info("Hearthside Vulkan probe: Vulkan can't be used here ({})", t.toString());
			shares = false;
		}
		if (!noLoader) {
			answer = shares;
		}
		return shares;
	}

	private static boolean probe() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkApplicationInfo app = VkApplicationInfo.calloc(stack).sType$Default().apiVersion(VK_API_VERSION_1_2);
			VkInstanceCreateInfo info = VkInstanceCreateInfo.calloc(stack).sType$Default().pApplicationInfo(app);
			PointerBuffer pointer = stack.mallocPointer(1);
			int created = vkCreateInstance(info, null, pointer);
			if (created != VK_SUCCESS) {
				HearthsideMod.LOGGER.info("Hearthside Vulkan probe: vkCreateInstance failed ({})", created);
				return false;
			}
			VkInstance instance = new VkInstance(pointer.get(0), info);
			try {
				VkPhysicalDevice device = minecraftsChoice(stack, instance);
				if (device == null) {
					HearthsideMod.LOGGER.info("Hearthside Vulkan probe: no device Minecraft could use");
					return false;
				}
				boolean shares = ExportDevice.hasExtension(stack, device, VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME);
				HearthsideMod.LOGGER.info("Hearthside Vulkan probe: {} {} share frames", name(stack, device),
						shares ? "can" : "can't");
				return shares;
			} finally {
				vkDestroyInstance(instance, null);
			}
		}
	}

	/**
	 * The device Minecraft 26.3's Vulkan backend picks ({@code VulkanBackend.findPhysicalDevice}): in enumeration order,
	 * the first suitable device, unless a later suitable one is discrete while it isn't. Suitable here is what can be
	 * checked without Minecraft's feature sets: Vulkan 1.2, not on its known-problematic list, and a queue family that
	 * does graphics and compute. Null when none is.
	 */
	private static VkPhysicalDevice minecraftsChoice(MemoryStack stack, VkInstance instance) {
		IntBuffer count = stack.mallocInt(1);
		if (vkEnumeratePhysicalDevices(instance, count, null) != VK_SUCCESS || count.get(0) == 0) {
			return null;
		}
		PointerBuffer devices = stack.mallocPointer(count.get(0));
		if (vkEnumeratePhysicalDevices(instance, count, devices) != VK_SUCCESS) {
			return null;
		}
		VkPhysicalDevice chosen = null;
		for (int i = 0; i < count.get(0); i++) {
			if (devices.get(i) == 0) {
				continue;
			}
			VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), instance);
			if (!suitable(stack, candidate)) {
				continue;
			}
			if (chosen == null) {
				chosen = candidate;
			} else if (discrete(stack, candidate) && !discrete(stack, chosen)) {
				chosen = candidate;
				break;
			}
		}
		return chosen;
	}

	private static boolean suitable(MemoryStack stack, VkPhysicalDevice device) {
		VkPhysicalDeviceProperties plain = VkPhysicalDeviceProperties.calloc(stack);
		vkGetPhysicalDeviceProperties(device, plain);
		if (plain.apiVersion() < VK_API_VERSION_1_2) {
			return false;
		}
		VkPhysicalDeviceDriverProperties driver = VkPhysicalDeviceDriverProperties.calloc(stack).sType$Default();
		VkPhysicalDeviceProperties2 props = VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(driver);
		vkGetPhysicalDeviceProperties2(device, props);
		VkPhysicalDeviceProperties p = props.properties();
		if (VulkanUtils.KNOWN_PROBLEMATIC_DEVICES.contains(
				new VulkanUtils.DeviceUUID(driver.driverID(), p.vendorID(), p.deviceID()))) {
			return false;
		}
		IntBuffer families = stack.mallocInt(1);
		vkGetPhysicalDeviceQueueFamilyProperties(device, families, null);
		try (VkQueueFamilyProperties.Buffer queues = VkQueueFamilyProperties.calloc(families.get(0))) {
			vkGetPhysicalDeviceQueueFamilyProperties(device, families, queues);
			int both = VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT;
			for (VkQueueFamilyProperties queue : queues) {
				if ((queue.queueFlags() & both) == both) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean discrete(MemoryStack stack, VkPhysicalDevice device) {
		VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
		vkGetPhysicalDeviceProperties(device, props);
		return props.deviceType() == DISCRETE;
	}

	private static String name(MemoryStack stack, VkPhysicalDevice device) {
		VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
		vkGetPhysicalDeviceProperties(device, props);
		return props.deviceNameString();
	}
}
