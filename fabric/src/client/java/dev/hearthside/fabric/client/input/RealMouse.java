package dev.hearthside.fabric.client.input;

import com.mojang.blaze3d.platform.Window;
import dev.hearthside.core.compositor.ViewportMath;
import dev.hearthside.core.input.PointerHandOff;
import dev.hearthside.fabric.client.compositor.Compositor;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.system.MemoryStack;

/**
 * Player 1's real OS mouse: where its arrow is, and whether it or their controller's pointer drives their screens
 * ({@link PointerHandOff}: a real move past a few pixels or any real click takes over, the controller takes back on its
 * next pointer input). Hearthside's own synthetic events never count, nor does the motion event SDL sends when Minecraft
 * itself warps the cursor (grabbing it for play, releasing it for a screen). Render thread.
 */
public final class RealMouse {
	/** How soon after a warp its echo arrives, at most. */
	private static final long WARP_ECHO_NANOS = 250_000_000L;
	private static final PointerHandOff HAND_OFF = new PointerHandOff();

	private static double warpX = Double.NaN, warpY = Double.NaN;
	private static long warpedAt;
	/** Where the OS arrow is, window coordinates: the last real move or warp; NaN until there was one. */
	private static double arrowX = Double.NaN, arrowY = Double.NaN;

	private RealMouse() {}

	/** Who drives player 1's screens. */
	public static PointerHandOff handOff() {
		return HAND_OFF;
	}

	/** Minecraft moved the OS cursor to (x, y), window coordinates. */
	public static void warped(double x, double y) {
		warpX = x;
		warpY = y;
		warpedAt = System.nanoTime();
		arrowX = x;
		arrowY = y;
	}

	/**
	 * An OS mouse move arrived at (x, y), window coordinates, by (xrel, yrel): player 1 using the mouse on their screen.
	 * The distance is SDL's relative motion where it has one (in play the cursor is held still), else from the last move.
	 */
	public static void moved(double x, double y, double xrel, double yrel) {
		long now = System.nanoTime();
		boolean echo = now - warpedAt < WARP_ECHO_NANOS && Math.abs(x - warpX) < 1 && Math.abs(y - warpY) < 1;
		boolean known = !Double.isNaN(arrowX);
		double dx = xrel != 0 || yrel != 0 ? xrel : known ? x - arrowX : 0;
		double dy = xrel != 0 || yrel != 0 ? yrel : known ? y - arrowY : 0;
		arrowAt(x, y);
		if (echo) {
			warpX = Double.NaN; // the warp's own echo, once
			return;
		}
		HAND_OFF.mouseMoved(dx, dy, now);
	}

	/** The OS arrow is at (x, y), window coordinates, without player 1 using their screen (it is over another view). */
	public static void arrowAt(double x, double y) {
		arrowX = x;
		arrowY = y;
	}

	/**
	 * An OS mouse button or the wheel was used: the mouse drives. Returns whether the controller's pointer was driving,
	 * so the game's mouse is where the pointer is and must first be put where the arrow is ({@link #resync}).
	 */
	public static boolean clicked() {
		return HAND_OFF.mouseClicked(System.nanoTime());
	}

	/**
	 * Puts the game's mouse where the OS arrow is (into player 1's view while the host composites, as real moves are),
	 * so a click lands where player 1 sees the arrow. Only on a screen with the cursor free: in play the position is the
	 * camera's, not a place to click.
	 */
	public static void resync(Minecraft minecraft) {
		MouseHandler mouse = minecraft.mouseHandler;
		if (minecraft.gui.screen() == null || mouse.isMouseGrabbed()) {
			return;
		}
		Window window = minecraft.getWindow();
		if (Double.isNaN(arrowX)) {
			// No real move or warp seen yet: ask SDL where the arrow is.
			try (MemoryStack stack = MemoryStack.stackPush()) {
				FloatBuffer x = stack.mallocFloat(1);
				FloatBuffer y = stack.mallocFloat(1);
				SDLMouse.SDL_GetMouseState(x, y);
				arrowAt(x.get(0), y.get(0));
			}
		}
		ViewportMath.Point at = new ViewportMath.Point(arrowX, arrowY);
		Compositor compositor = Compositor.active();
		if (compositor != null) {
			at = ViewportMath.windowToViewport(at, compositor.hostViewport(), compositor.screenPerFramebuffer());
		}
		double x = at.x(), y = at.y(), fromX = mouse.xpos(), fromY = mouse.ypos();
		if (x != fromX || y != fromY) {
			SyntheticMouse.run(() -> mouse.onMove(window.handle(), x, y, x - fromX, y - fromY));
		}
	}
}
