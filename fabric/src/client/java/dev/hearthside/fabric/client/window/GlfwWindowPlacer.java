package dev.hearthside.fabric.client.window;

import com.mojang.blaze3d.platform.Window;
import dev.hearthside.core.layout.Rect;
import org.lwjgl.glfw.GLFW;

/** Moves game windows into split-screen viewports. Every method must run on the render thread. */
public final class GlfwWindowPlacer {
	private GlfwWindowPlacer() {}

	/** The primary monitor's area excluding the taskbar, in screen coordinates. */
	public static Rect primaryWorkArea() {
		int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
		GLFW.glfwGetMonitorWorkarea(GLFW.glfwGetPrimaryMonitor(), x, y, w, h);
		return new Rect(x[0], y[0], w[0], h[0]);
	}

	/** Makes the window borderless and fits it exactly into {@code rect}. */
	public static void place(Window window, Rect rect) {
		long handle = window.handle();
		GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
		GLFW.glfwSetWindowPos(handle, rect.x(), rect.y());
		GLFW.glfwSetWindowSize(handle, rect.width(), rect.height());
	}

	public static void focus(Window window) {
		GLFW.glfwFocusWindow(window.handle());
	}
}
