package dev.hearthside.fabric.client.compositor.backend;

/**
 * One slot image allocated and exported by the {@link ExportDevice}. Closing it closes the handle and frees the image
 * and its memory on the export device; imports made from it keep the memory alive until they are closed too.
 *
 * @param handle the opaque Win32 handle to the slot's memory, owned by this process
 * @param allocationSize bytes allocated, which every import must ask for
 * @param memoryType the memory type index allocated from
 */
public record ExportedSlot(long handle, long allocationSize, int memoryType) implements AutoCloseable {
	/** The memory as an import from this process sees it: by handle. */
	public SlotMemory byHandle() {
		return new SlotMemory(allocationSize, memoryType, new SlotMemory.Source.ByHandle(handle));
	}

	/** The memory as another process sees it: by the name it was exported under. */
	public SlotMemory byName(String name) {
		return new SlotMemory(allocationSize, memoryType, new SlotMemory.Source.ByName(name));
	}

	@Override
	public void close() {
		ExportDevice.release(this);
	}
}
