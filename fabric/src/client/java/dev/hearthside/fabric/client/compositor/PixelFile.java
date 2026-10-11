package dev.hearthside.fabric.client.compositor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The copy transport's shared pixel file: one {@code width × height × 4} region per slot, each holding a frame's RGBA
 * rows as the guest's readback writes them (bottom-up, as Minecraft stores its render targets). Both sides map it.
 */
public final class PixelFile implements AutoCloseable {
	// Mapped through an arena so close() unmaps it at once (a MappedByteBuffer waits for GC, and on Windows a mapped file
	// cannot be deleted).
	private final Arena arena;
	private final MemorySegment pixels;
	private final long bytesPerSlot;

	private PixelFile(Arena arena, MemorySegment pixels, long bytesPerSlot) {
		this.arena = arena;
		this.pixels = pixels;
		this.bytesPerSlot = bytesPerSlot;
	}

	/** Maps (creating it if needed) a file of {@code slots} regions for {@code width}×{@code height} frames. */
	public static PixelFile open(Path file, int width, int height, int slots) {
		long bytesPerSlot = (long) width * height * 4;
		Arena arena = Arena.ofShared();
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ,
				StandardOpenOption.WRITE)) {
			return new PixelFile(arena, channel.map(FileChannel.MapMode.READ_WRITE, 0, bytesPerSlot * slots, arena),
					bytesPerSlot);
		} catch (IOException e) {
			arena.close();
			throw new UncheckedIOException(e);
		}
	}

	/** The pixel file that goes with a slot table. */
	public static Path forControlFile(Path controlFile) {
		return controlFile.resolveSibling(controlFile.getFileName() + ".pixels");
	}

	/** Slot {@code slot}'s frame, as a direct buffer over the mapping (valid until {@link #close}). */
	public ByteBuffer region(int slot) {
		return pixels.asSlice(slot * bytesPerSlot, bytesPerSlot).asByteBuffer().order(ByteOrder.nativeOrder());
	}

	@Override
	public void close() {
		arena.close();
	}
}
