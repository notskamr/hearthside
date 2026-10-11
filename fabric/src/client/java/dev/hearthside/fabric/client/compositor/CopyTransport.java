package dev.hearthside.fabric.client.compositor;

import static org.lwjgl.opengl.GL45C.*;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The fallback transport: the guest reads each frame back (asynchronously, through a pixel buffer) into a
 * memory-mapped file with one region per slot; the host uploads the region into its own texture. Costs about half a
 * millisecond per 960×1080 frame on each side (see {@code docs/research/compositor-spike.md}).
 */
public final class CopyTransport implements FrameTransport {
	private final int width, height;
	private final int[] textures;
	private final int[] pixelBuffers; // guest only
	private final MappedByteBuffer pixels;
	private final long bytesPerSlot;

	private CopyTransport(Path pixelFile, int width, int height, int slots, boolean guest) {
		this.width = width;
		this.height = height;
		this.bytesPerSlot = (long) width * height * 4;
		this.textures = new int[slots];
		this.pixelBuffers = new int[guest ? slots : 0];
		try (FileChannel channel = FileChannel.open(pixelFile, StandardOpenOption.CREATE, StandardOpenOption.READ,
				StandardOpenOption.WRITE)) {
			this.pixels = channel.map(FileChannel.MapMode.READ_WRITE, 0, bytesPerSlot * slots);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		for (int slot = 0; slot < slots; slot++) {
			textures[slot] = glCreateTextures(GL_TEXTURE_2D);
			glTextureStorage2D(textures[slot], 1, GL_RGBA8, width, height);
			glTextureParameteri(textures[slot], GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			glTextureParameteri(textures[slot], GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			if (guest) {
				pixelBuffers[slot] = glCreateBuffers();
				glNamedBufferData(pixelBuffers[slot], bytesPerSlot, GL_STREAM_READ);
			}
		}
	}

	/** Guest side. {@code pixelFile} holds {@code slots × width × height × 4} bytes. */
	public static CopyTransport guest(Path pixelFile, int width, int height, int slots) {
		return new CopyTransport(pixelFile, width, height, slots, true);
	}

	/** Host side, over the guest's pixel file. */
	public static CopyTransport host(Path pixelFile, int width, int height, int slots) {
		return new CopyTransport(pixelFile, width, height, slots, false);
	}

	/** The pixel file that goes with a slot table. */
	public static Path pixelFileFor(Path controlFile) {
		return controlFile.resolveSibling(controlFile.getFileName() + ".pixels");
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

	@Override
	public void afterDraw(int slot) {
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
			memCopy(memAddress(mapped), memAddress(pixels) + slot * bytesPerSlot, bytesPerSlot);
		} finally {
			glUnmapNamedBuffer(pixelBuffers[slot]);
		}
	}

	@Override
	public void beforeRead(int slot) {
		int previous = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
		glTextureSubImage2D(textures[slot], 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE,
				memAddress(pixels) + slot * bytesPerSlot);
		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, previous);
	}

	@Override
	public void close() {
		for (int texture : textures) {
			glDeleteTextures(texture);
		}
		for (int buffer : pixelBuffers) {
			glDeleteBuffers(buffer);
		}
	}
}
