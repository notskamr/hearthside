package dev.hearthside.fabric.client.ui;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.PadStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * Drawing in Hearthside's visual language (Spec A section 4): wood and parchment for the title and lobby, Ember glass
 * over the world, pixel-art glyphs for buttons. All art comes from {@link HearthSprites}.
 */
public final class HearthDraw {
	/** Firelight at the bottom edge of the lobby: #ff963c at about 35 % alpha. */
	private static final int FIRELIGHT = 0x59FF963C;
	public static final int INK = 0xFF3A2410;
	public static final int CREAM = 0xFFF3DFB6;

	private HearthDraw() {}

	public static void wood(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.WOOD_PANEL, x, y, width, height);
	}

	public static void parchment(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.PARCHMENT_CARD, x, y, width, height);
	}

	/** A pixel frame in a player's colour (0xRRGGBB). */
	public static void frame(GuiGraphicsExtractor g, int x, int y, int width, int height, int rgb) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.CARD_FRAME, x, y, width, height, 0xFF000000 | rgb);
	}

	public static void glass(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.GLASS_PANEL, x, y, width, height);
	}

	public static void chip(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.GLASS_CHIP, x, y, width, height);
	}

	/** Firelight glowing up from the bottom of the area (the lower third). */
	public static void firelight(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		int top = y + height - height / 3;
		g.fillGradient(x, top, x + width, y + height, FIRELIGHT & 0x00FFFFFF, FIRELIGHT);
	}

	/** A heading: Minecraft's font at twice the size, centred on {@code centreX}. */
	public static void heading(GuiGraphicsExtractor g, Font font, Component text, int centreX, int y, int argb) {
		g.pose().pushMatrix();
		g.pose().translate(centreX, y);
		g.pose().scale(2, 2);
		g.centeredText(font, text, 0, 0, argb);
		g.pose().popMatrix();
	}

	/** Width in GUI pixels of a control's glyph (as generated: two-letter labels are wider). */
	public static int glyphWidth(Control control) {
		return switch (control) {
			case LEFT_BUMPER, RIGHT_BUMPER, LEFT_TRIGGER, RIGHT_TRIGGER, LEFT_STICK, RIGHT_STICK -> 15;
			default -> 11;
		};
	}

	public static final int GLYPH_HEIGHT = 11;

	/** Draws a control's glyph for this controller family; returns its width. */
	public static int glyph(GuiGraphicsExtractor g, Control control, PadStyle style, int x, int y) {
		int width = glyphWidth(control);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.glyph(control, style), x, y, width, GLYPH_HEIGHT);
		return width;
	}
}
