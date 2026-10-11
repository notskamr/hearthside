package dev.hearthside.fabric.client.window;

import com.mojang.blaze3d.platform.Window;
import dev.hearthside.core.layout.Rect;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.system.MemoryStack;

/**
 * Moves game windows into split-screen viewports. Minecraft 26.x windows are SDL3 windows, so
 * {@link Window#handle()} is an {@code SDL_Window*}. Every method must run on the render thread.
 */
public final class WindowPlacer {
	private WindowPlacer() {}

	/** The primary display's area excluding the taskbar, in screen coordinates. */
	public static Rect primaryWorkArea() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			SDL_Rect bounds = SDL_Rect.malloc(stack);
			if (!SDLVideo.SDL_GetDisplayUsableBounds(SDLVideo.SDL_GetPrimaryDisplay(), bounds)) {
				throw new IllegalStateException("Could not read the primary display's usable bounds");
			}
			return new Rect(bounds.x(), bounds.y(), bounds.w(), bounds.h());
		}
	}

	/** Makes the window borderless and fits it exactly into {@code rect}. */
	public static void place(Window window, Rect rect) {
		long handle = window.handle();
		SDLVideo.SDL_SetWindowBordered(handle, false);
		SDLVideo.SDL_SetWindowPosition(handle, rect.x(), rect.y());
		SDLVideo.SDL_SetWindowSize(handle, rect.width(), rect.height());
	}

	public static void focus(Window window) {
		SDLVideo.SDL_RaiseWindow(window.handle());
	}
}
