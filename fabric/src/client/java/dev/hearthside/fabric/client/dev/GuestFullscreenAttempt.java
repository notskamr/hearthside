package dev.hearthside.fabric.client.dev;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hearthside.fabric.HearthsideMod;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import org.lwjgl.sdl.SDLVideo;

/**
 * E2E (Q4), on a guest's game: a few seconds into the world it tries every way into fullscreen (the option, the window,
 * the Toggle Fullscreen key), logs what came of it, then shows vanilla's Video Settings and Sodium's (when installed)
 * for the host to capture, and closes them. Enabled by {@value #ENV}{@code =1} in the guest's environment, which a host
 * started with {@code -Dhearthside.e2e.guestTryFullscreen=true} sets (satellites get no {@code -Dhearthside.*}).
 */
public final class GuestFullscreenAttempt {
	public static final String ENV = "HEARTHSIDE_E2E_GUEST_TRY_FULLSCREEN";

	private int inWorldTicks;

	private GuestFullscreenAttempt() {}

	public static void registerIfEnabled() {
		if ("1".equals(System.getenv(ENV))) {
			ClientTickEvents.END_CLIENT_TICK.register(new GuestFullscreenAttempt()::tick);
		}
	}

	private void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		inWorldTicks++;
		// 3 s in, once the world has settled; then each settings screen shows for 5 s.
		switch (inWorldTicks) {
			case 60 -> tryEveryWay(minecraft);
			case 100 -> {
				minecraft.gui.setScreen(new VideoSettingsScreen(null, minecraft, minecraft.options));
				HearthsideMod.LOGGER.info("Hearthside e2e guest shows vanilla Video Settings");
			}
			case 200 -> {
				Screen sodium = sodiumVideoSettings();
				minecraft.gui.setScreen(sodium);
				HearthsideMod.LOGGER.info("Hearthside e2e guest shows {}",
						sodium == null ? "no Sodium Video Settings" : "Sodium's Video Settings");
			}
			case 300 -> {
				minecraft.gui.setScreen(null);
				HearthsideMod.LOGGER.info("Hearthside e2e guest closed Video Settings");
			}
			default -> {}
		}
	}

	private static void tryEveryWay(Minecraft minecraft) {
		HearthsideMod.LOGGER.info("Hearthside e2e guest tries fullscreen 3 ways");
		minecraft.options.fullscreen().set(true);
		minecraft.getWindow().setFullscreen(true);
		minecraft.handleGlobalKeyPress(InputConstants.getKey(minecraft.options.keyFullscreen.saveString()), false);
		// A window applies a request on its next frame: look after that.
		minecraft.execute(() -> minecraft.execute(() -> {
			long flags = SDLVideo.SDL_GetWindowFlags(minecraft.getWindow().handle());
			HearthsideMod.LOGGER.info("Hearthside e2e guest fullscreen option={} window fullscreen={} hidden={}",
					minecraft.options.fullscreen().get(), (flags & SDLVideo.SDL_WINDOW_FULLSCREEN) != 0,
					(flags & SDLVideo.SDL_WINDOW_HIDDEN) != 0);
		}));
	}

	/** Sodium's Video Settings, which replace vanilla's from the Options screen; null without Sodium. */
	private static Screen sodiumVideoSettings() {
		if (!FabricLoader.getInstance().isModLoaded("sodium")) {
			return null;
		}
		try {
			Method create = Class.forName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen")
					.getMethod("createScreen", Screen.class);
			return (Screen) create.invoke(null, (Screen) null);
		} catch (ReflectiveOperationException | LinkageError e) {
			HearthsideMod.LOGGER.warn("Hearthside e2e could not open Sodium's Video Settings", e);
			return null;
		}
	}
}
