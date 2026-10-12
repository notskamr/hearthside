package dev.hearthside.fabric.client.satellite;

import dev.hearthside.core.Role;
import dev.hearthside.fabric.HearthsideMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * A guest never goes fullscreen. Its window is hidden (or tiled beside the others), and fullscreen could change the
 * monitor's display mode or put the guest's window over player 1's. Every way in is refused on a guest's game: the
 * Fullscreen option, the Toggle Fullscreen key, a request to the window itself, and fullscreen remembered in
 * {@code options.txt} (which a guest's game copies from the host's on its first launch) or asked for on the command
 * line. Each attempt is logged as {@value #REFUSED}. Video Settings grey Fullscreen out ({@link GuestVideoSettings}).
 *
 * <p>Kept free of screen classes: the options and the window use it while Minecraft is still starting.
 */
public final class GuestFullscreen {
	public static final String REFUSED = "Hearthside guest fullscreen refused";

	/** Read from the property directly: the options and the window exist before client entrypoints run. */
	private static final boolean GUEST = Role.fromProperty(System.getProperty(Role.PROPERTY)) == Role.SATELLITE;
	private static final String FULLSCREEN_CAPTION = "options.fullscreen";

	private GuestFullscreen() {}

	/** Whether this game is a guest's. */
	public static boolean onGuest() {
		return GUEST;
	}

	/** Whether an option with this caption is Minecraft's Fullscreen (it is set while the options load, so by caption). */
	public static boolean isFullscreen(Component caption) {
		return caption.getContents() instanceof TranslatableContents key && key.getKey().equals(FULLSCREEN_CAPTION);
	}

	/** Logs a refused attempt and how it came. */
	public static void refused(String how) {
		HearthsideMod.LOGGER.info("{}: {}", REFUSED, how);
	}
}
