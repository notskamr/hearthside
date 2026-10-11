package dev.hearthside.fabric.client.mixin;

import dev.hearthside.core.compositor.ViewportMath;
import dev.hearthside.fabric.client.compositor.Compositor;
import dev.hearthside.fabric.client.input.SyntheticMouse;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the host composites, player 1's game thinks its window is just its viewport: real mouse positions are moved
 * into the viewport's coordinates, and clicks or scrolls over other players' views are ignored.
 */
@Mixin(MouseHandler.class)
abstract class HostMouseMixin {
	private static ViewportMath.Point hearthside$lastWindowPosition = new ViewportMath.Point(0, 0);

	@Shadow
	public abstract void onMove(long handle, double xpos, double ypos, double xrel, double yrel);

	@Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
	private void hearthside$intoViewport(long handle, double xpos, double ypos, double xrel, double yrel, CallbackInfo ci) {
		Compositor compositor = Compositor.active();
		if (compositor == null || SyntheticMouse.active()) {
			return;
		}
		hearthside$lastWindowPosition = new ViewportMath.Point(xpos, ypos);
		ViewportMath.Point local = ViewportMath.windowToViewport(hearthside$lastWindowPosition, compositor.hostViewport(),
				compositor.screenPerFramebuffer());
		ci.cancel();
		SyntheticMouse.run(() -> onMove(handle, local.x(), local.y(), xrel, yrel));
	}

	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void hearthside$onlyInsideViewport(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (hearthside$outsideHostViewport()) {
			ci.cancel();
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void hearthside$scrollOnlyInsideViewport(long handle, double x, double y, CallbackInfo ci) {
		if (hearthside$outsideHostViewport()) {
			ci.cancel();
		}
	}

	private static boolean hearthside$outsideHostViewport() {
		Compositor compositor = Compositor.active();
		return compositor != null && !SyntheticMouse.active()
				&& !ViewportMath.contains(hearthside$lastWindowPosition, compositor.hostViewport(), compositor.screenPerFramebuffer());
	}
}
