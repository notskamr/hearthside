package dev.hearthside.fabric.client.window;

import dev.hearthside.fabric.HearthsideMod;
import net.minecraft.client.Minecraft;

/** Logs when this window gains or loses focus: vanilla changes behaviour with focus, so bug reports need it. */
public final class FocusLog implements Runnable {
	private Boolean focused;

	@Override
	public void run() {
		boolean now = Minecraft.getInstance().isWindowActive();
		if (focused == null || focused != now) {
			focused = now;
			HearthsideMod.LOGGER.info("Hearthside window focused: {}", now);
		}
	}
}
