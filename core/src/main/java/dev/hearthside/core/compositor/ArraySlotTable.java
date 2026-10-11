package dev.hearthside.core.compositor;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;

/** An in-process {@link SlotTable}, for tests and single-process use. */
public final class ArraySlotTable implements SlotTable {
	private final int generation;
	private final AtomicIntegerArray states;
	private final AtomicLongArray frames;
	private final AtomicLongArray sizes;
	private final AtomicIntegerArray memoryTypes;
	private volatile ImageDescription description;

	public ArraySlotTable(int slots, int generation) {
		if (slots < 1) {
			throw new IllegalArgumentException("At least one slot, was " + slots);
		}
		this.generation = generation;
		this.states = new AtomicIntegerArray(slots);
		this.frames = new AtomicLongArray(slots);
		this.sizes = new AtomicLongArray(slots);
		this.memoryTypes = new AtomicIntegerArray(slots);
	}

	@Override
	public int slots() {
		return states.length();
	}

	@Override
	public int generation() {
		return generation;
	}

	@Override
	public SlotState state(int slot) {
		return SlotState.ofOrdinal(states.get(slot));
	}

	@Override
	public boolean compareAndSet(int slot, SlotState expect, SlotState update) {
		return states.compareAndSet(slot, expect.ordinal(), update.ordinal());
	}

	@Override
	public long frame(int slot) {
		return frames.get(slot);
	}

	@Override
	public void setFrame(int slot, long frame) {
		frames.set(slot, frame);
	}

	@Override
	public long memorySize(int slot) {
		return sizes.get(slot);
	}

	@Override
	public void setMemorySize(int slot, long size) {
		sizes.set(slot, size);
	}

	@Override
	public int memoryType(int slot) {
		return memoryTypes.get(slot);
	}

	@Override
	public void setMemoryType(int slot, int type) {
		memoryTypes.set(slot, type);
	}

	@Override
	public Optional<ImageDescription> description() {
		return Optional.ofNullable(description);
	}

	@Override
	public void setDescription(ImageDescription description) {
		if (description.width() < 1) { // as SegmentSlotTable, which reads a width of 0 as "no description"
			throw new IllegalArgumentException("An image is at least 1 pixel wide, was " + description.width());
		}
		this.description = description;
	}
}
