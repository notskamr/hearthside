package dev.hearthside.fabric.client.ui;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.ui.KeyboardModel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The on-screen keyboard (Spec A section 5.4) in Ember glass, sized to fit inside the typing player's own card: the
 * text being typed, the key grid with the cursor in the player's colour, and button hints in their controller's family.
 * Several players can type at once, each in their own card.
 */
public final class KeyboardWidget {
	private static final int KEY_H = 10, GAP = 1, PAD = 3, FIELD = 11, COLUMNS = 10;
	private static final int TEXT = 0xFFF3DFB6, KEY = 0x60FFE6C8, KEY_TEXT = 0xFFFFFFFF, SUGGESTED = 0xFFFFB35A;

	private final KeyboardModel model;
	private final int accent;

	/** @param accent the typing player's colour, 0xRRGGBB */
	public KeyboardWidget(KeyboardModel model, int accent) {
		this.model = model;
		this.accent = accent;
	}

	/** The height the keyboard takes at any width. */
	public static int height() {
		return PAD + FIELD + 3 + 4 * (KEY_H + GAP) + 3 + 2 * (HearthDraw.GLYPH_HEIGHT + 1) + PAD;
	}

	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int width, PadStyle style) {
		int height = height();
		// Backed so the keys stay readable over the card.
		g.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xD8201410);
		HearthDraw.glass(g, x, y, width, height);
		// The text being typed: an untouched suggestion shows as selected, ready to keep or replace.
		int fieldY = y + PAD;
		HearthDraw.chip(g, x + PAD, fieldY, width - PAD * 2, FIELD);
		String text = font.plainSubstrByWidth(model.text(), width - PAD * 2 - 10);
		boolean caret = !model.suggested() && (System.currentTimeMillis() / 500) % 2 == 0;
		if (model.suggested()) {
			g.fill(x + PAD + 2, fieldY + 1, x + PAD + 4 + font.width(text), fieldY + FIELD - 1, 0x80FF8A3A);
		}
		g.text(font, text + (caret ? "_" : ""), x + PAD + 3, fieldY + 2, model.suggested() ? SUGGESTED : TEXT, false);

		int keyW = (width - PAD * 2 - (COLUMNS - 1) * GAP) / COLUMNS;
		int gridTop = fieldY + FIELD + 3;
		for (int row = 0; row < model.rowCount(); row++) {
			int length = model.rowLength(row);
			int rowX = x + (width - (length * keyW + (length - 1) * GAP)) / 2;
			int keyY = gridTop + row * (KEY_H + GAP);
			for (int column = 0; column < length; column++) {
				int keyX = rowX + column * (keyW + GAP);
				boolean under = row == model.cursorRow() && column == model.cursorColumn();
				g.fill(keyX, keyY, keyX + keyW, keyY + KEY_H, under ? 0xFF000000 | accent : KEY);
				if (under) {
					g.outline(keyX - 1, keyY - 1, keyW + 2, KEY_H + 2, 0xFFFFFFFF);
				}
				HearthDraw.labelCentred(g, font, Component.literal(String.valueOf(model.keyAt(row, column))),
						keyX + keyW / 2 + 1, keyY + 1, KEY_TEXT);
			}
		}

		int hintY = gridTop + 4 * (KEY_H + GAP) + 3;
		int hintX = x + PAD;
		hintX = hint(g, font, Control.X, style, "Del", hintX, hintY);
		hint(g, font, Control.RIGHT_BUMPER, style, model.mode() == KeyboardModel.Mode.NAME ? "Aa" : "Aa!", hintX, hintY);
		hintX = x + PAD;
		hintY += HearthDraw.GLYPH_HEIGHT + 1;
		if (model.mode() == KeyboardModel.Mode.TEXT) {
			hintX = hint(g, font, Control.Y, style, "Space", hintX, hintY);
		}
		hint(g, font, Control.START, style, "Done", hintX, hintY);
	}

	private static int hint(GuiGraphicsExtractor g, Font font, Control control, PadStyle style, String label, int x, int y) {
		int width = HearthDraw.glyph(g, control, style, x, y);
		g.text(font, Component.literal(label), x + width + 2, y + 2, TEXT, false);
		return x + width + 2 + font.width(label) + 5;
	}
}
