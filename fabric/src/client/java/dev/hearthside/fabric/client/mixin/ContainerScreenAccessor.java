package dev.hearthside.fabric.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen's panel and slots are drawn, for controller slot snapping and placing hints. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos")
	int hearthside$leftPos();

	@Accessor("topPos")
	int hearthside$topPos();

	@Accessor("imageWidth")
	int hearthside$imageWidth();

	@Accessor("imageHeight")
	int hearthside$imageHeight();
}
