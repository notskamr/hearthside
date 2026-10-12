package dev.hearthside.fabric.client.compositor;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.ViewFrame;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.mixin.GameRendererAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.WindowRenderState;

/**
 * Hearthside's own GUI over the composited views (Spec B section 3.4): after the host's GUI, a second GUI pass at the
 * full window size renders every registered layer into a transparent target, which the compositor then draws over
 * all the views. The pass reuses Minecraft's GUI renderer: the window state's size is the window's for the pass, and
 * the renderer draws into this target instead of the host's (see {@code GuiRendererTargetMixin}). Render thread.
 */
public final class OverlayPass {
	private static final List<OverlayLayer> LAYERS = new CopyOnWriteArrayList<>();
	private static TextureTarget target;
	private static boolean active;
	/** Whether the target holds this frame's overlay. */
	private static boolean drawn;
	private static boolean failed;
	private static volatile boolean reduceMotion;

	private OverlayPass() {}

	public static void register(OverlayLayer layer) {
		if (!LAYERS.contains(layer)) {
			LAYERS.add(layer);
			LAYERS.sort(java.util.Comparator.comparingInt(OverlayLayer::order));
		}
	}

	public static void unregister(OverlayLayer layer) {
		LAYERS.remove(layer);
	}

	/** Player 1's reduce-motion setting, for layers' animations. */
	public static void setReduceMotion(boolean reduce) {
		reduceMotion = reduce;
	}

	/** True only while the pass renders (the GUI renderer then draws into the overlay target). */
	public static boolean active() {
		return active;
	}

	public static RenderTarget target() {
		return target;
	}

	/** The texture holding this frame's overlay, or empty when nothing was drawn. */
	public static Optional<GpuTextureView> texture() {
		return drawn && target != null ? Optional.ofNullable(target.getColorTextureView()) : Optional.empty();
	}

	/** At the end of the host's frame: renders every layer at the window's size, while compositing. */
	public static void run(Minecraft minecraft, GameRenderer gameRenderer) {
		drawn = false;
		Compositor compositor = Compositor.active();
		if (compositor == null || LAYERS.isEmpty() || failed) {
			return;
		}
		int width = compositor.windowWidth();
		int height = compositor.windowHeight();
		if (width <= 0 || height <= 0) {
			return;
		}
		if (target == null) {
			target = new TextureTarget("hearthside_overlay", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
		} else if (target.width != width || target.height != height) {
			target.resize(width, height);
		}
		RenderSystem.getDevice().createCommandEncoder()
				.clearColorAndDepthTextures(target.getColorTexture(), new org.joml.Vector4f(0, 0, 0, 0), target.getDepthTexture(), 0.0);
		WindowRenderState window = gameRenderer.gameRenderState().windowRenderState;
		int ownWidth = window.width;
		int ownHeight = window.height;
		int ownScale = window.guiScale;
		// The scale Minecraft would pick for the whole window (player 1's own, at their split view's size, is smaller).
		// The overlay draws no items, so changing it for this pass never rebuilds the item atlas.
		int scale = scaleFor(width, height, minecraft.options.guiScale().get());
		window.width = width;
		window.height = height;
		window.guiScale = scale;
		active = true;
		try {
			var graphics = new GuiGraphicsExtractor(minecraft, gameRenderer.gameRenderState().guiRenderState, -1, -1);
			var context = new OverlayContext(Math.ceilDiv(width, scale), Math.ceilDiv(height, scale), scale,
					inGuiUnits(compositor.overlayViews(), scale), System.nanoTime(), reduceMotion);
			boolean first = true;
			for (OverlayLayer layer : LAYERS) {
				if (!first) {
					graphics.nextStratum(); // above all of the layer before, its text too
				}
				first = false;
				layer.extract(graphics, context);
			}
			GuiRenderer renderer = ((GameRendererAccessor) gameRenderer).hearthside$guiRenderer();
			renderer.render();
			renderer.endFrame();
			drawn = true;
		} catch (RuntimeException e) {
			// Never take the game down for an overlay: stop drawing overlays for this session and say why once.
			failed = true;
			HearthsideMod.LOGGER.error("Hearthside overlay pass failed; overlays are off for this session", e);
		} finally {
			active = false;
			window.width = ownWidth;
			window.height = ownHeight;
			window.guiScale = ownScale;
		}
	}

	/** Minecraft's GUI scale for a framebuffer this size: the largest fitting 320×240, capped by the setting (0 = auto). */
	static int scaleFor(int width, int height, int setting) {
		int scale = 1;
		while (scale != setting && scale < width && scale < height && width / (scale + 1) >= 320 && height / (scale + 1) >= 240) {
			scale++;
		}
		return scale;
	}

	/** A window-pixel rect in the overlay's GUI units (covering every pixel it touched). */
	public static Rect inGui(Rect r, int scale) {
		int x = Math.floorDiv(r.x(), scale);
		int y = Math.floorDiv(r.y(), scale);
		return new Rect(x, y, Math.ceilDiv(r.x() + r.width(), scale) - x, Math.ceilDiv(r.y() + r.height(), scale) - y);
	}

	private static List<ViewFrame> inGuiUnits(List<ViewFrame> views, int scale) {
		List<ViewFrame> out = new ArrayList<>(views.size());
		for (ViewFrame view : views) {
			Rect r = view.rect();
			int x = Math.floorDiv(r.x(), scale);
			int y = Math.floorDiv(r.y(), scale);
			int right = Math.ceilDiv(r.x() + r.width(), scale);
			int bottom = Math.ceilDiv(r.y() + r.height(), scale);
			out.add(new ViewFrame(view.seat(), new Rect(x, y, right - x, bottom - y), view.alpha()));
		}
		return out;
	}
}
