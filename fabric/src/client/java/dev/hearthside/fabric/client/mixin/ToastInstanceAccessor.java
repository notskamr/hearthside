package dev.hearthside.fabric.client.mixin;

import net.minecraft.client.gui.components.toasts.Toast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** One toast on screen and the slot it was given, for keeping controller hints clear of it. */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
public interface ToastInstanceAccessor {
	@Accessor("toast")
	Toast hearthside$toast();

	@Accessor("firstSlotIndex")
	int hearthside$firstSlotIndex();

	/** How far it has slid in: 0 out of sight, 1 all the way. */
	@Accessor("visiblePortion")
	float hearthside$visiblePortion();
}
