package dev.hearthside.fabric.client.ui;

import dev.hearthside.core.ui.EmberField;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws an {@link EmberField} over an area, advancing it by real time. Render thread. */
public final class EmberLayer {
	private static final int EMBER = 0xFFB35A, GLOW = 0xFF8A3A;

	private final EmberField field;
	private long lastNanos;

	public EmberLayer(long seed, int max) {
		this.field = new EmberField(seed, max);
	}

	public void draw(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean reduceMotion) {
		long now = System.nanoTime();
		double seconds = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;
		field.step(seconds, reduceMotion);
		for (EmberField.Ember ember : field.embers()) {
			int px = x + (int) (ember.x() * width);
			int py = y + height - (int) (ember.y() * height);
			int size = (int) ember.size();
			int alpha = (int) (ember.alpha() * 255);
			g.fill(px - 1, py - 1, px + size + 1, py + size + 1, (alpha / 4) << 24 | GLOW);
			g.fill(px, py, px + size, py + size, alpha << 24 | EMBER);
		}
	}
}
