package dev.hearthside.fabric.client.input;

import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.mixin.ToastInstanceAccessor;
import dev.hearthside.fabric.client.mixin.ToastManagerAccessor;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.TutorialToast;

/**
 * Minecraft's tutorial toasts are for keyboard and mouse (Spec D section 3.5), and stay until their step is done, which
 * some steps count only from the mouse. New ones never reach player 1 on a controller (ToastFilterMixin); this clears
 * the ones already up or waiting when they take one. The player's tutorial progress is left alone.
 */
public final class TutorialToasts {
	private TutorialToasts() {}

	/** Slides out every tutorial toast on screen, the way Minecraft does, and drops any waiting for room. Render thread. */
	public static void clear(Minecraft mc) {
		ToastManagerAccessor manager = (ToastManagerAccessor) mc.gui.toastManager();
		int cleared = 0;
		for (Object entry : manager.hearthside$visibleToasts()) {
			Toast toast = ((ToastInstanceAccessor) entry).hearthside$toast();
			if (toast instanceof TutorialToast tutorial && tutorial.getWantedVisibility() != Toast.Visibility.HIDE) {
				tutorial.hide();
				cleared++;
			}
		}
		Deque<Toast> queued = manager.hearthside$queued();
		int waiting = queued.size();
		queued.removeIf(toast -> toast instanceof TutorialToast);
		cleared += waiting - queued.size();
		if (cleared > 0) {
			HearthsideMod.LOGGER.info("Hearthside cleared {} keyboard tutorial toast(s) as player 1 took a controller", cleared);
		}
	}
}
