package dev.hearthside.core.compositor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImageDescriptionTest {
	static final UUID GPU = new UUID(1, 1), OTHER = new UUID(2, 2), DRV = new UUID(3, 3);

	static ImageDescription image(int usage, boolean rowsTopDown, UUID gpu, UUID driver) {
		return new ImageDescription(37, 960, 1080, 0, 8, usage, rowsTopDown, gpu, driver);
	}

	@Test
	void sameImageIgnoresTheDevicesAndRowOrder() {
		assertThat(image(0x1F, false, GPU, DRV).sameImage(image(0x1F, true, OTHER, OTHER))).isTrue();
	}

	@Test
	void aDifferentUsageIsNotTheSameImage() {
		assertThat(image(0x17, false, GPU, DRV).sameImage(image(0x1F, false, GPU, DRV))).isFalse();
	}

	@Test
	void aDifferentFormatSizeTilingOrFlagsIsNotTheSameImage() {
		ImageDescription base = image(0x1F, false, GPU, DRV);
		assertThat(base.sameImage(new ImageDescription(44, 960, 1080, 0, 8, 0x1F, false, GPU, DRV))).isFalse();
		assertThat(base.sameImage(new ImageDescription(37, 961, 1080, 0, 8, 0x1F, false, GPU, DRV))).isFalse();
		assertThat(base.sameImage(new ImageDescription(37, 960, 1079, 0, 8, 0x1F, false, GPU, DRV))).isFalse();
		assertThat(base.sameImage(new ImageDescription(37, 960, 1080, 1, 8, 0x1F, false, GPU, DRV))).isFalse();
		assertThat(base.sameImage(new ImageDescription(37, 960, 1080, 0, 0, 0x1F, false, GPU, DRV))).isFalse();
	}

	@Test
	void uuidsSurviveTheirBytes() {
		UUID id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
		byte[] bytes = ImageDescription.bytesOf(id);
		// Big-endian, most-significant half first: the order Vulkan and OpenGL report UUIDs in.
		assertThat(bytes).hasSize(16).startsWith((byte) 0x00, (byte) 0x11).endsWith((byte) 0xee, (byte) 0xff);
		assertThat(ImageDescription.uuidOf(bytes)).isEqualTo(id);
	}

	@Test
	void uuidOfRefusesTheWrongLength() {
		assertThatThrownBy(() -> ImageDescription.uuidOf(new byte[15])).isInstanceOf(IllegalArgumentException.class);
	}
}
