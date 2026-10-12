package dev.hearthside.core.compositor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Slot table version 2: each generation's image description and each slot's memory type (Spec C section 3.6). */
class SegmentSlotTableDescriptionTest {
	static final ImageDescription IMAGE =
			new ImageDescription(37, 960, 1080, 0, 8, 0x1F, false, new UUID(0x0102030405060708L, 0x090a0b0c0d0e0f10L),
					new UUID(-1L, 42L));

	@TempDir
	Path dir;

	@Test
	void aNewTableHasNoDescription() throws IOException {
		try (SegmentSlotTable table = SegmentSlotTable.create(dir.resolve("frames.slots"), 4, 1)) {
			assertThat(table.description()).isEmpty();
		}
	}

	@Test
	void theDescriptionRoundTripsThroughTheFile() throws IOException {
		Path file = dir.resolve("frames.slots");
		try (SegmentSlotTable created = SegmentSlotTable.create(file, 4, 1)) {
			created.setDescription(IMAGE);
			try (SegmentSlotTable opened = SegmentSlotTable.open(file)) {
				assertThat(opened.description()).contains(IMAGE);
			}
		}
	}

	@Test
	void memoryTypesAreKeptPerSlot() throws IOException {
		Path file = dir.resolve("frames.slots");
		try (SegmentSlotTable created = SegmentSlotTable.create(file, 4, 1)) {
			created.setMemoryType(0, 3);
			created.setMemoryType(3, 7);
			created.setMemorySize(3, 4_718_592L);
			try (SegmentSlotTable opened = SegmentSlotTable.open(file)) {
				assertThat(opened.memoryType(0)).isEqualTo(3);
				assertThat(opened.memoryType(1)).isZero();
				assertThat(opened.memoryType(2)).isZero();
				assertThat(opened.memoryType(3)).isEqualTo(7);
				assertThat(opened.memorySize(3)).isEqualTo(4_718_592L);
			}
		}
	}

	@Test
	void theLayoutIsEightyBytesOfHeaderAndThirtyTwoPerSlot() throws IOException {
		Path file = dir.resolve("frames.slots");
		try (SegmentSlotTable ignored = SegmentSlotTable.create(file, 4, 1)) {
			assertThat(Files.size(file)).isEqualTo(80 + 4 * 32);
		}
	}

	@Test
	void aVersionOneTableIsRefused() throws IOException {
		Path file = dir.resolve("old.slots");
		ByteBuffer v1 = ByteBuffer.allocate(16 + 4 * 24).order(ByteOrder.nativeOrder());
		v1.putInt(0x48534C54).putInt(1).putInt(4).putInt(0);
		Files.write(file, v1.array());
		assertThatThrownBy(() -> SegmentSlotTable.open(file)).isInstanceOf(IOException.class)
				.hasMessageContaining("not a slot table");
	}

	@Test
	void theInMemoryTableKeepsADescriptionAndMemoryTypesToo() {
		ArraySlotTable table = new ArraySlotTable(4, 1);
		assertThat(table.description()).isEmpty();
		table.setDescription(IMAGE);
		table.setMemoryType(2, 5);
		assertThat(table.description()).contains(IMAGE);
		assertThat(table.memoryType(2)).isEqualTo(5);
	}
}
