package dev.hearthside.fabric.client.compositor.backend;

import static org.lwjgl.opengl.EXTMemoryObject.*;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.*;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LEVEL;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LOD;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MIN_LOD;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.opengl.FrameBufferCache;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import dev.hearthside.core.compositor.ImageDescription;
import dev.hearthside.core.compositor.SharingFacts;
import dev.hearthside.fabric.client.mixin.backend.FrontendGpuDeviceAccessor;
import dev.hearthside.fabric.client.mixin.backend.GlDeviceAccessor;
import java.nio.ByteBuffer;
import java.util.UUID;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;

/**
 * Shared images on Minecraft's OpenGL backend: the memory is imported as a memory object
 * ({@code GL_EXT_memory_object_win32}) and given to a texture, which is wrapped as a Minecraft {@code GlTexture}.
 * OpenGL has no ownership transfer, so {@link #beforeUse} and {@link #afterUse} do nothing.
 */
final class GlSharedImages implements SharedImages {
	private final FrameBufferCache frameBufferCache;
	private final SharingFacts facts;

	GlSharedImages(GpuDevice device) {
		this.frameBufferCache = ((GlDeviceAccessor) ((FrontendGpuDeviceAccessor) device).hearthside$backend())
				.hearthside$frameBufferCache();
		this.facts = probe();
	}

	private static SharingFacts probe() {
		GLCapabilities caps = GL.getCapabilities();
		if (!caps.GL_EXT_memory_object) {
			return new SharingFacts(SharedImages.windows(), false, UNKNOWN, UNKNOWN);
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer bytes = stack.malloc(GL_UUID_SIZE_EXT);
			glGetUnsignedBytei_vEXT(GL_DEVICE_UUID_EXT, 0, bytes);
			UUID device = ImageDescription.uuidOf(array(bytes));
			glGetUnsignedBytevEXT(GL_DRIVER_UUID_EXT, bytes);
			UUID driver = ImageDescription.uuidOf(array(bytes));
			boolean canImport = SharedImages.windows() && caps.GL_EXT_memory_object_win32;
			return new SharingFacts(SharedImages.windows(), canImport, device, driver);
		}
	}

	private static byte[] array(ByteBuffer buffer) {
		byte[] bytes = new byte[GL_UUID_SIZE_EXT];
		buffer.get(0, bytes);
		return bytes;
	}

	@Override
	public SharingFacts facts() {
		return facts;
	}

	@Override
	public GpuTexture importSlot(ImageDescription image, SlotMemory memory, String label) throws ImportFailed {
		if (!facts.canImport()) {
			throw new ImportFailed("OpenGL here can't import shared memory");
		}
		if (!image.sameImage(describe(image.width(), image.height()))) {
			throw new ImportFailed("Not the image this game makes: " + image);
		}
		GlStateManager.clearGlErrors();
		int memoryObject = glCreateMemoryObjectsEXT();
		int texture = 0;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			glMemoryObjectParameteriEXT(memoryObject, GL_DEDICATED_MEMORY_OBJECT_EXT, GL_TRUE);
			switch (memory.source()) {
				case SlotMemory.Source.ByName(String name) -> glImportMemoryWin32NameEXT(memoryObject,
						memory.allocationSize(), GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, memAddress(stack.UTF16(name)));
				case SlotMemory.Source.ByHandle(long handle) -> glImportMemoryWin32HandleEXT(memoryObject,
						memory.allocationSize(), GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handle);
			}
			check("importing " + memory.source());
			// As Minecraft's GlDevice.createTexture sets up a one-level texture, but on the imported memory.
			texture = GlStateManager._genTexture();
			GlStateManager._bindTexture(texture);
			GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
			GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_LOD, 0);
			GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAX_LOD, 0);
			glTexStorageMem2DEXT(GL_TEXTURE_2D, 1, GL_RGBA8, image.width(), image.height(), memoryObject, 0);
			check("giving the texture its memory");
			return new ImportedGlTexture(SLOT_USAGE, label, image.width(), image.height(), texture, frameBufferCache,
					memoryObject);
		} catch (ImportFailed e) {
			if (texture != 0) {
				GlStateManager._deleteTexture(texture);
			}
			glDeleteMemoryObjectsEXT(memoryObject);
			throw e;
		}
	}

	private static void check(String what) throws ImportFailed {
		int error = GlStateManager._getError();
		if (error != GL_NO_ERROR) {
			throw new ImportFailed(what + ": GL error 0x" + Integer.toHexString(error));
		}
	}

	@Override
	public void beforeUse(GpuTexture texture, CommandEncoder encoder) {}

	@Override
	public void afterUse(GpuTexture texture, CommandEncoder encoder) {}
}
