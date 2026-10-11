package dev.hearthside.fabric.client.compositor;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Something Hearthside draws over the composited views, across the whole window (Spec B section 3.4). */
@FunctionalInterface
public interface OverlayLayer {
	/** Extracts this layer's GUI elements for this frame, in GUI units of the full window. Render thread. */
	void extract(GuiGraphicsExtractor g, OverlayContext context);
}
