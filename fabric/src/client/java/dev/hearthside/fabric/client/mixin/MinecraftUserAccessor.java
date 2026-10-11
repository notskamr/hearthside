package dev.hearthside.fabric.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A satellite starts before its player has chosen a profile, under a placeholder name, and takes the player's name
 * when it is sent into a world (the login Hello carries {@code getUser().getName()}).
 */
@Mixin(Minecraft.class)
public interface MinecraftUserAccessor {
	@Mutable
	@Accessor("user")
	void hearthside$setUser(User user);
}
