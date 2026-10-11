package dev.hearthside.fabric.client.compositor;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

/** How this game's split-screen views reach the screen. */
public final class Presentation {
	private Presentation() {}

	/** Satellite: the host asked for a real tiled window instead of compositing. */
	public static boolean satelliteTiled() {
		return "tiled".equals(System.getProperty("hearthside.presenter"));
	}

	/** Satellite or host: use the copy transport even if GPU sharing is available. */
	public static boolean forceCopy() {
		return Boolean.getBoolean("hearthside.forceCopy");
	}

	/**
	 * Whether this GL context has what the compositor needs. Minecraft creates a 3.3 core context; these are 4.x
	 * features that drivers usually expose as extensions there. Render thread.
	 */
	public static boolean glSupportsCompositing() {
		GLCapabilities caps = GL.getCapabilities();
		return caps.GL_ARB_direct_state_access && caps.GL_ARB_copy_image && caps.GL_ARB_clear_texture
				&& caps.GL_ARB_sync && caps.glBlitNamedFramebuffer != 0;
	}
}
