package dev.hearthside.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.hearthside.fabric.client.compositor.GuiScaleOverride;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Every view's GUI is the same size (Spec D section 3.4): while split-screen forces the party's GUI scale, Minecraft
 * lays its GUI out at that scale instead of the one its own setting picks. The setting itself is never written, so
 * nothing of it is saved. Player 1's game and guests' alike.
 */
@Mixin(Minecraft.class)
abstract class GuiScaleMixin {
	@ModifyExpressionValue(method = "resizeGui",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;calculateScale(IZ)I"))
	private int hearthside$partyGuiScale(int scale) {
		int forced = GuiScaleOverride.forced();
		return forced > 0 ? forced : scale;
	}
}
