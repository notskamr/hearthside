package dev.hearthside.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.hearthside.fabric.client.input.PadPlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Holding attack only keeps mining while the mouse is grabbed, and an unfocused window never grabs it. A controller
 * driving this client counts as grabbed for that one check, so we never capture the OS cursor for an unfocused window.
 */
@Mixin(Minecraft.class)
abstract class ContinueAttackMixin {
	@ModifyExpressionValue(method = "handleKeybinds",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
	private boolean hearthside$controllerCountsAsGrabbed(boolean grabbed) {
		return grabbed || PadPlay.driving();
	}
}
