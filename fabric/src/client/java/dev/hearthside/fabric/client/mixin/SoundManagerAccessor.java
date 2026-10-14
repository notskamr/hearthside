package dev.hearthside.fabric.client.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The sound engine, for the E2E's probe of whether player 1's game's sounds are paused (N3). */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
	@Accessor("soundEngine")
	SoundEngine hearthside$soundEngine();
}
