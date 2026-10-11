package dev.hearthside.fabric.client.compositor.backend;

import static org.lwjgl.opengl.EXTMemoryObject.glDeleteMemoryObjectsEXT;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.backend.opengl.FrameBufferCache;
import com.mojang.renderpearl.backend.opengl.GlTexture;

/**
 * A Minecraft OpenGL texture whose storage is imported shared memory. It owns the memory object and deletes it once
 * the texture itself is gone, which {@code GlTexture} does when it is closed and its last view is closed.
 */
final class ImportedGlTexture extends GlTexture {
	private int memoryObject;
	private int views;

	ImportedGlTexture(int usage, String label, int width, int height, int id, FrameBufferCache frameBufferCache,
			int memoryObject) {
		super(usage, label, GpuFormat.RGBA8_UNORM, width, height, 1, 1, id, frameBufferCache);
		this.memoryObject = memoryObject;
	}

	@Override
	public void close() {
		super.close();
		deleteMemoryWhenUnused();
	}

	@Override
	public void addViews() {
		super.addViews();
		views++;
	}

	@Override
	public void removeViews() {
		super.removeViews();
		views--;
		deleteMemoryWhenUnused();
	}

	/** {@code GlTexture} deletes the texture when it is closed with no views left; the memory goes right after. */
	private void deleteMemoryWhenUnused() {
		if (closed && views == 0 && memoryObject != 0) {
			glDeleteMemoryObjectsEXT(memoryObject);
			memoryObject = 0;
		}
	}
}
