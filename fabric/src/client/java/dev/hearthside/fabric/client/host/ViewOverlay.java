package dev.hearthside.fabric.client.host;

import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.ViewFrame;
import dev.hearthside.fabric.client.compositor.Compositor;
import dev.hearthside.fabric.client.compositor.OverlayContext;
import dev.hearthside.fabric.client.compositor.OverlayLayer;
import dev.hearthside.fabric.client.ui.CardPainter;
import dev.hearthside.fabric.client.ui.HearthSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * What Hearthside draws over each player's view (Spec A sections 3.1(5), 6): a joining player's card until their game
 * is in the world (then it crossfades into it), a frosted tint over a picture waiting for one at its new size, and the
 * fade of a view arriving or leaving. A guest's view never shows the bare window or their game's loading screens.
 * Render thread.
 */
final class ViewOverlay implements OverlayLayer {
	/** Divider colour, which a fading view fades from or to. */
	private static final int DIVIDER = 0x120C08;
	/** The frosted glass over a picture shown at the wrong size, about 35% opaque. */
	private static final int FROST = 0x59FFFFFF;

	private final Party party;

	ViewOverlay(Party party) {
		this.party = party;
	}

	@Override
	public void extract(GuiGraphicsExtractor g, OverlayContext context) {
		Compositor compositor = Compositor.active();
		if (compositor == null) {
			return;
		}
		var font = Minecraft.getInstance().font;
		for (ViewFrame view : context.views()) {
			Rect r = view.rect();
			int seat = view.seat();
			if (seat != 0) {
				// Nothing from their game yet: their card, never the bare window.
				float cover = compositor.hasFrame(seat) ? party.cardCover(seat, context.nanos(), context.reduceMotion()) : 1f;
				if (cover < 1f && compositor.isStale(seat)) {
					g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.GLASS_PANEL, r.x(), r.y(), r.width(), r.height(), FROST);
				}
				if (cover > 0f) {
					CardPainter.joining(g, font, r, party.who(seat), party.viewPhase(seat),
							party.joiningStuck(seat, context.nanos()), cover, context.nanos(), context.reduceMotion());
				}
			}
			if (view.alpha() < 1f) {
				int alpha = Math.round((1f - view.alpha()) * 255);
				g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), alpha << 24 | DIVIDER);
			}
		}
	}
}
