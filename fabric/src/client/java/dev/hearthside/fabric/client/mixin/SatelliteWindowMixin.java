package dev.hearthside.fabric.client.mixin;

import dev.hearthside.core.Role;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.compositor.Presentation;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A satellite's window is driven by a controller and ignores the OS keyboard and mouse, so it is not focusable:
 * clicking it does nothing. (Not being activated when first shown comes from the launch environment's
 * {@code SDL_WINDOW_ACTIVATE_WHEN_SHOWN=0}; this flag alone does not prevent that.) When the host composites, the
 * window is also hidden: its frames go to the host instead. Both backends create their window the same way and both
 * are hooked, by name (backend classes are only imported under {@code backend/}).
 */
@Mixin(targets = {"com.mojang.renderpearl.backend.opengl.GlBackend", "com.mojang.renderpearl.backend.vulkan.VulkanBackend"})
abstract class SatelliteWindowMixin {
	@ModifyArg(method = "createWindow", index = 3,
			at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_CreateWindow(Ljava/lang/CharSequence;IIJ)J"))
	private long hearthside$satelliteWindowFlags(long flags) {
		// Read the property directly: the window exists before client entrypoints run.
		if (Role.fromProperty(System.getProperty(Role.PROPERTY)) != Role.SATELLITE) {
			return flags;
		}
		boolean hidden = !Presentation.satelliteTiled();
		HearthsideMod.LOGGER.info("Hearthside window flags hidden={}", hidden);
		return flags | SDLVideo.SDL_WINDOW_NOT_FOCUSABLE | (hidden ? SDLVideo.SDL_WINDOW_HIDDEN : 0);
	}
}
