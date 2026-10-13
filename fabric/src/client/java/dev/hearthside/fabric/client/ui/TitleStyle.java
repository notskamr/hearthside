package dev.hearthside.fabric.client.ui;

import dev.hearthside.core.config.TitleStyleSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The title screen in the Cozy-hearth style (Spec A section 4.1): the plank wall, firelight and embers instead of the
 * panorama, a wood sign behind the logo, and wooden buttons. Drawn by the title-screen mixins, unless player 1 chose
 * Minecraft's own title screen (Spec E section 3.5), which applies from the next time the title screen opens.
 */
public final class TitleStyle {
	private static final EmberLayer EMBERS = new EmberLayer(29, 50);
	/** Set once each part has been drawn (the E2E checks the style is applied). */
	public static volatile boolean drewBackground, drewButtons;
	/** The style of the title screen open now, as the setting was when it was shown. */
	private static volatile TitleStyleSetting style = TitleStyleSetting.HEARTHSIDE;

	private TitleStyle() {}

	/** A title screen is being shown (a new one, or the same one again): it keeps this style until it is shown next. */
	public static void opening(TitleStyleSetting setting) {
		style = setting;
	}

	/** Whether the open title screen is drawn in the Cozy-hearth style (otherwise it is Minecraft's own, untouched). */
	public static boolean applies() {
		return style == TitleStyleSetting.HEARTHSIDE;
	}

	/** The background, and the sign the logo sits on. */
	public static void background(GuiGraphicsExtractor g, int width, int height) {
		HearthDraw.board(g, 0, 0, width, height);
		double t = System.nanoTime() / 1e9;
		float flicker = (float) (0.85 + 0.08 * Math.sin(t * 7.3) + 0.05 * Math.sin(t * 13.1 + 1.7));
		HearthDraw.firelightGlow(g, width / 10, height * 35 / 100, width * 8 / 10, height - height * 35 / 100, flicker);
		EMBERS.draw(g, 0, 0, width, height, false);
		// The logo is 256 wide at y 30 with "Java Edition" under it: a sign behind both.
		HearthDraw.wood(g, width / 2 - 146, 20, 292, 70);
		drewBackground = true;
	}
}
