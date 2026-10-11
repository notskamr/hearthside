package dev.hearthside.fabric.client.mixin;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Minecraft's built-in skins by name, for the lobby's skin previews (quick setup offers all 18). */
@Mixin(DefaultPlayerSkin.class)
public interface DefaultPlayerSkinInvoker {
	@Invoker("create")
	static PlayerSkin hearthside$create(String name, PlayerModelType model) {
		throw new AssertionError("mixin");
	}
}
