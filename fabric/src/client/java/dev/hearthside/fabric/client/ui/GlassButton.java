package dev.hearthside.fabric.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * An Ember-glass button over the world (Spec A section 4.1): a frosted chip that lights ember-orange when it is the
 * selection. A real vanilla {@link Button}, so other mods, narration and the E2E see it as one.
 */
public final class GlassButton extends Button {
	/** The ember accent of a selected button, and its fainter fill. */
	private static final int LIT = 0xFFFFB35A, LIT_FILL = 0x50FF8A3A;
	private static final int INACTIVE_TEXT = 0xFF8A7A6A;

	public GlassButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	/** Hearthside's knock, not vanilla's click. */
	@Override
	public void playDownSound(SoundManager soundManager) {
		HearthSounds.play(HearthSounds.Ui.KNOCK);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// One selection on a screen: the focused button lights, one merely under the pointer only glows.
		boolean selected = isActive() && HearthDraw.selected(this);
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		HearthDraw.chip(g, x, y, w, h);
		if (selected) {
			g.fill(x + 1, y + 1, x + w - 1, y + h - 1, LIT_FILL);
			g.outline(x, y, w, h, LIT);
		} else if (isActive() && isHovered()) {
			HearthDraw.hoverGlow(g, x, y, w, h);
		}
		var font = Minecraft.getInstance().font;
		HearthDraw.labelCentred(g, font, getMessage(), x + w / 2, y + (h - 8) / 2, isActive() ? HearthDraw.CREAM : INACTIVE_TEXT);
	}
}
