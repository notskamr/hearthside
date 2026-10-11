package dev.hearthside.fabric.client.mixin;

import dev.hearthside.fabric.client.HearthsideClient;
import dev.hearthside.fabric.client.ui.TitleStyle;
import dev.hearthside.core.Role;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The title screen's background in the Cozy-hearth style instead of the panorama (host only; Spec A section 4.1). */
@Mixin(TitleScreen.class)
public abstract class TitleScreenStyleMixin extends Screen {
	private TitleScreenStyleMixin() {
		super(null);
	}

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void hearthside$background(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (HearthsideClient.role() == Role.HOST) {
			TitleStyle.background(g, width, height);
			ci.cancel();
		}
	}
}
