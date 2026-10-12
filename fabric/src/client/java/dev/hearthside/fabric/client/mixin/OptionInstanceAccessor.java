package dev.hearthside.fabric.client.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * An option's value without {@code set}'s side effects: a guest's capped render distance is swapped for its own setting
 * only while {@code options.txt} is written, which must not make the world re-render.
 */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
	@Accessor("value")
	void hearthside$setValue(Object value);
}
