package dev.hearthside.core.compositor;

/**
 * The size a game is told to render at: its viewport, not its real window. Framebuffer sizes are in pixels; screen
 * sizes are in window coordinates (they differ on high-DPI displays).
 */
public record VirtualSize(int framebufferWidth, int framebufferHeight, int screenWidth, int screenHeight) {
	public VirtualSize {
		if (framebufferWidth < 1 || framebufferHeight < 1 || screenWidth < 1 || screenHeight < 1) {
			throw new IllegalArgumentException("Virtual size must be positive: " + framebufferWidth + "x" + framebufferHeight
					+ " / " + screenWidth + "x" + screenHeight);
		}
	}
}
