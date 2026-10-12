package dev.hearthside.fabric.client.compositor;

/** How this game's split-screen views reach the screen. */
public final class Presentation {
	private Presentation() {}

	/** Satellite: the host asked for a real tiled window instead of compositing. */
	public static boolean satelliteTiled() {
		return "tiled".equals(System.getProperty("hearthside.presenter"));
	}

	/** Satellite or host: use the copy transport even if GPU sharing is available. */
	public static boolean forceCopy() {
		return Boolean.getBoolean("hearthside.forceCopy");
	}
}
