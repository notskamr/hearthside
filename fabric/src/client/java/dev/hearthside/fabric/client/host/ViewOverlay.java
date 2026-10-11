package dev.hearthside.fabric.client.host;

import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.ViewFrame;
import dev.hearthside.fabric.client.compositor.Compositor;
import dev.hearthside.fabric.client.compositor.OverlayContext;
import dev.hearthside.fabric.client.compositor.OverlayLayer;
import dev.hearthside.fabric.client.ui.HearthDraw;
import dev.hearthside.fabric.client.ui.HearthSprites;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * What Hearthside draws over each player's view (Spec A sections 3.1(5), 6): the hearth's board behind a view that has
 * no picture yet, a frosted tint over a picture waiting for one at its new size, and the fade of a view arriving or
 * leaving. Render thread.
 */
final class ViewOverlay implements OverlayLayer {
	/** Divider colour, which a fading view fades from or to. */
	private static final int DIVIDER = 0x120C08;
	/** The frosted glass over a picture shown at the wrong size, about 35% opaque. */
	private static final int FROST = 0x59FFFFFF;

	@Override
	public void extract(GuiGraphicsExtractor g, OverlayContext context) {
		Compositor compositor = Compositor.active();
		if (compositor == null) {
			return;
		}
		for (ViewFrame view : context.views()) {
			Rect r = view.rect();
			if (view.seat() != 0 && !compositor.hasFrame(view.seat())) {
				// Nothing from their game yet: the hearth, never the bare window.
				HearthDraw.board(g, r.x(), r.y(), r.width(), r.height());
				HearthDraw.firelightGlow(g, r.x() + r.width() / 10, r.y() + r.height() * 35 / 100, r.width() * 8 / 10,
						r.height() - r.height() * 35 / 100, 0.85f);
			} else if (compositor.isStale(view.seat())) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.GLASS_PANEL, r.x(), r.y(), r.width(), r.height(), FROST);
			}
			if (view.alpha() < 1f) {
				int alpha = Math.round((1f - view.alpha()) * 255);
				g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), alpha << 24 | DIVIDER);
			}
		}
	}
}
