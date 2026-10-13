package dev.hearthside.fabric.client.mixin;

import java.util.Deque;
import java.util.List;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The toasts on screen, for keeping controller hints clear of them, and those waiting for room, for clearing keyboard
 * tutorial toasts when player 1 takes a controller. Each visible entry is a {@link ToastInstanceAccessor}.
 */
@Mixin(ToastManager.class)
public interface ToastManagerAccessor {
	@Accessor("visibleToasts")
	List<?> hearthside$visibleToasts();

	@Accessor("queued")
	Deque<Toast> hearthside$queued();
}
