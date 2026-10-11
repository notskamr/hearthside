package dev.hearthside.core.compositor;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A {@link SlotTable} in a memory-mapped file, shared between a guest and the host.
 *
 * <p>Layout (little or big endian as the platform maps it; both sides run the same JVM build): a 16-byte header of
 * int magic {@code "HSLT"}, int generation, int slot count, int padding; then 24 bytes per slot: int state, int
 * padding, long frame, long memory size. Every cell is naturally aligned for atomic access.
 */
public final class SegmentSlotTable implements SlotTable, AutoCloseable {
	static final int MAGIC = 0x48534C54;
	private static final long HEADER = 16, SLOT_BYTES = 24;
	private static final VarHandle INT = ValueLayout.JAVA_INT.varHandle();
	private static final VarHandle LONG = ValueLayout.JAVA_LONG.varHandle();

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
	public void close() {
		if (arena.scope().isAlive()) {
			arena.close();
		}
	}
}
