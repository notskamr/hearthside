package dev.hearthside.fabric.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.hearthside.core.compositor.ViewportMath;
import dev.hearthside.fabric.client.compositor.Compositor;
import dev.hearthside.fabric.client.input.RealMouse;
import dev.hearthside.fabric.client.input.SyntheticMouse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the host composites, player 1's game thinks its window is just its viewport: real mouse positions are moved
 * into the viewport's coordinates, and clicks or scrolls over other players' views are ignored. Player 1's own use of
 * the mouse (not Minecraft warping the cursor) is noted for {@link RealMouse}.
 */
@Mixin(MouseHandler.class)
abstract class HostMouseMixin {
	private static ViewportMath.Point hearthside$lastWindowPosition = new ViewportMath.Point(0, 0);

	@Shadow
	public abstract void onMove(long handle, double xpos, double ypos, double xrel, double yrel);

	@Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
	private void hearthside$intoViewport(long handle, double xpos, double ypos, double xrel, double yrel, CallbackInfo ci) {
		if (SyntheticMouse.active()) {
			return;
		}
		Compositor compositor = Compositor.active();
		if (compositor == null) {
			RealMouse.moved(xpos, ypos);
			return;
		}
		hearthside$lastWindowPosition = new ViewportMath.Point(xpos, ypos);
		if (!hearthside$pointingAtAnotherView()) {
			// Moving over another player's view isn't using player 1's screen.
			RealMouse.moved(xpos, ypos);
		}
		ViewportMath.Point local = ViewportMath.windowToViewport(hearthside$lastWindowPosition, compositor.hostViewport(),
				compositor.screenPerFramebuffer());
		ci.cancel();
		SyntheticMouse.run(() -> onMove(handle, local.x(), local.y(), xrel, yrel));
	}

	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void hearthside$onlyInsideViewport(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
		// Releases always go through: dropping one would leave a button (attack, a drag) held down.
		if (action == 1 && hearthside$pointingAtAnotherView()) {
			ci.cancel();
		} else if (action == 1 && !SyntheticMouse.active()) {
			RealMouse.used();
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void hearthside$scrollOnlyInsideViewport(long handle, double x, double y, CallbackInfo ci) {
		if (hearthside$pointingAtAnotherView()) {
			ci.cancel();
		} else if (!SyntheticMouse.active()) {
			RealMouse.used();
		}
	}

	/** Grabbing the cursor for play warps it; SDL reports the warp as a move, which isn't the player's. */
	@Redirect(method = "grabMouse", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/InputConstants;grabMouse(Lcom/mojang/blaze3d/platform/Window;DD)V"))
	private void hearthside$noteGrabWarp(Window window, double x, double y) {
		RealMouse.warped(x, y);
		InputConstants.grabMouse(window, x, y);
	}

	/** So does releasing it for a screen. */
	@Redirect(method = "releaseMouse", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/InputConstants;releaseMouse(Lcom/mojang/blaze3d/platform/Window;DD)V"))
	private void hearthside$noteReleaseWarp(Window window, double x, double y) {
		RealMouse.warped(x, y);
		InputConstants.releaseMouse(window, x, y);
	}

	/**
	 * In a menu, a visible cursor over another player's view must not click player 1's menu. In the world the cursor is
	 * hidden and grabbed (or about to be), and wherever it has drifted the clicks are player 1's attacks and uses.
	 */
	private boolean hearthside$pointingAtAnotherView() {
		Compositor compositor = Compositor.active();
		return compositor != null && !SyntheticMouse.active() && Minecraft.getInstance().gui.screen() != null
				&& !((MouseHandler) (Object) this).isMouseGrabbed()
				&& !ViewportMath.contains(hearthside$lastWindowPosition, compositor.hostViewport(), compositor.screenPerFramebuffer());
	}
}
