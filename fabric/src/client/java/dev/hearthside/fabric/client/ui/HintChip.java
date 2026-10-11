package dev.hearthside.fabric.client.ui;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.PadStyle;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A button hint on an Ember-glass chip: the controller's glyph and what it does (Spec A section 4.3). Clicking it with
 * the mouse does the same thing, so screens can use hints as their only buttons, as the lobby draft does.
 */
public final class HintChip extends Button {
	private final Control control;
	private final Supplier<PadStyle> style;

	public HintChip(int x, int y, Control control, Supplier<PadStyle> style, Component label, OnPress onPress) {
		super(x, y, width(control, label), HEIGHT, label, onPress, DEFAULT_NARRATION);
		this.control = control;
		this.style = style;
	}

	public static final int HEIGHT = 15;

	public static int width(Control control, Component label) {
		return 4 + HearthDraw.glyphWidth(control) + 4 + Minecraft.getInstance().font.width(label) + 5;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		HearthDraw.chip(g, getX(), getY(), getWidth(), getHeight());
		if (isActive() && isHoveredOrFocused()) {
			g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0x30FFB35A);
		}
		int glyph = HearthDraw.glyph(g, control, style.get(), getX() + 4, getY() + 2);
		HearthDraw.label(g, Minecraft.getInstance().font, getMessage(), getX() + 4 + glyph + 4, getY() + 4,
				HearthDraw.CREAM);
	}
}
