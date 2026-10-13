package dev.hearthside.fabric.client.ui;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.PadStyle;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * A wooden Cozy-hearth button (Spec A section 4.1); glows ember-orange when hovered or focused. It can show the
 * controller button that also presses it, as a glyph before its label.
 */
public final class HearthButton extends Button {
	private Control glyph;
	private Supplier<PadStyle> style;

	public HearthButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	private HearthSounds.Ui sound = HearthSounds.Ui.KNOCK;

	/** Hearthside's knock, not vanilla's click (or the sound {@link #withSound} gave). */
	@Override
	public void playDownSound(SoundManager soundManager) {
		HearthSounds.play(sound);
	}

	/** Sounds like this when pressed: a row that cycles a value turns a page, as left and right on it do. */
	public HearthButton withSound(HearthSounds.Ui sound) {
		this.sound = sound;
		return this;
	}

	/** Shows this control's glyph (in the family {@code style} gives) before the label. */
	public HearthButton withGlyph(Control control, Supplier<PadStyle> style) {
		this.glyph = control;
		this.style = style;
		return this;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// One selection on a screen: the focused button lights, one merely under the pointer only glows.
		boolean selected = HearthDraw.selected(this);
		var sprite = !isActive() ? HearthSprites.BUTTON_DISABLED
				: selected ? HearthSprites.BUTTON_HIGHLIGHTED : HearthSprites.BUTTON;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX(), getY(), getWidth(), getHeight());
		if (isActive() && !selected && isHovered()) {
			HearthDraw.hoverGlow(g, getX(), getY(), getWidth(), getHeight());
		}
		var font = Minecraft.getInstance().font;
		int colour = isActive() ? HearthDraw.CREAM : 0xFF8A7A6A;
		int textY = getY() + (getHeight() - 8) / 2;
		if (glyph == null) {
			HearthDraw.labelCentred(g, font, getMessage(), getX() + getWidth() / 2, textY, colour);
			return;
		}
		int glyphWidth = HearthDraw.glyphWidth(glyph);
		int total = glyphWidth + 4 + font.width(getMessage());
		int x = getX() + (getWidth() - total) / 2;
		HearthDraw.glyph(g, glyph, style.get(), x, getY() + (getHeight() - HearthDraw.GLYPH_HEIGHT) / 2);
		HearthDraw.label(g, font, getMessage(), x + glyphWidth + 4, textY, colour);
	}
}
