package dev.hearthside.fabric.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen's panel and slots are drawn, and which slot the pointer is over, for snapping and hints. */
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

	/** The slot under the pointer, or null (for bundle hints). */
	@Accessor("hoveredSlot")
	Slot hearthside$hoveredSlot();
}
