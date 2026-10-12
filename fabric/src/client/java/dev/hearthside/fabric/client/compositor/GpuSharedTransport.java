package dev.hearthside.fabric.client.compositor;

import static org.lwjgl.opengl.EXTMemoryObject.*;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.*;
import static org.lwjgl.opengl.GL11C.*;

import dev.hearthside.core.compositor.FrameNames;
import dev.hearthside.core.compositor.ImageDescription;
import dev.hearthside.core.compositor.SlotTable;
import dev.hearthside.fabric.client.compositor.backend.ExportDevice;
import dev.hearthside.fabric.client.compositor.backend.ExportedSlot;
import dev.hearthside.fabric.client.compositor.backend.SharedImages;

/**
 * Zero-copy frame slots, guest side: each slot is an image the {@link ExportDevice} allocates and exports under a name,
 * imported into this guest's GL by handle. The host imports the same memory by name through its own backend's
 * {@link SharedImages} ({@code GuestImport}), so both games use the same GPU memory.
 */
public final class GpuSharedTransport implements FrameTransport {
	private final int width, height;
	private final int[] memoryObjects, textures;
	// Guest only: the exported slots that own the memory.
	private final ExportedSlot[] exported;

	private GpuSharedTransport(int width, int height, int slots) {
		this.width = width;
		this.height = height;
		this.memoryObjects = new int[slots];
		this.textures = new int[slots];
		this.exported = new ExportedSlot[slots];
	}

	/**
	 * Guest: allocates and exports {@code table.slots()} images on the export device, records each one's size and
	 * memory type in the table, then the images' description.
	 */
	public static GpuSharedTransport export(String controlFile, SlotTable table, int width, int height) {
		SharedImages images = SharedImages.forThisGame();
		ImageDescription description = images.describe(width, height);
		ExportDevice device = ExportDevice.forGpu(images.facts().deviceUuid());
		GpuSharedTransport transport = new GpuSharedTransport(width, height, table.slots());
		try {
			for (int slot = 0; slot < table.slots(); slot++) {
				transport.exportSlot(device, description, slot,
						FrameNames.gpuMemory(controlFile, table.generation(), slot), table);
			}
			table.setDescription(description);
		} catch (RuntimeException e) {
			transport.close();
			throw e;
		}
		return transport;
	}

	private void exportSlot(ExportDevice device, ImageDescription description, int slot, String name, SlotTable table) {
		ExportedSlot memory = device.export(description, name);
		exported[slot] = memory;
		drainGlErrors();
		int memoryObject = glCreateMemoryObjectsEXT();
		memoryObjects[slot] = memoryObject;
		glMemoryObjectParameteriEXT(memoryObject, GL_DEDICATED_MEMORY_OBJECT_EXT, GL_TRUE);
		glImportMemoryWin32HandleEXT(memoryObject, memory.allocationSize(), GL_HANDLE_TYPE_OPAQUE_WIN32_EXT,
				memory.handle());
		textures[slot] = textureOn(memoryObject);
		glCheck("importing own " + name);
		table.setMemorySize(slot, memory.allocationSize());
		table.setMemoryType(slot, memory.memoryType());
	}

	private int textureOn(int memory) {
		int previous = glGetInteger(GL_TEXTURE_BINDING_2D);
		int texture = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, texture);
		glTexStorageMem2DEXT(GL_TEXTURE_2D, 1, org.lwjgl.opengl.GL11C.GL_RGBA8, width, height, memory, 0);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glBindTexture(GL_TEXTURE_2D, previous);
		return texture;
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
	public void afterDraw(int slot) {}

	@Override
	public void beforePublish(int slot) {}

	@Override
	public void beforeRead(int slot) {}

	@Override
	public void close() {
		for (int slot = 0; slot < textures.length; slot++) {
			if (textures[slot] != 0) {
				glDeleteTextures(textures[slot]);
			}
			if (memoryObjects[slot] != 0) {
				glDeleteMemoryObjectsEXT(memoryObjects[slot]);
			}
			if (exported[slot] != null) {
				exported[slot].close();
			}
		}
		// The export device itself is shared by every export in this process and lives as long as it does.
	}

	/** Minecraft may have left an error pending; clear it so it is not blamed on our next call. */
	static void drainGlErrors() {
		for (int i = 0; i < 16 && glGetError() != GL_NO_ERROR; i++) {
			// discard
		}
	}

	static void glCheck(String what) {
		int error = glGetError();
		if (error != GL_NO_ERROR) {
			throw new IllegalStateException(what + ": GL error 0x" + Integer.toHexString(error));
		}
	}
}
