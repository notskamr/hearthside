package dev.hearthside.core.compositor;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A {@link SlotTable} in a memory-mapped file, shared between a guest and the host.
 *
 * <p>Layout, version 2 (little or big endian as the platform maps it; both sides run the same JVM build):
 * <ul>
 *   <li>an 80-byte header: int magic {@code "HSL2"}, int generation, int slot count, int padding; then the
 *       {@link ImageDescription}: int format, int width, int height, int tiling, int create flags, int usage, int
 *       rows-top-down (0 or 1), int padding, 16 bytes of device UUID, 16 bytes of driver UUID. The UUIDs are stored
 *       in {@link ImageDescription#bytesOf} order whatever the platform's endianness. A width of 0 means no
 *       description yet;</li>
 *   <li>then 32 bytes per slot: int state, int memory type, long frame, long memory size, 8 bytes of padding.</li>
 * </ul>
 * Every cell is naturally aligned for atomic access. Version 1 tables (magic {@code "HSLT"}) are refused.
 */
public final class SegmentSlotTable implements SlotTable, AutoCloseable {
	static final int MAGIC = 0x48534C32;
	private static final long HEADER = 80, SLOT_BYTES = 32;
	/** Offsets of the description's cells in the header. */
	private static final long FORMAT = 16, WIDTH = 20, HEIGHT = 24, TILING = 28, CREATE_FLAGS = 32, USAGE = 36,
			ROWS_TOP_DOWN = 40, DEVICE_UUID = 48, DRIVER_UUID = 64;
	private static final VarHandle INT = ValueLayout.JAVA_INT.varHandle();
	private static final VarHandle LONG = ValueLayout.JAVA_LONG.varHandle();
	/** For the UUIDs, so their bytes land in the order Vulkan and OpenGL report them on any platform. */
	private static final VarHandle BIG_LONG = ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN).varHandle();

	private final Arena arena;
	private final MemorySegment segment;
	private final int slots;
	private final int generation;

	private SegmentSlotTable(Arena arena, MemorySegment segment) throws IOException {
		this.arena = arena;
		this.segment = segment;
		if (segment.byteSize() < HEADER || (int) INT.getVolatile(segment, 0L) != MAGIC) {
			arena.close();
			throw new IOException("This file is not a slot table");
		}
		this.generation = (int) INT.getVolatile(segment, 4L);
		this.slots = (int) INT.getVolatile(segment, 8L);
		if (slots < 1 || segment.byteSize() < HEADER + slots * SLOT_BYTES) {
			arena.close();
			throw new IOException("This slot table is truncated");
		}
	}

	/** Creates (or replaces) the table with every slot FREE. */
	public static SegmentSlotTable create(Path file, int slots, int generation) throws IOException {
		if (slots < 1) {
			throw new IllegalArgumentException("At least one slot, was " + slots);
		}
		long size = HEADER + slots * SLOT_BYTES;
		Arena arena = Arena.ofShared();
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.READ, StandardOpenOption.WRITE)) {
			MemorySegment segment = channel.map(FileChannel.MapMode.READ_WRITE, 0, size, arena);
			segment.fill((byte) 0);
			INT.setVolatile(segment, 4L, generation);
			INT.setVolatile(segment, 8L, slots);
			INT.setVolatile(segment, 0L, MAGIC); // last, so a reader never sees a half-written header as valid
			return new SegmentSlotTable(arena, segment);
		} catch (IOException | RuntimeException e) {
			if (arena.scope().isAlive()) {
				arena.close();
			}
			throw e;
		}
	}

	/** Opens a table another process created. */
	public static SegmentSlotTable open(Path file) throws IOException {
		Arena arena = Arena.ofShared();
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
			long size = Files.size(file);
			return new SegmentSlotTable(arena, channel.map(FileChannel.MapMode.READ_WRITE, 0, size, arena));
		} catch (IOException | RuntimeException e) {
			if (arena.scope().isAlive()) {
				arena.close();
			}
			throw e;
		}
	}

	private static long base(int slot) {
		return HEADER + slot * SLOT_BYTES;
	}

	private void check(int slot) {
		if (slot < 0 || slot >= slots) {
			throw new IndexOutOfBoundsException("Slot " + slot + " of " + slots);
		}
	}

	@Override
	public int slots() {
		return slots;
	}

	@Override
	public int generation() {
		return generation;
	}

	@Override
	public SlotState state(int slot) {
		check(slot);
		return SlotState.ofOrdinal((int) INT.getVolatile(segment, base(slot)));
	}

	@Override
	public boolean compareAndSet(int slot, SlotState expect, SlotState update) {
		check(slot);
		return INT.compareAndSet(segment, base(slot), expect.ordinal(), update.ordinal());
	}

	@Override
	public long frame(int slot) {
		check(slot);
		return (long) LONG.getVolatile(segment, base(slot) + 8);
	}

	@Override
	public void setFrame(int slot, long frame) {
		check(slot);
		LONG.setVolatile(segment, base(slot) + 8, frame);
	}

	@Override
	public long memorySize(int slot) {
		check(slot);
		return (long) LONG.getVolatile(segment, base(slot) + 16);
	}

	@Override
	public void setMemorySize(int slot, long size) {
		check(slot);
		LONG.setVolatile(segment, base(slot) + 16, size);
	}

	@Override
	public int memoryType(int slot) {
		check(slot);
		return (int) INT.getVolatile(segment, base(slot) + 4);
	}

	@Override
	public void setMemoryType(int slot, int type) {
		check(slot);
		INT.setVolatile(segment, base(slot) + 4, type);
	}

	@Override
	public Optional<ImageDescription> description() {
		int width = (int) INT.getVolatile(segment, WIDTH); // first: the writer publishes it last
		if (width == 0) {
			return Optional.empty();
		}
		return Optional.of(new ImageDescription((int) INT.getVolatile(segment, FORMAT), width,
				(int) INT.getVolatile(segment, HEIGHT), (int) INT.getVolatile(segment, TILING),
				(int) INT.getVolatile(segment, CREATE_FLAGS), (int) INT.getVolatile(segment, USAGE),
				(int) INT.getVolatile(segment, ROWS_TOP_DOWN) != 0, uuidAt(DEVICE_UUID), uuidAt(DRIVER_UUID)));
	}

	@Override
	public void setDescription(ImageDescription description) {
		Objects.requireNonNull(description, "description");
		if (description.width() < 1) {
			throw new IllegalArgumentException("An image is at least 1 pixel wide, was " + description.width());
		}
		INT.setVolatile(segment, WIDTH, 0); // absent while the other cells change
		INT.setVolatile(segment, FORMAT, description.vkFormat());
		INT.setVolatile(segment, HEIGHT, description.height());
		INT.setVolatile(segment, TILING, description.tiling());
		INT.setVolatile(segment, CREATE_FLAGS, description.createFlags());
		INT.setVolatile(segment, USAGE, description.usage());
		INT.setVolatile(segment, ROWS_TOP_DOWN, description.rowsTopDown() ? 1 : 0);
		setUuidAt(DEVICE_UUID, description.deviceUuid());
		setUuidAt(DRIVER_UUID, description.driverUuid());
		INT.setVolatile(segment, WIDTH, description.width()); // last, so a reader never sees a half-written one
	}

	private UUID uuidAt(long offset) {
		return new UUID((long) BIG_LONG.getVolatile(segment, offset), (long) BIG_LONG.getVolatile(segment, offset + 8));
	}

	private void setUuidAt(long offset, UUID uuid) {
		BIG_LONG.setVolatile(segment, offset, uuid.getMostSignificantBits());
		BIG_LONG.setVolatile(segment, offset + 8, uuid.getLeastSignificantBits());
	}

	@Override
	public void close() {
		if (arena.scope().isAlive()) {
			arena.close();
		}
	}
}
