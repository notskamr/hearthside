package dev.hearthside.fabric.client.compositor;

import dev.hearthside.core.compositor.ImageDescription;
import dev.hearthside.core.compositor.ProbeFacts;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.compositor.backend.ExportDevice;
import java.nio.ByteBuffer;
import java.util.UUID;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;

/** Finds out whether this GPU can share frames between processes without copying. Render thread (needs the GL context). */
public final class GlInterop {
	private GlInterop() {}

	public static ProbeFacts probe(boolean forceCopy) {
		GLCapabilities caps = GL.getCapabilities();
		boolean glImport = caps.GL_EXT_memory_object && caps.GL_EXT_memory_object_win32;
		boolean deviceMatches = false, vkExport = false;
		if (glImport) {
			try {
				ExportDevice.Match match = ExportDevice.find(glDeviceUuid());
				deviceMatches = match.found();
				vkExport = match.canExport();
			} catch (RuntimeException | LinkageError e) {
				HearthsideMod.LOGGER.warn("Hearthside could not probe Vulkan; using the copy transport", e);
			}
		}
		ProbeFacts facts = new ProbeFacts(glImport, deviceMatches, vkExport, forceCopy);
		HearthsideMod.LOGGER.info("Hearthside GPU sharing probe: {}", facts);
		return facts;
	}

	private static UUID glDeviceUuid() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer uuid = stack.malloc(EXTMemoryObject.GL_UUID_SIZE_EXT);
			EXTMemoryObject.glGetUnsignedBytei_vEXT(EXTMemoryObject.GL_DEVICE_UUID_EXT, 0, uuid);
			byte[] bytes = new byte[EXTMemoryObject.GL_UUID_SIZE_EXT];
			uuid.get(0, bytes);
			return ImageDescription.uuidOf(bytes);
		}
	}
}
