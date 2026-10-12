package dev.hearthside.fabric.client.compositor;

import com.mojang.blaze3d.platform.Window;
import dev.hearthside.core.compositor.VirtualSize;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.mixin.WindowAccessor;
import java.nio.IntBuffer;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

/**
 * Makes this game render and lay out its GUI at a viewport's size instead of its window's. Minecraft sizes its main
 * render target and GUI from the window's size fields, while the real swapchain follows the SDL window; so setting the
 * fields changes what the game draws without touching the window. Render thread only.
 */
public final class VirtualSizing {
	private static VirtualSize current;
	private static boolean rewriting;

	private VirtualSizing() {}

	public static Optional<VirtualSize> current() {
		return Optional.ofNullable(current);
	}

	/** Renders at {@code size} from now on; re-applied whenever the real window changes size. */
	public static void apply(Minecraft minecraft, VirtualSize size) {
		if (size.equals(current)) {
			return;
		}
		current = size;
		write(minecraft.getWindow(), size);
		minecraft.resizeGui();
		HearthsideMod.LOGGER.info("Hearthside virtual size {}x{}", size.framebufferWidth(), size.framebufferHeight());
	}

	/** Goes back to rendering at the real window size. */
	public static void clear(Minecraft minecraft) {
		if (current == null) {
			return;
		}
		current = null;
		Window window = minecraft.getWindow();
		WindowAccessor fields = (WindowAccessor) (Object) window;
		fields.hearthside$refreshFramebufferSize();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1);
			SDLVideo.SDL_GetWindowSize(window.handle(), w, h);
			fields.hearthside$setScreenWidth(w.get(0));
			fields.hearthside$setScreenHeight(h.get(0));
		}
		minecraft.resizeGui();
		HearthsideMod.LOGGER.info("Hearthside virtual size cleared");
	}

	/**
	 * Before Minecraft switches fullscreen on or off: puts the real window size in the screen-size fields, which it
	 * records as the size to go back to. The virtual size comes back when the switch returns ({@link #reapply}).
	 */
	public static void realScreenSize(Window window) {
		if (current == null) {
			return;
		}
		WindowAccessor fields = (WindowAccessor) (Object) window;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1);
			SDLVideo.SDL_GetWindowSize(window.handle(), w, h);
			fields.hearthside$setScreenWidth(w.get(0));
			fields.hearthside$setScreenHeight(h.get(0));
		}
	}

	/** Called after Minecraft has written real sizes into the window (resize events); puts the virtual size back. */
	public static void reapply(Window window) {
		VirtualSize size = current;
		if (size == null || rewriting) {
			return;
		}
		boolean changed = window.getWidth() != size.framebufferWidth() || window.getHeight() != size.framebufferHeight()
				|| window.getScreenWidth() != size.screenWidth() || window.getScreenHeight() != size.screenHeight();
		if (!changed) {
			return;
		}
		write(window, size);
		rewriting = true;
		try {
			// Minecraft just laid out the GUI for the real size; lay it out again for the virtual one.
			Minecraft.getInstance().resizeGui();
		} finally {
			rewriting = false;
		}
	}

	private static void write(Window window, VirtualSize size) {
		WindowAccessor fields = (WindowAccessor) (Object) window;
		fields.hearthside$setFramebufferWidth(size.framebufferWidth());
		fields.hearthside$setFramebufferHeight(size.framebufferHeight());
		fields.hearthside$setScreenWidth(size.screenWidth());
		fields.hearthside$setScreenHeight(size.screenHeight());
	}
}
