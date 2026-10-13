package dev.hearthside.fabric.client.host;

import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.hearthside.core.layout.LayoutSettings;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.ViewFrame;
import dev.hearthside.fabric.client.compositor.Compositor;
import dev.hearthside.fabric.client.compositor.OverlayContext;
import dev.hearthside.fabric.client.compositor.OverlayLayer;
import dev.hearthside.fabric.client.ui.CardPainter;
import dev.hearthside.fabric.client.ui.HearthSprites;
import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.profile.GuestProfile;
import dev.hearthside.core.profile.ProfileCarousel;
import dev.hearthside.core.profile.QuickSetup;
import dev.hearthside.fabric.client.ui.HearthDraw;
import dev.hearthside.fabric.client.ui.KeyboardWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * What Hearthside draws over each player's view (Spec A sections 3.1(5), 6): a joining player's card until their game
 * is in the world (then it crossfades into it), a frosted tint over a picture waiting for one at its new size, and the
 * fade of a view arriving or leaving. A guest's view never shows the bare window or their game's loading screens.
 * Render thread.
 */
final class ViewOverlay implements OverlayLayer {
	/**
	 * Fades what the overlay already holds under it by the fill's alpha (colour and alpha alike, so it stays
	 * premultiplied): a card fades as one picture, letting the canvas under it show through.
	 */
	private static final RenderPipeline FADE = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("hearthside", "pipeline/overlay_fade"))
			.withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ZERO, BlendFactor.SRC_ALPHA)))
			.build();
	/** The frosted glass over a picture shown at the wrong size, about 35% opaque. */
	private static final int FROST = 0x59FFFFFF;
	private static final int ERROR_INK = 0xFFA8321E;
	/** Name tags show this long after a player arrives or the layout changes, then fade over {@link #TAG_FADE}. */
	private static final long TAG_SHOW = 4_000_000_000L, TAG_FADE = 400_000_000L;

	private final Party party;

	ViewOverlay(Party party) {
		this.party = party;
	}

	/** A newcomer's card in their own view: the profile carousel, or quick setup with the keyboard. */
	private void drawPicker(GuiGraphicsExtractor g, Font font, Rect view, int seat, SeatCard card) {
		CardPainter.board(g, view, 1f);
		Rect c = CardPainter.cardRect(view);
		CardPainter.cardBack(g, c, party.pickerColour(seat), 1f);
		PadStyle style = party.padStyleOf(seat);
		int centre = c.x() + c.width() / 2;
		int bottom = c.y() + c.height();
		int hintX = c.x() + 7;
		QuickSetup setup = card.setup();
		if (setup == null) {
			String name = card.carousel().current() instanceof ProfileCarousel.Entry.Profile(GuestProfile p) ? p.name() : "+ New";
			ink(g, font, "‹ " + name + " ›", centre, c.y() + 7, c.width() - 6, HearthDraw.INK);
			skin(g, seat, c);
			if (card.problem() != null) {
				ink(g, font, card.problem(), centre, bottom - 44, c.width() - 6, ERROR_INK);
			}
			CardPainter.hint(g, font, Control.A, style, "Pick", hintX, bottom - 30);
			CardPainter.hint(g, font, Control.B, style, "Leave", hintX, bottom - 17);
			return;
		}
		switch (setup.step()) {
			case NAME -> {
				ink(g, font, "Name", centre, c.y() + 7, c.width() - 6, HearthDraw.INK);
				int y = c.y() + 18;
				new KeyboardWidget(card.keyboard(), setup.color()).render(g, font, c.x() + 3, y, c.width() - 6, style);
				if (card.nameProblem() != null) {
					ink(g, font, card.nameProblem(), centre, y + KeyboardWidget.height(card.keyboard(), c.width() - 6) + 3, c.width() - 6, ERROR_INK);
				}
			}
			case SKIN -> {
				ink(g, font, "‹ Skin ›", centre, c.y() + 7, c.width() - 6, HearthDraw.INK);
				skin(g, seat, c);
				CardPainter.hint(g, font, Control.A, style, "Next", hintX, bottom - 30);
				CardPainter.hint(g, font, Control.B, style, "Back", hintX, bottom - 17);
			}
			case COLOR -> {
				ink(g, font, "‹ Colour ›", centre, c.y() + 7, c.width() - 6, HearthDraw.INK);
				skin(g, seat, c);
				HearthDraw.colourPill(g, font, Component.literal("  "), centre, bottom - 46, setup.color());
				CardPainter.hint(g, font, Control.A, style, "Next", hintX, bottom - 30);
				CardPainter.hint(g, font, Control.B, style, "Back", hintX, bottom - 17);
			}
			case DONE -> { }
		}
	}

	private void skin(GuiGraphicsExtractor g, int seat, Rect c) {
		int height = Math.max(24, c.height() - 66);
		CardPainter.skin(g, party.pickerSkin(seat), c.x() + c.width() / 2 - height / 4, c.y() + 19, height / 2, height, 30f);
	}

	/**
	 * A border in the player's colour (thin, 2 px for "colour", or none: plan R2), and for a while their name in an
	 * Ember-glass tag unless name tags are off (Spec A 4.1, 6; Spec E 3.5). Both follow the settings from the next frame.
	 */
	private void borderAndTag(GuiGraphicsExtractor g, Font font, ViewFrame view, OverlayContext context) {
		Rect r = view.rect();
		int seat = view.seat();
		boolean live = seat == 0 || party.viewPhase(seat) == dev.hearthside.core.session.ViewPhase.LIVE;
		int colour = party.colorOf(seat) & 0xFFFFFF;
		LayoutSettings settings = party.layout();
		int edgeWidth = switch (settings.borders()) {
			case COLOUR -> 2;
			case THIN -> 1;
			case OFF -> 0;
		};
		if (edgeWidth > 0) {
			int edge = Math.round(0xD9 * view.alpha()) << 24 | colour;
			g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + edgeWidth, edge);
			g.fill(r.x(), r.y() + r.height() - edgeWidth, r.x() + r.width(), r.y() + r.height(), edge);
			g.fill(r.x(), r.y() + edgeWidth, r.x() + edgeWidth, r.y() + r.height() - edgeWidth, edge);
			g.fill(r.x() + r.width() - edgeWidth, r.y() + edgeWidth, r.x() + r.width(), r.y() + r.height() - edgeWidth, edge);
		}
		if (!live || !settings.nameTags()) {
			return; // their card already says who they are, or player 1 turned name tags off
		}
		long since = Math.max(party.layoutChangedAt(), seat == 0 ? Long.MIN_VALUE : party.liveSince(seat));
		long age = context.nanos() - since;
		float shown = age < TAG_SHOW ? 1f : context.reduceMotion() ? 0f : 1f - (age - TAG_SHOW) / (float) TAG_FADE;
		shown *= view.alpha();
		if (shown <= 0.05f) {
			return;
		}
		String name = font.plainSubstrByWidth(party.nameOf(seat), r.width() - 30);
		int a = Math.round(shown * 255);
		int w = 6 + 5 + 4 + font.width(name) + 6;
		int x = r.x() + 6, y = r.y() + 6;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.GLASS_CHIP, x, y, w, 15, a << 24 | 0xFFFFFF);
		g.fill(x + 6, y + 5, x + 11, y + 10, a << 24 | colour);
		g.text(font, Component.literal(name), x + 15, y + 4, a << 24 | (HearthDraw.CREAM & 0xFFFFFF), true);
	}

	private static void ink(GuiGraphicsExtractor g, Font font, String text, int centre, int y, int maxWidth, int argb) {
		String fitted = font.plainSubstrByWidth(text, maxWidth);
		g.text(font, Component.literal(fitted), centre - font.width(fitted) / 2, y, argb, false);
	}

	@Override
	public void extract(GuiGraphicsExtractor g, OverlayContext context) {
		Compositor compositor = Compositor.active();
		if (compositor == null) {
			return;
		}
		var font = Minecraft.getInstance().font;
		// The free quarter with three players: the party panel and its live map, fading in and out with the layout.
		party.panelAt(context.nanos(), context.reduceMotion()).ifPresent(panel -> {
			Rect inGui = dev.hearthside.fabric.client.compositor.OverlayPass.inGui(panel.rect(), context.guiScale());
			dev.hearthside.fabric.client.ui.PartyPanel.draw(g, font, inGui, party.panelState(), panel.alpha());
			if (Compositor.e2eGapSampling && panel.alpha() < 1f) {
				dev.hearthside.fabric.HearthsideMod.LOGGER.info("Hearthside panel alpha={}",
						String.format(java.util.Locale.ROOT, "%.2f", panel.alpha()));
			}
		});
		for (ViewFrame view : context.views()) {
			Rect r = view.rect();
			int seat = view.seat();
			if (seat != 0) {
				// Nothing from their game yet: their card, never the bare window.
				float cover = compositor.hasFrame(seat) ? party.cardCover(seat, context.nanos(), context.reduceMotion()) : 1f;
				if (cover < 1f && compositor.isStale(seat)) {
					g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.GLASS_PANEL, r.x(), r.y(), r.width(), r.height(), FROST);
				}
				SeatCard card = party.drawnCard(seat);
				if (card != null) {
					// Dropping in: they pick their profile right here, while everyone else plays on.
					drawPicker(g, font, r, seat, card);
				} else if (cover > 0f) {
					CardPainter.joining(g, font, r, party.who(seat), party.cardStatus(seat, context.nanos()), cover,
							context.nanos(), context.reduceMotion());
				}
				// The compositor fades a view's picture itself; with no picture yet, fade the card drawn there instead,
				// over whatever is under it (a view still gliding out of this place, or the divider).
				if (view.alpha() < 1f && !compositor.hasFrame(seat)) {
					// Never 0: the GUI shader discards a fully transparent fill, which would leave the card unfaded.
					int alpha = Math.max(1, Math.round(view.alpha() * 255));
					g.fill(FADE, r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), alpha << 24);
				}
			}
			if (context.views().size() > 1) {
				borderAndTag(g, font, view, context);
			}
		}
	}
}
