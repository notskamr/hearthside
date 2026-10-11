package dev.hearthside.fabric.client.compositor;

import dev.hearthside.core.layout.ViewFrame;
import java.util.List;

/**
 * What an overlay layer draws over: the full window in GUI units (at player 1's GUI scale), and each view as it is
 * drawn this frame, in the same units.
 */
public record OverlayContext(int guiWidth, int guiHeight, int guiScale, List<ViewFrame> views, long nanos,
		boolean reduceMotion) {}
