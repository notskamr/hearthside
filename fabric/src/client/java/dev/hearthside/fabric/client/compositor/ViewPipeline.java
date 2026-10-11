package dev.hearthside.fabric.client.compositor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.hearthside.core.compositor.ViewQuad;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.compositor.backend.TextureRegions;
import java.nio.ByteBuffer;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

/**
 * How a view is drawn onto the canvas (Spec C section 3.3). Normally through {@code hearthside:pipeline/view}: one quad
 * per view, placed, scaled, faded and (when stale) blurred by its {@link ViewQuad} push constants. If that pipeline
 * can't be built (say a resource pack overrides {@code hearthside:core/view} badly), views are drawn through Minecraft's
 * own {@code screenquad}/{@code blit_screen} pair instead, without fades or blur. That pair always fills its whole
 * target (a render pass's render area only scissors; it never moves the viewport, on either backend), so the fallback
 * scales a view into a scratch texture of the view's size when needed, then copies it into place. Render thread only.
 */
public final class ViewPipeline {
	private static final Identifier SHADER = Identifier.fromNamespaceAndPath("hearthside", "core/view");
	private static final Identifier RELOAD_LISTENER = Identifier.fromNamespaceAndPath("hearthside", "view_pipeline");
	static final RenderPipeline VIEW = RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath("hearthside", "pipeline/view"))
			.withVertexShader(SHADER)
			.withFragmentShader(SHADER)
			.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withCull(false)
			.withPushConstantSize(ViewQuad.BYTES)
			.build();
	/** The fallback: Minecraft's blit pair, as {@code RenderPipelines.TRACY_BLIT} uses it, without its unused globals. */
	static final RenderPipeline PLAIN_BLIT = RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath("hearthside", "pipeline/view_fallback"))
			.withVertexShader("core/screenquad")
			.withFragmentShader("core/blit_screen")
			.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
			.withColorTargetState(ColorTargetState.DEFAULT)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.build();
	/**
	 * The overlay over every view: the same blit pair, blended as premultiplied alpha (Minecraft's GUI blending onto a
	 * transparent target leaves exactly that), as {@code OverlayBlitter} blended it on OpenGL.
	 */
	static final RenderPipeline OVERLAY = RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath("hearthside", "pipeline/overlay"))
			.withVertexShader("core/screenquad")
			.withFragmentShader("core/blit_screen")
			.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.build();

	private static boolean fallback;
	/** Whether this game composites (a host), so resource reloads warm the pipelines again. */
	private static boolean wanted;

	private ViewPipeline() {}

	/** Whether views are drawn without fades or blur, because {@code hearthside:pipeline/view} couldn't be built. */
	public static boolean usingFallback() {
		return fallback;
	}

	/**
	 * Builds the pipelines and draws each once off-screen into a 1×1 texture, so the first composited frame never stalls
	 * on Minecraft compiling them. If the view pipeline fails, views use the fallback until the next warm-up, and that is
	 * logged once. The fallback and overlay use Minecraft's own shaders; a failure there is thrown.
	 */
	public static void warmUp() {
		wanted = true;
		try (Canvas target = new Canvas("hearthside warm-up target"); Canvas source = new Canvas("hearthside warm-up source")) {
			target.ensure(1, 1);
			source.ensure(1, 1);
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			encoder.clearColorTexture(source.texture(), new Vector4f(0, 0, 0, 1));
			try (RenderPass pass = encoder.createRenderPass(() -> "Hearthside warm-up", target.view(), Optional.empty())) {
				blit(pass, PLAIN_BLIT, source.view(), FilterMode.NEAREST);
				blit(pass, OVERLAY, source.view(), FilterMode.NEAREST);
			}
			try {
				if (Boolean.getBoolean("hearthside.e2e.breakViewPipeline")) {
					throw new IllegalStateException("E2E: the view pipeline is broken on purpose");
				}
				if (RenderSystem.getCompiledPipelineNullable(VIEW) == null) {
					throw new IllegalStateException("Minecraft could not build " + VIEW.getLocation());
				}
				try (RenderPass pass = encoder.createRenderPass(() -> "Hearthside warm-up", target.view(), Optional.empty())) {
					ViewQuad.of(new Rect(0, 0, 1, 1), 1, 1, 1, 1, false, 1f, true).ifPresent(quad -> draw(pass, source.view(), quad));
				}
				fallback = false;
				HearthsideMod.LOGGER.info("Hearthside view pipeline warmed");
			} catch (RuntimeException e) {
				fallback = true;
				HearthsideMod.LOGGER.warn("Hearthside view pipeline unavailable; drawing views without fades", e);
			}
		}
	}

	/**
	 * After every resource reload, once Minecraft has rebuilt its pipelines (and dropped ours with them), warms ours
	 * again, in a game that composites.
	 */
	public static void registerReloadListener() {
		ResourceLoader loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
		loader.registerReloadListener(RELOAD_LISTENER, (ResourceManagerReloadListener) manager -> {
			if (!wanted) {
				return;
			}
			try {
				warmUp();
			} catch (RuntimeException e) {
				// Never fail Minecraft's reload over this; the first frame builds what it needs, or the compose logs why.
				HearthsideMod.LOGGER.error("Hearthside could not warm its pipelines after the reload", e);
			}
		});
		loader.addListenerOrdering(ResourceReloaderKeys.Client.SHADERS, RELOAD_LISTENER);
	}

	/** Draws {@code source} as {@code quad} says, in a pass on the canvas. Only while not {@link #usingFallback}. */
	public static void draw(RenderPass pass, GpuTextureView source, ViewQuad quad) {
		pass.setPipeline(RenderSystem.getCompiledPipeline(VIEW));
		// Filtered: it scales mid-glide and blurs; at one texel per pixel it samples texel centres, so nothing smears.
		pass.setUniform("InSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer constants = stack.malloc(ViewQuad.BYTES);
			quad.write(constants);
			pass.pushConstants(constants.flip());
		}
		pass.draw(6, 1, 0, 0);
	}

	/** Draws the overlay over the whole canvas, in a pass on it. */
	static void drawOverlay(RenderPass pass, GpuTextureView overlay, boolean sameSize) {
		blit(pass, OVERLAY, overlay, sameSize ? FilterMode.NEAREST : FilterMode.LINEAR);
	}

	/**
	 * The fallback: puts {@code source} into {@code rect} (pixels from the canvas's top-left), scaled through
	 * {@code scratch} when its size differs from the rect's, then copied into place. Outside any render pass.
	 */
	static void drawPlaced(CommandEncoder encoder, Canvas canvas, GpuTextureView source, Rect rect, Canvas scratch) {
		int x0 = Math.max(0, rect.x()), x1 = Math.min(canvas.width(), rect.x() + rect.width());
		int y0 = Math.max(0, rect.y()), y1 = Math.min(canvas.height(), rect.y() + rect.height());
		if (x0 >= x1 || y0 >= y1) {
			return; // off the canvas, or no area
		}
		GpuTexture picture = source.texture();
		if (source.getWidth(0) != rect.width() || source.getHeight(0) != rect.height()) {
			scratch.ensure(rect.width(), rect.height());
			try (RenderPass pass = encoder.createRenderPass(() -> "Hearthside fallback view", scratch.view(), Optional.empty())) {
				blit(pass, PLAIN_BLIT, source, FilterMode.LINEAR);
			}
			picture = scratch.texture();
		}
		// Both are stored bottom-up, so the visible part's lowest row is canvas row (height - y1) and picture row
		// (rect bottom - y1).
		TextureRegions.copy(encoder, picture, canvas.texture(), x0, canvas.height() - y1, x0 - rect.x(),
				rect.y() + rect.height() - y1, x1 - x0, y1 - y0);
	}

	private static void blit(RenderPass pass, RenderPipeline pipeline, GpuTextureView source, FilterMode filter) {
		pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
		pass.setUniform("InSampler", source, RenderSystem.getSamplerCache().getClampToEdge(filter));
		pass.draw(3, 1, 0, 0);
	}
}
