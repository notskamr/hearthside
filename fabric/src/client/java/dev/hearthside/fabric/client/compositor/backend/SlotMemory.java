package dev.hearthside.fabric.client.compositor.backend;

/**
 * One slot's shared GPU memory, as the exporter allocated it.
 *
 * @param allocationSize bytes the exporter allocated; an import must ask for exactly this many
 * @param memoryType the exporter's memory type index; an import on the same GPU uses the same one
 * @param source where the memory is found: the host opens it by name, the guest that exported it by handle
 */
public record SlotMemory(long allocationSize, int memoryType, Source source) {
	public sealed interface Source {
		/** A named Win32 memory object, which any process can open. */
		record ByName(String name) implements Source {}

		/** An opaque Win32 handle owned by this process (the import does not take it over). */
		record ByHandle(long handle) implements Source {}
	}
}
