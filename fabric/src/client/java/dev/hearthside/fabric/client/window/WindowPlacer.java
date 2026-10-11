package dev.hearthside.fabric.client.window;

import com.mojang.blaze3d.platform.Window;
import dev.hearthside.core.layout.Rect;
import java.nio.IntBuffer;
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

	/** The window's current position and size. */
	public static Rect bounds(Window window) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer x = stack.mallocInt(1), y = stack.mallocInt(1), w = stack.mallocInt(1), h = stack.mallocInt(1);
			SDLVideo.SDL_GetWindowPosition(window.handle(), x, y);
			SDLVideo.SDL_GetWindowSize(window.handle(), w, h);
			return new Rect(x.get(0), y.get(0), w.get(0), h.get(0));
		}
	}

	/** Makes the window borderless and fits it exactly into {@code rect}. */
	public static void place(Window window, Rect rect) {
		long handle = window.handle();
		SDLVideo.SDL_SetWindowBordered(handle, false);
		SDLVideo.SDL_SetWindowPosition(handle, rect.x(), rect.y());
		SDLVideo.SDL_SetWindowSize(handle, rect.width(), rect.height());
	}

	/** Gives the window its border back and returns it to {@code rect}. */
	public static void restore(Window window, Rect rect) {
		long handle = window.handle();
		SDLVideo.SDL_SetWindowBordered(handle, true);
		SDLVideo.SDL_SetWindowSize(handle, rect.width(), rect.height());
		SDLVideo.SDL_SetWindowPosition(handle, rect.x(), rect.y());
	}

	/** The refresh rate of the display the window is on, rounded; 60 if SDL can't tell. */
	public static int refreshHz(Window window) {
		var mode = SDLVideo.SDL_GetCurrentDisplayMode(SDLVideo.SDL_GetDisplayForWindow(window.handle()));
		if (mode == null || mode.refresh_rate() < 1) {
			return 60;
		}
		return Math.clamp(Math.round(mode.refresh_rate()), 1, 1000);
	}

	public static void focus(Window window) {
		SDLVideo.SDL_RaiseWindow(window.handle());
	}
}
