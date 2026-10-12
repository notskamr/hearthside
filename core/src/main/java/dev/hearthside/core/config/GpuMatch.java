package dev.hearthside.core.config;

import dev.hearthside.core.config.PresetChooser.GpuKind;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Which of Vulkan's devices Minecraft's OpenGL is running on, for its kind (plan ruling T5-6): on OpenGL Minecraft only
 * tells Intel GPUs apart, so an AMD or NVIDIA card would otherwise count as an unknown GPU. The device is the one named
 * like OpenGL's renderer; else the only integrated or discrete GPU there is; else the only one from OpenGL's vendor.
 * Anything else is unknown.
 */
public final class GpuMatch {
	static final int NVIDIA = 0x10DE, AMD = 0x1002, INTEL = 0x8086;

	private GpuMatch() {}

	/** One of Vulkan's physical devices: its name, PCI vendor id and kind. */
	public record Device(String name, int vendorId, GpuKind kind) {
		public Device {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(kind, "kind");
		}
	}

	/**
	 * What was found: the kind, the device it came from (null when none settled it) and how it was chosen, for the log.
	 */
	public record Match(GpuKind kind, String device, String why) {}

	/**
	 * @param glName OpenGL's renderer, as Minecraft's device info names it
	 * @param glVendor OpenGL's vendor string ("NVIDIA Corporation", "ATI Technologies Inc.", "Intel"...)
	 */
	public static Match choose(List<Device> devices, String glName, String glVendor) {
		String gl = glName == null ? "" : glName.strip().toLowerCase(Locale.ROOT);
		if (!gl.isEmpty()) {
			for (Device device : devices) {
				String vk = device.name().strip().toLowerCase(Locale.ROOT);
				if (!vk.isEmpty() && (vk.contains(gl) || gl.contains(vk))) {
					return found(device, "named like OpenGL's renderer");
				}
			}
		}
		List<Device> real = devices.stream().filter(d -> d.kind() != GpuKind.UNKNOWN).toList();
		if (real.size() == 1) {
			return found(real.getFirst(), "the only GPU");
		}
		Integer vendor = vendorId(glVendor);
		if (vendor != null) {
			List<Device> same = devices.stream().filter(d -> d.vendorId() == vendor).toList();
			if (same.size() == 1) {
				return found(same.getFirst(), "the only one from OpenGL's vendor");
			}
		}
		return new Match(GpuKind.UNKNOWN, null, devices.isEmpty() ? "Vulkan lists no device"
				: "none of Vulkan's " + devices.size() + " devices is plainly OpenGL's \"" + glName + "\"");
	}

	private static Match found(Device device, String why) {
		return new Match(device.kind(), device.name(), why);
	}

	/** The PCI vendor id OpenGL's vendor string names, or null. Whole words: "Corporation" isn't ATI. */
	static Integer vendorId(String glVendor) {
		if (glVendor == null) {
			return null;
		}
		String lower = glVendor.toLowerCase(Locale.ROOT);
		List<String> words = List.of(lower.split("[^a-z]+"));
		if (words.contains("nvidia")) {
			return NVIDIA;
		}
		if (words.contains("amd") || words.contains("ati") || lower.contains("advanced micro devices")) {
			return AMD;
		}
		if (words.contains("intel")) {
			return INTEL;
		}
		return null;
	}
}
