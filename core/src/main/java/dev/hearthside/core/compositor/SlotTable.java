package dev.hearthside.core.compositor;

/**
 * The shared state cells behind {@link FrameSlots}: one state, frame number and memory size per slot, plus the
 * generation the table was created for. State changes are atomic compare-and-sets so two processes can share it.
 */
public interface SlotTable {
	int slots();

	int generation();

	SlotState state(int slot);

	boolean compareAndSet(int slot, SlotState expect, SlotState update);

	long frame(int slot);

	void setFrame(int slot, long frame);

	/** Bytes of GPU memory behind this slot, for the importer; 0 for the copy transport. */
	long memorySize(int slot);

	void setMemorySize(int slot, long size);
}
