package dev.hearthside.fabric.client.compositor.backend;

import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;

import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import org.lwjgl.opengl.ARBCopyImage;
import org.lwjgl.opengl.GL;

/**
 * Copies part of one texture into part of another. Minecraft 26.3's OpenGL backend hands {@code copyTextureToTexture}'s
 * width and height to {@code glBlitNamedFramebuffer} as the far corner of both rectangles, which is only right when both
 * offsets are 0 (all Minecraft itself ever copies). So on OpenGL a copy at an offset uses {@code glCopyImageSubData};
 * Vulkan's copy is right as it is. Render thread, outside any render pass.
 */
public final class TextureRegions {
	private TextureRegions() {}

	public static void copy(CommandEncoder encoder, GpuTexture source, GpuTexture destination, int destX, int destY,
			int sourceX, int sourceY, int width, int height) {
		boolean offset = destX != 0 || destY != 0 || sourceX != 0 || sourceY != 0;
		if (offset && source instanceof GlTexture from && destination instanceof GlTexture to
				&& GL.getCapabilities().glCopyImageSubData != 0) {
			ARBCopyImage.glCopyImageSubData(from.glId(), GL_TEXTURE_2D, 0, sourceX, sourceY, 0, to.glId(), GL_TEXTURE_2D, 0,
					destX, destY, 0, width, height, 1);
			return;
		}
		encoder.copyTextureToTexture(source, destination, 0, destX, destY, sourceX, sourceY, width, height);
	}
}
