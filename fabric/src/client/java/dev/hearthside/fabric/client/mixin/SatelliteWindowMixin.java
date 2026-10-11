package dev.hearthside.fabric.client.mixin;

import com.mojang.renderpearl.backend.opengl.GlBackend;
import dev.hearthside.core.Role;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A satellite's window is driven by a controller and ignores the OS keyboard and mouse, so it is not focusable:
 * clicking it does nothing. (Not being activated when first shown comes from the launch environment's
 * {@code SDL_WINDOW_ACTIVATE_WHEN_SHOWN=0}; this flag alone does not prevent that.)
 */
@Mixin(GlBackend.class)
abstract class SatelliteWindowMixin {
	@ModifyArg(method = "createWindow", index = 3,
			at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_CreateWindow(Ljava/lang/CharSequence;IIJ)J"))
	private long hearthside$notFocusable(long flags) {
		// Read the property directly: the window exists before client entrypoints run.
		return Role.fromProperty(System.getProperty(Role.PROPERTY)) == Role.SATELLITE
				? flags | SDLVideo.SDL_WINDOW_NOT_FOCUSABLE
				: flags;
	}
}
