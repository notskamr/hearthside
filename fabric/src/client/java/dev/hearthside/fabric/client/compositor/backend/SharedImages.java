package dev.hearthside.fabric.client.compositor.backend;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import dev.hearthside.core.compositor.ImageDescription;
import dev.hearthside.core.compositor.SharingFacts;
import java.util.Locale;
import java.util.UUID;

/**
 * Turns shared GPU memory into Minecraft textures on this game's own backend (Spec C section 3.5). Every slot image,
 * whichever process or API made it, is created exactly as Minecraft's Vulkan backend creates a texture with
 * {@link #SLOT_USAGE}, plus mutable format, so either backend can import it identically. Render thread only.
 */
public interface SharedImages {
	/** What a slot texture is used for: drawn into, sampled, and copied to and from. */
	int SLOT_USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING
			| GpuTexture.USAGE_RENDER_ATTACHMENT;
	/** {@code VK_FORMAT_R8G8B8A8_UNORM}: Minecraft's {@link GpuFormat#RGBA8_UNORM}. */
	int VK_FORMAT_RGBA8 = 37;
	/** {@code VK_IMAGE_TILING_OPTIMAL}, as Minecraft creates every texture. */
	int VK_TILING_OPTIMAL = 0;
	/** {@code VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT}: what OpenGL's import expects of an image Vulkan made. */
	int VK_CREATE_MUTABLE_FORMAT = 8;
	/** A UUID this game's backend can't report. It never matches anything (see {@code TransportChoice.decide}). */
	UUID UNKNOWN = new UUID(0, 0);

	/** This game's GPU UUIDs, and whether it can import shared memory. */
	SharingFacts facts();

	/** How a {@code width}×{@code height} slot image is created, on any backend. */
	default ImageDescription describe(int width, int height) {
		SharingFacts facts = facts();
		// Render targets are bottom-up on both backends: Minecraft's Screenshot flips rows on each.
		return new ImageDescription(VK_FORMAT_RGBA8, width, height, VK_TILING_OPTIMAL, VK_CREATE_MUTABLE_FORMAT,
				VulkanConst.textureUsageToVk(SLOT_USAGE, GpuFormat.RGBA8_UNORM), false, facts.deviceUuid(),
				facts.driverUuid());
	}

	/**
	 * Wraps one slot's shared memory as a Minecraft texture. Refused when this game can't import, when {@code image}
	 * isn't the image {@link #describe} would make here (Review Focus 4), or when the import call fails.
	 */
	GpuTexture importSlot(ImageDescription image, SlotMemory memory, String label) throws ImportFailed;

	/** Before Minecraft touches {@code texture} this frame: on Vulkan, takes it over from the other process. */
	void beforeUse(GpuTexture texture, CommandEncoder encoder);

	/** After Minecraft is done with {@code texture} this frame: on Vulkan, hands it back to the other process. */
	void afterUse(GpuTexture texture, CommandEncoder encoder);

	/** The adapter for the backend this game runs (OpenGL or Vulkan), made once per device. */
	static SharedImages forThisGame() {
		GpuDevice device = RenderSystem.getDevice();
		SharedImages cached = Holder.images;
		if (cached != null && Holder.device == device) {
			return cached;
		}
		String backend = device.getDeviceInfo().backendName().toLowerCase(Locale.ROOT);
		SharedImages images = switch (backend) {
			case "opengl" -> new GlSharedImages(device);
			case "vulkan" -> new VulkanSharedImages(device);
			default -> throw new IllegalStateException("No shared-image adapter for the " + backend + " backend");
		};
		Holder.device = device;
		Holder.images = images;
		return images;
	}

	/** Whether this process runs on Windows, the only platform with an export device today. */
	static boolean windows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	/** The adapter {@link #forThisGame} made, and the device it was made for. */
	final class Holder {
		private static GpuDevice device;
		private static SharedImages images;

		private Holder() {}
	}
}
