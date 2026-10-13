package dev.hearthside.fabric.client.ui;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.layout.Rect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * A player's card in the Cozy-hearth style, drawn inside their own view (Spec A sections 3.1(5), 3.2): the plank wall
 * and firelight behind, a parchment card framed in their colour with their skin turning slowly and their name, and
 * what is happening ("Joining…", "Reconnecting…"). Everything is drawn at {@code cover} opacity, so the card can
 * crossfade into the world. Render thread.
 */
public final class CardPainter {
	private static final int MUTED = 0xFF7A6A58;
	private static final int EMBER = 0xFFFF9A4A, ERROR_INK = 0xFFA8321E;
	private static Model.Simple wide, slim;

	private CardPainter() {}

	/** What the card says about the player. */
	public record Who(String name, PlayerSkin skin, int colour, PadStyle style) {}

	/** The plank wall and firelight over an area, at {@code alpha}. */
	public static void board(GuiGraphicsExtractor g, Rect r, float alpha) {
		int a = alpha(alpha);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.BOARD, r.x(), r.y(), r.width(), r.height(), a << 24 | 0xFFFFFF);
		HearthDraw.firelightGlow(g, r.x() + r.width() / 10, r.y() + r.height() * 35 / 100, r.width() * 8 / 10,
				r.height() - r.height() * 35 / 100, 0.85f * alpha);
	}

	/**
	 * What a joining player's card says: its line (and a shorter one for a small card); whether it sits over their last
	 * picture, dimmed (their game restarted or gave up), rather than the plank wall; whether the line is bad news; and
	 * whether their controller's B cancels (joining has taken too long).
	 */
	public record Status(String text, String shorter, boolean overLastFrame, boolean failed, boolean offerCancel) {
		public static final Status JOINING = new Status("Joining…", "Joining…", false, false, false),
				STILL_JOINING = new Status("Still joining…", "Joining…", false, false, true),
				RECONNECTING = new Status("Reconnecting…", "Reconnecting…", true, false, false),
				STILL_RECONNECTING = new Status("Still reconnecting…", "Reconnecting…", true, false, true),
				COULDNT_START = new Status("Couldn't start", "Can't start", true, true, false),
				// Never in the world: on the plank wall, as joining was (nothing of theirs to show but loading screens).
				COULDNT_START_JOINING = new Status("Couldn't start", "Can't start", false, true, false);
	}

	/** A joining (or reconnecting) player's card filling their view. */
	public static void joining(GuiGraphicsExtractor g, Font font, Rect view, Who who, Status status, float cover, long nanos,
			boolean reduceMotion) {
		if (cover <= 0.02f) {
			return;
		}
		if (status.overLastFrame()) {
			// Their last picture stays, dimmed, behind the card.
			g.fill(view.x(), view.y(), view.x() + view.width(), view.y() + view.height(), alpha(0.55f * cover) << 24);
		} else {
			board(g, view, cover);
		}
		Rect card = cardRect(view);
		int cw = card.width(), ch = card.height(), cx = card.x(), cy = card.y();
		int a = alpha(cover);
		cardBack(g, card, who.colour(), cover);
		int centre = cx + cw / 2;
		String name = font.plainSubstrByWidth(who.name(), cw - 8);
		g.text(font, Component.literal(name), centre - font.width(name) / 2, cy + 7, a << 24 | (HearthDraw.INK & 0xFFFFFF), false);
		// The skin, turning slowly (still with reduce motion); dropped as soon as the card starts fading into the world.
		int skinTop = cy + 20;
		int skinHeight = Math.max(24, ch - 58);
		if (cover > 0.98f) {
			float sway = reduceMotion || status.failed() ? 0 : (float) Math.sin(nanos / 1e9 * 0.9) * 25f;
			skin(g, who.skin(), centre - skinHeight / 4, skinTop, skinHeight / 2, skinHeight, 30f + sway);
		}
		String text = font.width(status.text()) <= cw - 6 ? status.text() : font.plainSubstrByWidth(status.shorter(), cw - 6);
		int statusY = cy + ch - 30;
		int ink = status.failed() ? ERROR_INK : HearthDraw.INK;
		g.text(font, Component.literal(text), centre - font.width(text) / 2, statusY, a << 24 | (ink & 0xFFFFFF), false);
		if (status.offerCancel()) {
			// Their controller's B leaves the seat.
			Component cancel = Component.literal("Cancel");
			int gw = HearthDraw.glyphWidth(Control.B);
			int x = centre - (gw + 3 + font.width(cancel)) / 2;
			HearthDraw.glyph(g, Control.B, who.style(), x, statusY + 11);
			g.text(font, cancel, x + gw + 3, statusY + 13, a << 24 | (MUTED & 0xFFFFFF), false);
		} else if (!status.failed()) {
			embers(g, centre, statusY + 13, a, nanos, reduceMotion);
		}
	}

	/**
	 * What a dropped controller's card says (Spec E section 3.3): who the player is, and whether another controller may
	 * take them over now, its A drawn in {@code takeoverStyle}.
	 */
	public record Reconnect(Who who, boolean offerTakeover, PadStyle takeoverStyle) {}

	/** Over a dropped controller's view: their picture (their pause menu) dims to 45%. */
	private static final int VEIL = 0x8C000000;
	private static final String RECONNECT = "Reconnect your controller";
	/** The take-over line, word by word; the empty word is the A button's glyph. */
	private static final List<String> TAKE_OVER = List.of("Or", "press", "", "on", "another", "controller", "to", "take",
			"over");

	/**
	 * A dropped controller's card over the player's view: a dark veil over their game, and a card in their colour asking
	 * for the controller back (their name, a controller in their colour, "Reconnect your controller"), and once a
	 * minute has passed, how another controller takes them over.
	 */
	public static void reconnect(GuiGraphicsExtractor g, Font font, Rect view, Reconnect card) {
		g.fill(view.x(), view.y(), view.x() + view.width(), view.y() + view.height(), VEIL);
		int cw = Math.min(Math.clamp(Math.round(view.width() * 0.6f), 96, 160), view.width() - 12);
		int inner = cw - 12;
		List<FormattedCharSequence> ask = font.split(Component.literal(RECONNECT), inner);
		List<List<String>> offer = card.offerTakeover() ? wrap(font, TAKE_OVER, inner) : List.of();
		// Kept inside a short view (a small window, four players): first without the icon, then with the take-over line's
		// first row only, cut short after it (the A and what it presses on stay).
		int room = view.height() - 8;
		boolean icon = height(ask.size(), offer.size(), true) <= room;
		if (!icon && offer.size() > 1 && height(ask.size(), offer.size(), false) > room) {
			offer = List.of(firstRowCut(font, offer.getFirst(), inner));
		}
		int ch = height(ask.size(), offer.size(), icon);
		Rect c = new Rect(view.x() + (view.width() - cw) / 2, view.y() + Math.max(0, (view.height() - ch) / 2), cw, ch);
		int colour = card.who().colour() & 0xFFFFFF;
		cardBack(g, c, colour, 1f);
		int centre = c.x() + cw / 2;
		int y = c.y() + 8;
		String name = font.plainSubstrByWidth(card.who().name(), inner - 10);
		HearthDraw.colourPill(g, font, Component.literal(name), centre, y, colour);
		y += 12 + 6;
		if (icon) {
			// Their controller, lit in their colour as the real one is.
			g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.CONTROLLER, centre - ICON_W / 2, y, ICON_W, ICON_H,
					0xFF000000 | colour);
			y += ICON_H + 6;
		}
		for (FormattedCharSequence line : ask) {
			g.text(font, line, centre - font.width(line) / 2, y, HearthDraw.INK, false);
			y += 10;
		}
		y += 4;
		for (List<String> line : offer) {
			int x = centre - lineWidth(font, line) / 2;
			for (String word : line) {
				if (word.isEmpty()) {
					x += HearthDraw.glyph(g, Control.A, card.takeoverStyle(), x, y);
				} else {
					g.text(font, Component.literal(word), x, y + 2, MUTED, false);
					x += font.width(word);
				}
				x += font.width(" ");
			}
			y += 12;
		}
	}

	/** The controller icon on a reconnect card: the 13×9 sprite at twice its size. */
	private static final int ICON_W = 26, ICON_H = 18;

	/** A reconnect card's height: its pill, the icon if shown, the ask's lines and the take-over's rows. */
	private static int height(int askLines, int offerRows, boolean icon) {
		return 8 + 12 + 6 + (icon ? ICON_H + 6 : 0) + askLines * 10 + (offerRows == 0 ? 0 : 4 + offerRows * 12) + 6;
	}

	/** A take-over row that has to stand alone, ending in "…" (words dropped from its end until it fits). */
	private static List<String> firstRowCut(Font font, List<String> row, int width) {
		List<String> cut = new ArrayList<>(row);
		while (true) {
			List<String> shown = new ArrayList<>(cut);
			if (shown.getLast().isEmpty()) {
				shown.add("…");
			} else {
				shown.set(shown.size() - 1, shown.getLast() + "…");
			}
			if (cut.size() == 1 || lineWidth(font, shown) <= width) {
				return shown;
			}
			cut.removeLast();
		}
	}

	/** Words (the empty one a button glyph) in lines no wider than {@code width}. */
	private static List<List<String>> wrap(Font font, List<String> words, int width) {
		List<List<String>> lines = new ArrayList<>();
		List<String> line = new ArrayList<>();
		for (String word : words) {
			line.add(word);
			if (line.size() > 1 && lineWidth(font, line) > width) {
				line.removeLast();
				lines.add(line);
				line = new ArrayList<>(List.of(word));
			}
		}
		lines.add(line);
		return lines;
	}

	private static int lineWidth(Font font, List<String> line) {
		int width = font.width(" ") * (line.size() - 1);
		for (String word : line) {
			width += word.isEmpty() ? HearthDraw.glyphWidth(Control.A) : font.width(word);
		}
		return width;
	}

	/** Where a player's card sits in their view: a portrait card, centred, scaled to the view. */
	public static Rect cardRect(Rect view) {
		int cw = Math.clamp(Math.round(view.width() * 0.42f), 64, 120);
		int ch = Math.min(Math.round(cw * 1.45f), view.height() - 16);
		return new Rect(view.x() + (view.width() - cw) / 2, view.y() + (view.height() - ch) / 2, cw, ch);
	}

	/** The card itself: parchment in a frame of the player's colour (0xRRGGBB), at {@code alpha}. */
	public static void cardBack(GuiGraphicsExtractor g, Rect card, int colour, float alpha) {
		int a = alpha(alpha);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.CARD_FRAME, card.x() - 3, card.y() - 3, card.width() + 6,
				card.height() + 6, a << 24 | (colour & 0xFFFFFF));
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.PARCHMENT_CARD, card.x(), card.y(), card.width(), card.height(),
				a << 24 | 0xFFFFFF);
	}

	/** One of a card's button hints: the glyph in that player's controller family, then what it does, in ink. */
	public static void hint(GuiGraphicsExtractor g, Font font, Control control, PadStyle style, String label, int x, int y) {
		int gw = HearthDraw.glyph(g, control, style, x, y);
		g.text(font, Component.literal(label), x + gw + 3, y + 2, HearthDraw.INK, false);
	}

	/** Three embers pulsing in turn (steady with reduce motion). */
	private static void embers(GuiGraphicsExtractor g, int centre, int y, int a, long nanos, boolean reduceMotion) {
		for (int i = 0; i < 3; i++) {
			double phase = reduceMotion ? 0.6 : (Math.sin(nanos / 1e9 * 5.0 - i * 0.9) + 1) / 2;
			int lift = reduceMotion ? 0 : (int) Math.round(phase);
			int ea = Math.round(a * (float) (0.3 + 0.7 * phase));
			int x = centre - 8 + i * 6;
			g.fill(x, y - lift, x + 3, y + 3 - lift, ea << 24 | (EMBER & 0xFFFFFF));
		}
	}

	/** A player's skin, standing, turned {@code rotationY} degrees. */
	public static void skin(GuiGraphicsExtractor g, PlayerSkin skin, int x, int y, int width, int height, float rotationY) {
		if (wide == null) {
			var models = Minecraft.getInstance().getEntityModels();
			wide = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
			slim = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);
		}
		Model.Simple model = skin.model() == PlayerModelType.SLIM ? slim : wide;
		float scale = 0.97F * height / 2.125F;
		g.skin(model, skin.body().texturePath(), scale, -5f, rotationY, -1.0625F, x, y, x + width, y + height);
	}

	private static int alpha(float alpha) {
		return Math.clamp(Math.round(alpha * 255), 0, 255);
	}
}
