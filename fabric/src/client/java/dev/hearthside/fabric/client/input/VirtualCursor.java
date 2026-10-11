package dev.hearthside.fabric.client.input;

import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A controller-driven pointer for menus. Every window on the PC shares one OS cursor, so a
 * controller-driven client draws its own. Position is in window coordinates, the space
 * {@code MouseHandler} uses.
 */
public final class VirtualCursor {
	private static final int SIZE = 8;
	private static double x = -1;
	private static double y = -1;
	private static volatile boolean drawn;
	private static boolean registered;

	private VirtualCursor() {}

	/** Shows or hides the drawn pointer (satellites always; the host while it has a controller). */
	public static void setDrawn(boolean show) {
		drawn = show;
		if (show && !registered) {
			registered = true;
			ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) ->
					ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> draw(graphics, minecraft.getWindow())));
		}
	}

	public static double x() {
		return x;
	}

	public static double y() {
		return y;
	}

	/**
	 * Puts the cursor in the middle of the window, as it is now. Called whenever a menu opens: a position kept from
	 * an earlier menu may belong to an older window size (a guest window is tiled after its first screen).
	 */
	public static void centre(Window window) {
		x = window.getScreenWidth() / 2.0;
		y = window.getScreenHeight() / 2.0;
	}

	public static void moveTo(double newX, double newY, Window window) {
		x = Math.clamp(newX, 0, Math.max(0, window.getScreenWidth() - 1));
		y = Math.clamp(newY, 0, Math.max(0, window.getScreenHeight() - 1));
	}

	private static void draw(GuiGraphicsExtractor graphics, Window window) {
		if (!drawn || x < 0) {
			return;
		}
		int gx = (int) (x * window.getGuiScaledWidth() / window.getScreenWidth());
		int gy = (int) (y * window.getGuiScaledHeight() / window.getScreenHeight());
		graphics.fill(gx - 1, gy - 1, gx + SIZE + 1, gy + SIZE + 1, 0xFF000000);
		graphics.fill(gx, gy, gx + SIZE, gy + SIZE, 0xFFFFFFFF);
	}
}
