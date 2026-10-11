package dev.hearthside.fabric.client.ui;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.layout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
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
				COULDNT_START = new Status("Couldn't start", "Can't start", true, true, false);
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
