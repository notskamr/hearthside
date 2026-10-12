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
	/** Secondary text on parchment (dates, who played, hints): a warm faded ink. */
	public static final int MUTED = 0xFF7A6A58;
	/** D-pad glyphs drawn with their pressed arm painted in the accent. For the E2E. */
	public static volatile int dpadArmsDrawn;

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

	/** A heading: Minecraft's font at twice the size with a warm shadow, centred on {@code centreX}. */
	public static void heading(GuiGraphicsExtractor g, Font font, Component text, int centreX, int y, int argb) {
		g.pose().pushMatrix();
		g.pose().translate(centreX, y);
		g.pose().scale(2, 2);
		label(g, font, text, -font.width(text) / 2, 0, argb);
		g.pose().popMatrix();
	}

	/**
	 * Light text with a shadow. Minecraft's shadow is the text's own colour at a quarter brightness, so cream text gets
	 * a warm dark brown (only dark text gets a black one, which is why ink never has a shadow). Drawing a shadow as a
	 * second text call doesn't work: the GUI may draw the two in either order.
	 */
	public static void label(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int argb) {
		g.text(font, text, x, y, argb, true);
	}

	public static void labelCentred(GuiGraphicsExtractor g, Font font, Component text, int centreX, int y, int argb) {
		label(g, font, text, centreX - font.width(text) / 2, y, argb);
	}

	/** Dark text on parchment: printed ink, so no shadow. */
	public static void inkCentred(GuiGraphicsExtractor g, Font font, Component text, int centreX, int y, int argb) {
		g.text(font, text, centreX - font.width(text) / 2, y, argb, false);
	}

	/** The lobby's plank wall, tiled over the area. */
	public static void board(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.BOARD, x, y, width, height);
	}

	/** Firelight glowing up from the bottom centre; {@code alpha} (0–1) lets it flicker. */
	public static void firelightGlow(GuiGraphicsExtractor g, int x, int y, int width, int height, float alpha) {
		int a = Math.clamp((int) (alpha * 255), 0, 255);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.FIRELIGHT, x, y, width, height, a << 24 | 0xFFFFFF);
	}

	public static void ghost(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.GHOST_CARD, x, y, width, height);
	}

	public static void pill(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.PILL, x, y, width, height);
	}

	/** A pill tinted with a player's colour (0xRRGGBB) holding a short cream label, centred. */
	public static void colourPill(GuiGraphicsExtractor g, Font font, Component text, int centreX, int y, int rgb) {
		int w = font.width(text) + 10;
		int x = centreX - w / 2;
		// A tinted sprite, like every other panel: text over plain fills here came out garbled.
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.TAG, x, y, w, 12, 0xFF000000 | rgb);
		label(g, font, text, x + 5, y + 2, CREAM);
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
		return glyph(g, control, style, x, y, 0xFFFFFFFF);
	}

	/** A button glyph tinted by {@code argb} (white at some alpha, to fade it). */
	public static int glyph(GuiGraphicsExtractor g, Control control, PadStyle style, int x, int y, int argb) {
		int width = glyphWidth(control);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.glyph(control, style), x, y, width, GLYPH_HEIGHT, argb);
		return width;
	}
}
