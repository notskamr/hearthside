package dev.hearthside.fabric.client.compositor;

import com.mojang.blaze3d.platform.Window;
import dev.hearthside.fabric.HearthsideMod;

/**
 * The party's GUI scale for this game (Spec D section 3.4), in place of the one Minecraft would pick from its own
 * setting: player 1's game sets it from the party, a guest's from its {@code SetViewport}. Minecraft's GUI-scale
 * option is never written, so nothing of it is saved ({@code GuiScaleMixin} applies it). Render thread.
 */
public final class GuiScaleOverride {
	private static int forced;
	private static String logged;

	private GuiScaleOverride() {}

	/** The GUI scale to use from the next GUI layout on; 0 goes back to Minecraft's own choice. */
	public static void set(int scale) {
		forced = Math.max(0, scale);
	}

	/** Split-screen ended: Minecraft chooses again, and the next session logs its scale afresh. */
	public static void clear() {
		forced = 0;
		logged = null;
	}

	/** The forced GUI scale, or 0 when Minecraft chooses. */
	public static int forced() {
		return forced;
	}

	/**
	 * Logs the GUI scale this game lays out at, the frame it renders and the view that frame is drawn into, whenever one
	 * of them changed (the E2E compares every player's).
	 */
	public static void log(int player, Window window, int viewWidth, int viewHeight) {
		String line = "player=" + player + " scale=" + window.getGuiScale() + " frame=" + window.getWidth() + "x"
				+ window.getHeight() + " view=" + viewWidth + "x" + viewHeight;
		if (!line.equals(logged)) {
			logged = line;
			HearthsideMod.LOGGER.info("Hearthside gui scale {}", line);
		}
	}
}
