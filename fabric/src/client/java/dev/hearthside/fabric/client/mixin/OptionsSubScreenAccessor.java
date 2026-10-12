package dev.hearthside.fabric.client.mixin;

import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** An options screen's list of option widgets: a guest's Video Settings grey out Fullscreen in it. */
@Mixin(OptionsSubScreen.class)
public interface OptionsSubScreenAccessor {
	@Accessor("list")
	OptionsList hearthside$list();
}
