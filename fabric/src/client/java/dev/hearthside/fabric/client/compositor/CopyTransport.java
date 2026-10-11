package dev.hearthside.fabric.client.compositor;

import static org.lwjgl.opengl.GL45C.*;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 * The fallback transport, guest side: the guest reads each frame back (asynchronously, through a pixel buffer) into its
 * slot's region of the shared {@link PixelFile}; the host uploads the region into its own texture ({@code GuestImport}).
 * Costs about half a millisecond per 960×1080 frame on each side (see {@code docs/research/compositor-spike.md}).
 */
public final class CopyTransport implements FrameTransport {
	private final int width, height;
	private final int[] textures;
	private final int[] pixelBuffers;
	private final PixelFile pixels;
	private final long bytesPerSlot;

	private CopyTransport(Path pixelFile, int width, int height, int slots) {
		this.width = width;
		this.height = height;
		this.bytesPerSlot = (long) width * height * 4;
		this.textures = new int[slots];
		this.pixelBuffers = new int[slots];
		this.pixels = PixelFile.open(pixelFile, width, height, slots);
		for (int slot = 0; slot < slots; slot++) {
			textures[slot] = glCreateTextures(GL_TEXTURE_2D);
			glTextureStorage2D(textures[slot], 1, GL_RGBA8, width, height);
			glTextureParameteri(textures[slot], GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			glTextureParameteri(textures[slot], GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			pixelBuffers[slot] = glCreateBuffers();
			glNamedBufferData(pixelBuffers[slot], bytesPerSlot, GL_STREAM_READ);
		}
	}

	/** Guest side. {@code pixelFile} holds {@code slots × width × height × 4} bytes. */
	public static CopyTransport guest(Path pixelFile, int width, int height, int slots) {
		return new CopyTransport(pixelFile, width, height, slots);
	}

	/** The pixel file that goes with a slot table. */
	public static Path pixelFileFor(Path controlFile) {
		return PixelFile.forControlFile(controlFile);
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public int height() {
		return height;
	}

	@Override
	public int slots() {
		return textures.length;
	}

	@Override
	public int glTexture(int slot) {
		return textures[slot];
	}

	/**
	 * Minecraft leaves its last upload's row length and skips in the pixel-store state; our transfers are tightly
	 * packed whole images. Set through {@link GlStateManager} so its state cache stays right; Minecraft sets these
	 * again before each of its own transfers.
	 */
	private static void tightlyPacked(int rowLength, int skipRows, int skipPixels, int alignment) {
		GlStateManager._pixelStore(rowLength, 0);
		GlStateManager._pixelStore(skipRows, 0);
		GlStateManager._pixelStore(skipPixels, 0);
		GlStateManager._pixelStore(alignment, 4);
	}

	@Override
	public void afterDraw(int slot) {
		tightlyPacked(GL_PACK_ROW_LENGTH, GL_PACK_SKIP_ROWS, GL_PACK_SKIP_PIXELS, GL_PACK_ALIGNMENT);
		int previous = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
		glBindBuffer(GL_PIXEL_PACK_BUFFER, pixelBuffers[slot]);
		glGetTextureImage(textures[slot], 0, GL_RGBA, GL_UNSIGNED_BYTE, (int) bytesPerSlot, 0L);
		glBindBuffer(GL_PIXEL_PACK_BUFFER, previous);
	}

	@Override
	public void beforePublish(int slot) {
		ByteBuffer mapped = glMapNamedBufferRange(pixelBuffers[slot], 0, bytesPerSlot, GL_MAP_READ_BIT);
		if (mapped == null) {
			throw new IllegalStateException("Could not map the frame read-back buffer");
		}
		try {
			memCopy(memAddress(mapped), memAddress(pixels.region(slot)), bytesPerSlot);
		} finally {
			glUnmapNamedBuffer(pixelBuffers[slot]);
		}
	}

	/** The host reads the pixel file through {@code GuestImport}, never through this class. */
	@Override
	public void beforeRead(int slot) {
		throw new UnsupportedOperationException("CopyTransport is the guest side only");
	}

	@Override
	public void close() {
		for (int texture : textures) {
			glDeleteTextures(texture);
		}
		for (int buffer : pixelBuffers) {
			glDeleteBuffers(buffer);
		}
		pixels.close();
	}
}
