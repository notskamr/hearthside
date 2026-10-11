package dev.hearthside.core.compositor;

import java.util.Optional;

/**
 * The shared state cells behind {@link FrameSlots}: one state, frame number, memory size and memory type per slot, plus
 * the generation the table was created for and that generation's {@link ImageDescription}. State changes are atomic
 * compare-and-sets so two processes can share it.
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

	/** The memory type index behind this slot's allocation, for the importer; 0 for the copy transport. */
	int memoryType(int slot);

	void setMemoryType(int slot, int type);

	/** How this generation's slot images were created; empty until the guest sets it. */
	Optional<ImageDescription> description();

	/** Records the slot images' description, once per generation, before the guest announces its slots. */
	void setDescription(ImageDescription description);
}
