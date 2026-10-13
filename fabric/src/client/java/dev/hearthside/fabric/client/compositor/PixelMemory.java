package dev.hearthside.fabric.client.compositor;

import dev.hearthside.core.compositor.FrameNames;
import dev.hearthside.fabric.client.compositor.backend.Win32SharedMemory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The copy transport's shared pixels (Spec D section 3.2): one {@code width × height × 4} region per slot, each holding
 * a frame's RGBA rows as the guest's readback writes them (bottom-up, as Minecraft stores its render targets). On
 * Windows they live in a page-file-backed section named after the slot table, never in a file on disk; elsewhere, until
 * phase 2 brings {@code shm_open}, in a {@link FilePixelMemory} beside the slot table.
 */
public interface PixelMemory extends AutoCloseable {
	/** Slot {@code slot}'s frame, as a direct buffer over the memory (valid until {@link #close}). */
	ByteBuffer region(int slot);

	@Override
	void close();

	/** The guest's side: creates the memory for the slot table at {@code controlFile}. */
	static PixelMemory create(String controlFile, int width, int height, int slots) throws IOException {
		return windows() ? section(controlFile, width, height, slots, true) : file(controlFile, width, height, slots);
	}

	/**
	 * The host's side: opens the memory the guest created for the slot table at {@code controlFile}. Memory that has gone
	 * is an error, never fresh black memory.
	 */
	static PixelMemory open(String controlFile, int width, int height, int slots) throws IOException {
		return windows() ? section(controlFile, width, height, slots, false) : file(controlFile, width, height, slots);
	}

	private static PixelMemory section(String controlFile, int width, int height, int slots, boolean create)
			throws IOException {
		String name = FrameNames.pixels(controlFile);
		long bytesPerSlot = (long) width * height * 4;
		Arena arena = Arena.ofShared();
		try {
			return new Section(arena, create ? Win32SharedMemory.create(name, bytesPerSlot * slots, arena)
					: Win32SharedMemory.open(name, bytesPerSlot * slots, arena), bytesPerSlot);
		} catch (IOException | RuntimeException e) {
			arena.close();
			throw e;
		}
	}

	private static PixelMemory file(String controlFile, int width, int height, int slots) throws IOException {
		try {
			return FilePixelMemory.open(FilePixelMemory.forControlFile(Path.of(controlFile)), width, height, slots);
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	private static boolean windows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	/** A view of a Windows section; closing {@code arena} unmaps it and closes its handle. */
	record Section(Arena arena, MemorySegment pixels, long bytesPerSlot) implements PixelMemory {
		@Override
		public ByteBuffer region(int slot) {
			return pixels.asSlice(slot * bytesPerSlot, bytesPerSlot).asByteBuffer().order(ByteOrder.nativeOrder());
		}

		@Override
		public void close() {
			arena.close();
		}
	}
}
