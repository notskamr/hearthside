package dev.hearthside.fabric.client.ui;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.ui.KeyboardModel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The on-screen keyboard (Spec A section 5.4) in Ember glass, sized to fit inside the typing player's own card: the
 * text being typed, the key grid with the cursor in the player's colour, and a legend of every button it uses in their
 * controller's family. Several players can type at once, each in their own card.
 */
public final class KeyboardWidget {
	private static final int KEY_H = 10, GAP = 1, PAD = 3, FIELD = 11, COLUMNS = 10;
	private static final int LEGEND_ROW = HearthDraw.GLYPH_HEIGHT + 1;
	private static final int TEXT = 0xFFF3DFB6, KEY = 0x60FFE6C8, KEY_TEXT = 0xFFFFFFFF, SUGGESTED = 0xFFFFB35A;
	/**
	 * The legend's styles, roomiest first: glyph-to-label gap, gap between a row's two entries, and whether to use the
	 * short labels (the narrowest cards, a drop-in's in half a view).
	 */
	private static final int[][] STYLES = {{2, 4, 0}, {1, 2, 0}, {1, 2, 1}};

	private final KeyboardModel model;
	private final int accent;

	/** @param accent the typing player's colour, 0xRRGGBB */
	public KeyboardWidget(KeyboardModel model, int accent) {
		this.model = model;
		this.accent = accent;
	}

	/** One entry of the legend: its buttons (the bumpers share one), what they do, and that said shorter. */
	public record Hint(List<Control> controls, String label, String shortLabel) {
		Hint(Control control, String label) {
			this(List.of(control), label, label);
		}
	}

	/**
	 * Every button the keyboard uses, two to a row: typing and closing, then editing (Y types a space; in a name, which
	 * can't have spaces, a "_", and the legend says so), then the layers and keeping the text.
	 */
	public static List<Hint> legend(KeyboardModel model) {
		boolean name = model.mode() == KeyboardModel.Mode.NAME;
		String layers = name ? "Aa" : "Aa!";
		return List.of(
				new Hint(Control.A, "Type"), new Hint(Control.B, "Close"),
				new Hint(Control.Y, name ? "_" : "Space"), new Hint(Control.X, "Del"),
				new Hint(List.of(Control.LEFT_BUMPER, Control.RIGHT_BUMPER), layers, layers),
				new Hint(List.of(Control.START), "Done", "OK"));
	}

	/** The legend's rows in the roomiest style that fits them two to a row; split rows where even the last can't. */
	private record Layout(List<List<Hint>> rows, int glyphGap, boolean shortLabels) {}

	private static Layout layout(Font font, KeyboardModel model, int width) {
		List<Hint> legend = legend(model);
		int room = width - PAD * 2;
		for (int[] style : STYLES) {
			boolean fits = true;
			for (int i = 0; i < legend.size(); i += 2) {
				fits &= rowWidth(font, legend.get(i), legend.get(i + 1), style) <= room;
			}
			if (fits) {
				List<List<Hint>> rows = new ArrayList<>();
				for (int i = 0; i < legend.size(); i += 2) {
					rows.add(legend.subList(i, i + 2));
				}
				return new Layout(rows, style[0], style[2] == 1);
			}
		}
		int[] tightest = STYLES[STYLES.length - 1];
		List<List<Hint>> rows = new ArrayList<>();
		for (int i = 0; i < legend.size(); i += 2) {
			Hint left = legend.get(i), right = legend.get(i + 1);
			if (rowWidth(font, left, right, tightest) <= room) {
				rows.add(List.of(left, right));
			} else {
				rows.add(List.of(left));
				rows.add(List.of(right));
			}
		}
		return new Layout(rows, tightest[0], true);
	}

	private static int rowWidth(Font font, Hint left, Hint right, int[] style) {
		return entryWidth(font, left, style[0], style[2] == 1) + style[1] + entryWidth(font, right, style[0], style[2] == 1);
	}

	private static int entryWidth(Font font, Hint hint, int glyphGap, boolean shortLabel) {
		int width = 0;
		for (Control control : hint.controls()) {
			width += HearthDraw.glyphWidth(control) + 1;
		}
		return width - 1 + glyphGap + font.width(shortLabel ? hint.shortLabel() : hint.label());
	}

	/** The height the keyboard takes at this width (its legend wraps when narrow). */
	public static int height(KeyboardModel model, int width) {
		int rows = layout(Minecraft.getInstance().font, model, width).rows().size();
		return PAD + FIELD + 3 + 4 * (KEY_H + GAP) + 3 + rows * LEGEND_ROW + PAD;
	}

	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int width, PadStyle style) {
		int height = height(model, width);
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

		// The legend: a row's first entry at the left, its second at the right.
		Layout layout = layout(font, model, width);
		List<String> drawn = new ArrayList<>();
		int hintY = gridTop + 4 * (KEY_H + GAP) + 3;
		for (List<Hint> row : layout.rows()) {
			hint(g, font, row.getFirst(), style, x + PAD, hintY, layout, drawn);
			if (row.size() > 1) {
				Hint right = row.get(1);
				int rightX = x + width - PAD - entryWidth(font, right, layout.glyphGap(), layout.shortLabels());
				hint(g, font, right, style, rightX, hintY, layout, drawn);
			}
			hintY += LEGEND_ROW;
		}
		drawnLegend = List.copyOf(drawn);
	}

	/** The legend the last keyboard drew: each entry's glyph sprites (joined by +), a space, its label. For the E2E. */
	public static volatile List<String> drawnLegend = List.of();

	private static void hint(GuiGraphicsExtractor g, Font font, Hint hint, PadStyle style, int x, int y, Layout layout,
			List<String> drawn) {
		List<String> sprites = new ArrayList<>();
		for (Control control : hint.controls()) {
			sprites.add(HearthSprites.glyph(control, style).toString());
			x += HearthDraw.glyph(g, control, style, x, y) + 1;
		}
		String label = layout.shortLabels() ? hint.shortLabel() : hint.label();
		g.text(font, Component.literal(label), x - 1 + layout.glyphGap(), y + 2, TEXT, false);
		drawn.add(String.join("+", sprites) + " " + label);
	}
}
