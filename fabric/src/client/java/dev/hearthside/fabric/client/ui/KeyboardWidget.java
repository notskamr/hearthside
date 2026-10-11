package dev.hearthside.fabric.client.ui;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.ui.KeyboardModel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The on-screen keyboard (Spec A section 5.4) in Ember glass: the text being typed, the key grid with the cursor
 * outlined in the typing player's colour, and button hints in their controller's family.
 */
public final class KeyboardWidget {
	public static final int WIDTH = 210, HEIGHT = 108;
	private static final int KEY_W = 18, KEY_H = 14, GAP = 2, PAD = 6;
	private static final int TEXT = 0xFFF3DFB6, KEY = 0x60FFE6C8, KEY_TEXT = 0xFFFFFFFF, SUGGESTED = 0xFFFFB35A;

	private final KeyboardModel model;
	private final int accent;

	/** @param accent the typing player's colour, 0xRRGGBB */
	public KeyboardWidget(KeyboardModel model, int accent) {
		this.model = model;
		this.accent = accent;
	}

	public void render(GuiGraphicsExtractor g, Font font, int x, int y, PadStyle style) {
		HearthDraw.glass(g, x, y, WIDTH, HEIGHT);
		// The text being typed: an untouched suggestion shows as selected, ready to keep or replace.
		int fieldY = y + PAD;
		HearthDraw.chip(g, x + PAD, fieldY, WIDTH - PAD * 2, 13);
		String text = model.text();
		boolean caret = !model.suggested() && (System.currentTimeMillis() / 500) % 2 == 0;
		if (model.suggested()) {
			g.fill(x + PAD + 3, fieldY + 2, x + PAD + 5 + font.width(text), fieldY + 11, 0x80FF8A3A);
		}
		g.text(font, text + (caret ? "_" : ""), x + PAD + 4, fieldY + 3, model.suggested() ? SUGGESTED : TEXT, false);

		int gridTop = fieldY + 13 + 4;
		for (int row = 0; row < model.rowCount(); row++) {
			int length = model.rowLength(row);
			int rowX = x + (WIDTH - (length * KEY_W + (length - 1) * GAP)) / 2;
			int keyY = gridTop + row * (KEY_H + GAP);
			for (int column = 0; column < length; column++) {
				int keyX = rowX + column * (KEY_W + GAP);
				boolean under = row == model.cursorRow() && column == model.cursorColumn();
				g.fill(keyX, keyY, keyX + KEY_W, keyY + KEY_H, under ? 0xFF000000 | accent : KEY);
				if (under) {
					g.outline(keyX - 1, keyY - 1, KEY_W + 2, KEY_H + 2, 0xFFFFFFFF);
				}
				g.centeredText(font, String.valueOf(model.keyAt(row, column)), keyX + KEY_W / 2, keyY + 3, KEY_TEXT);
			}
		}

		int hintY = y + HEIGHT - PAD - HearthDraw.GLYPH_HEIGHT;
		int hintX = x + PAD;
		hintX = hint(g, font, Control.X, style, "Delete", hintX, hintY);
		if (model.mode() == KeyboardModel.Mode.TEXT) {
			hintX = hint(g, font, Control.Y, style, "Space", hintX, hintY);
		}
		hintX = hint(g, font, Control.RIGHT_BUMPER, style, model.mode() == KeyboardModel.Mode.NAME ? "Aa" : "Aa?!", hintX,
				hintY);
		hint(g, font, Control.START, style, "Done", hintX, hintY);
	}

	private static int hint(GuiGraphicsExtractor g, Font font, Control control, PadStyle style, String label, int x, int y) {
		int width = HearthDraw.glyph(g, control, style, x, y);
		g.text(font, Component.literal(label), x + width + 2, y + 2, TEXT, false);
		return x + width + 2 + font.width(label) + 8;
	}
}
