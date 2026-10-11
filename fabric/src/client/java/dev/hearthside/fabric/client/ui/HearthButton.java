package dev.hearthside.fabric.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/** A wooden Cozy-hearth button (Spec A section 4.1); glows ember-orange when hovered or focused. */
public final class HearthButton extends Button {
	public HearthButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		var sprite = !isActive() ? HearthSprites.BUTTON_DISABLED
				: isHoveredOrFocused() ? HearthSprites.BUTTON_HIGHLIGHTED : HearthSprites.BUTTON;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX(), getY(), getWidth(), getHeight());
		int colour = isActive() ? HearthDraw.CREAM : 0xFF8A7A6A;
		g.centeredText(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2,
				getY() + (getHeight() - 8) / 2, colour);
	}
}
