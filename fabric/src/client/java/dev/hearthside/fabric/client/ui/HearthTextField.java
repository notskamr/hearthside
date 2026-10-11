package dev.hearthside.fabric.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** A text field on parchment with ink text, in place of vanilla's black box (the sprite swap is in a mixin). */
public class HearthTextField extends EditBox {
	/** Set when the parchment is drawn (the E2E checks it). */
	public static volatile boolean drewParchment;

	public HearthTextField(Font font, int x, int y, int width, int height, Component narration) {
		super(font, x, y, width, height, narration);
		setTextColor(HearthDraw.INK);
		setTextShadow(false);
	}
}
