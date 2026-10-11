package dev.hearthside.fabric.client.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** One-shot key presses (inventory, drop, ...) are counted clicks on a key mapping. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int hearthside$getClickCount();

	@Accessor("clickCount")
	void hearthside$setClickCount(int clicks);
}
