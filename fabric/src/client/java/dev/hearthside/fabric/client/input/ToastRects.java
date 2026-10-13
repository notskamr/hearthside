package dev.hearthside.fabric.client.input;

import dev.hearthside.core.layout.Rect;
import dev.hearthside.fabric.client.mixin.ToastInstanceAccessor;
import dev.hearthside.fabric.client.mixin.ToastManagerAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;

/**
 * Where Minecraft's toasts are, in GUI pixels (Spec D section 3.5). Toasts draw after the screen, so anything under one
 * is hidden; controller hints treat them as obstacles. Each rect is where its toast sits once fully slid in, so hints
 * move before a toast arrives rather than once it already covers them.
 */
public final class ToastRects {
	private ToastRects() {}

	/** Render thread. Empty when the HUD is hidden (F1), which hides toasts too. */
	public static List<Rect> visible(Minecraft mc) {
		if (mc.gui.hud.isHidden()) {
			return List.of();
		}
		int guiWidth = mc.getWindow().getGuiScaledWidth();
		List<Rect> rects = new ArrayList<>();
		for (Object entry : ((ToastManagerAccessor) mc.gui.toastManager()).hearthside$visibleToasts()) {
			ToastInstanceAccessor instance = (ToastInstanceAccessor) entry;
			Toast toast = instance.hearthside$toast();
			rects.add(new Rect((int) toast.xPos(guiWidth, 1f), (int) toast.yPos(instance.hearthside$firstSlotIndex()),
					toast.width(), toast.height()));
		}
		return rects;
	}
}
