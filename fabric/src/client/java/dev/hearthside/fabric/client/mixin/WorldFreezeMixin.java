package dev.hearthside.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.host.Party;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Pausing together (Spec E section 3.2): in split-screen player 1's pause screen no longer stops the world on its own;
 * it freezes only when everyone in the world is paused ({@link Party#freezeAllowed}). Only the pause screen's part of
 * Minecraft's pause decision changes: the world open to LAN still never pauses, and playing alone it's vanilla's.
 *
 * <p>Optional ({@code require = 0}): if another mod redirects that call there is nothing to attach to, Minecraft starts
 * regardless and the pause is vanilla's; {@code HearthsideMixinPlugin} logs it as Minecraft starts.
 */
@Mixin(Minecraft.class)
abstract class WorldFreezeMixin {
	private static boolean hearthside$failed;

	@ModifyExpressionValue(method = "runTick", require = 0,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;isPausing()Z"))
	private boolean hearthside$freezeOnlyWhenEveryoneIsPaused(boolean pausing) {
		if (!pausing || hearthside$failed) {
			return pausing;
		}
		try {
			Party party = Party.current();
			return !Party.splitScreen() || party == null || party.freezeAllowed();
		} catch (RuntimeException e) {
			// Never break Minecraft's pause: vanilla's rule from here on.
			hearthside$failed = true;
			HearthsideMod.LOGGER.error("Hearthside could not decide whether the world freezes; pausing as vanilla does", e);
			return pausing;
		}
	}
}
