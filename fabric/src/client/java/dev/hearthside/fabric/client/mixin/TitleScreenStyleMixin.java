package dev.hearthside.fabric.client.mixin;

import dev.hearthside.fabric.client.HearthsideClient;
import dev.hearthside.fabric.client.host.Party;
import dev.hearthside.fabric.client.host.SplitScreenConfigFile;
import dev.hearthside.fabric.client.ui.TitleStyle;
import dev.hearthside.core.Role;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The title screen's background in the Cozy-hearth style instead of the panorama (host only; Spec A section 4.1), or
 * Minecraft's own if player 1 chose it (Spec E section 3.5): each title screen takes the setting as it is created.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenStyleMixin extends Screen {
	private TitleScreenStyleMixin() {
		super(null);
	}

	@Inject(method = "<init>(ZLnet/minecraft/client/gui/components/LogoRenderer;)V", at = @At("TAIL"))
	private void hearthside$style(CallbackInfo ci) {
		if (HearthsideClient.role() != Role.HOST) {
			return;
		}
		Party party = Party.current();
		TitleStyle.opening(party != null ? party.config().titleStyle()
				: SplitScreenConfigFile.peek(Minecraft.getInstance().gameDirectory.toPath()).titleStyle());
	}

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void hearthside$background(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (HearthsideClient.role() == Role.HOST && TitleStyle.applies()) {
			TitleStyle.background(g, width, height);
			ci.cancel();
		}
	}
}
