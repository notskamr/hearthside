package dev.hearthside.fabric.client.compositor;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Something Hearthside draws over the composited views, across the whole window (Spec B section 3.4). */
@FunctionalInterface
public interface OverlayLayer {
	/** Extracts this layer's GUI elements for this frame, in GUI units of the full window. Render thread. */
	void extract(GuiGraphicsExtractor g, OverlayContext context);

	/**
	 * Where this layer sits: lower draws first, and each layer covers everything drawn before it (text included).
	 * The views' own overlay is 0; prompts that float over every view sit higher.
	 */
	default int order() {
		return 0;
	}
}
