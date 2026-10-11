package dev.hearthside.fabric.client.mixin;

import dev.hearthside.fabric.client.input.FrameHook;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs {@link FrameHook} callbacks once per rendered frame. */
@Mixin(Minecraft.class)
abstract class FrameHookMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void hearthside$onFrame(boolean advanceGameTime, CallbackInfo ci) {
		FrameHook.run();
	}
}
