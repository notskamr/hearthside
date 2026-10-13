package dev.hearthside.fabric.client.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * An option's value without {@code set}'s side effects: a session's options (a guest's capped render distance, a
 * preset's light graphics) are swapped for the player's own only while {@code options.txt} is written, which must not
 * make the world re-render or apply a graphics preset.
 */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
	@Accessor("value")
	void hearthside$setValue(Object value);
}
