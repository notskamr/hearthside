package dev.hearthside.fabric.client.input;

import dev.hearthside.fabric.HearthsideMod;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Callbacks run once per rendered frame on the render thread, with or without a screen open.
 * Client ticks (20 per second) are too coarse for camera input.
 */
public final class FrameHook {
	private static final List<Runnable> CALLBACKS = new CopyOnWriteArrayList<>();

	private FrameHook() {}

	public static void register(Runnable callback) {
		CALLBACKS.add(callback);
	}

	public static void unregister(Runnable callback) {
		CALLBACKS.remove(callback);
	}

	/** Called by {@code FrameHookMixin}. */
	public static void run() {
		for (Runnable callback : CALLBACKS) {
			try {
				callback.run();
			} catch (RuntimeException e) {
				HearthsideMod.LOGGER.error("Hearthside frame callback failed", e);
			}
		}
	}
}
