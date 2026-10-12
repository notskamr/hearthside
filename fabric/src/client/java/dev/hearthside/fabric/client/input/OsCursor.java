package dev.hearthside.fabric.client.input;

import org.lwjgl.sdl.SDLMouse;

/**
 * The OS arrow over player 1's window. It is hidden while their controller's pointer drives a screen, so only one
 * pointer shows (and the idle arrow doesn't turn into a hand over whatever the pointer hovers), and shown again when the
 * mouse takes over, in play, and whenever the controller goes. Render thread.
 */
public final class OsCursor {
	private static boolean hidden;

	private OsCursor() {}

	public static void setHidden(boolean hide) {
		if (hide == hidden) {
			return;
		}
		hidden = hide;
		if (hide) {
			SDLMouse.SDL_HideCursor();
		} else {
			SDLMouse.SDL_ShowCursor();
		}
	}
}
