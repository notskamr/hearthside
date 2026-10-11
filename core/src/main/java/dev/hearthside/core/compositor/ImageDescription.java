package dev.hearthside.core.compositor;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

/**
 * Exactly how a generation's slot images were created, so the other process can import identical images whichever API
 * it runs (Spec C section 3.6). The guest writes it into the slot table once per generation, before announcing slots.
 *
 * @param vkFormat the Vulkan format ({@code VkFormat}); an OpenGL side maps its internal format to the same value
 * @param width image width in pixels
 * @param height image height in pixels
 * @param tiling {@code VkImageTiling}
 * @param createFlags {@code VkImageCreateFlags}
 * @param usage {@code VkImageUsageFlags}
 * @param rowsTopDown whether row 0 is the top of the picture (Vulkan) rather than the bottom (OpenGL)
 * @param deviceUuid the exporting GPU's device UUID
 * @param driverUuid the exporting GPU's driver UUID
 */
public record ImageDescription(int vkFormat, int width, int height, int tiling, int createFlags, int usage,
		boolean rowsTopDown, UUID deviceUuid, UUID driverUuid) {
	public ImageDescription {
		Objects.requireNonNull(deviceUuid, "deviceUuid");
		Objects.requireNonNull(driverUuid, "driverUuid");
	}

	/**
	 * Whether an image created from {@code other} is interchangeable with one created from this: the same format, size,
	 * tiling, flags and usage. The row order and the UUIDs don't change the image itself, so they are ignored.
	 */
	public boolean sameImage(ImageDescription other) {
		return vkFormat == other.vkFormat && width == other.width && height == other.height && tiling == other.tiling
				&& createFlags == other.createFlags && usage == other.usage;
	}

	/** The UUID behind 16 bytes in the order Vulkan and OpenGL report them: big-endian, most-significant half first. */
	public static UUID uuidOf(byte[] sixteen) {
		if (sixteen.length != 16) {
			throw new IllegalArgumentException("A UUID is 16 bytes, was " + sixteen.length);
		}
		ByteBuffer buffer = ByteBuffer.wrap(sixteen);
		return new UUID(buffer.getLong(), buffer.getLong());
	}

	/** The 16 bytes of {@code uuid}, in the order {@link #uuidOf} reads them. */
	public static byte[] bytesOf(UUID uuid) {
		return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits())
				.array();
	}
}
