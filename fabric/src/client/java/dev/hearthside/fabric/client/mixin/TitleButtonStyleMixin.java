package dev.hearthside.fabric.client.mixin;

import dev.hearthside.core.Role;
import dev.hearthside.fabric.client.HearthsideClient;
import dev.hearthside.fabric.client.ui.HearthSprites;
import dev.hearthside.fabric.client.ui.TitleStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The title screen's buttons are wooden (Spec A section 4.1), unless player 1 chose Minecraft's own title screen (Spec E
 * section 3.5); every other screen's buttons stay vanilla. Buttons other mods add to the title look the same.
 */
@Mixin(AbstractButton.class)
public abstract class TitleButtonStyleMixin {
	@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void hearthside$wood(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (HearthsideClient.role() != Role.HOST || !(Minecraft.getInstance().gui.screen() instanceof TitleScreen)
				|| !TitleStyle.applies()) {
			return;
		}
		AbstractButton self = (AbstractButton) (Object) this;
		var sprite = !self.isActive() ? HearthSprites.BUTTON_DISABLED
				: self.isHoveredOrFocused() ? HearthSprites.BUTTON_HIGHLIGHTED : HearthSprites.BUTTON;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, self.getX(), self.getY(), self.getWidth(), self.getHeight());
		TitleStyle.drewButtons = true;
		ci.cancel();
	}
}
