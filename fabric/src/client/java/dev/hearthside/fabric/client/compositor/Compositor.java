package dev.hearthside.fabric.client.compositor;

import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.hearthside.core.compositor.CopyReason;
import dev.hearthside.core.compositor.ImageDescription;
import dev.hearthside.core.compositor.SegmentSlotTable;
import dev.hearthside.core.compositor.SharingFacts;
import dev.hearthside.core.compositor.Transport;
import dev.hearthside.core.compositor.TransportChoice;
import dev.hearthside.core.compositor.ViewQuad;
import dev.hearthside.core.compositor.ViewportMath;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.ViewFrame;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.compositor.backend.ImportFailed;
import dev.hearthside.fabric.client.compositor.backend.SharedImages;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import org.joml.Vector4f;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

/**
 * Host side of the compositor (Spec C section 3.3): draws player 1's own frame and every guest's newest frame into
 * their views on the {@link Canvas}, which Minecraft then presents in place of player 1's frame. It works through
 * Minecraft's GPU API only, so it runs on OpenGL and Vulkan alike. Views glide (their rects come from a
 * {@link ViewSource} each frame); a guest's frames at a new size replace the old ones only once one has arrived, and a
 * frame waiting for its new size is drawn softly blurred, so no view ever shows a gap. Render thread only.
 */
public final class Compositor {
	private static final Vector4f DIVIDER = new Vector4f(0x12 / 255f, 0x0c / 255f, 0x08 / 255f, 1f);
	private static Compositor active;

	/** One view to draw this frame. */
	private record Draw(int seat, GpuTextureView source, Rect rect, float alpha, boolean stale, boolean rowsTopDown) {}

	private final Minecraft minecraft;
	private final Runnable onWindowResized;
	private final IntConsumer onImportFailed;
	private ViewSource views;
	private final Map<Integer, GuestImport> imports = new HashMap<>();
	/** A guest's frames at a new size (or from a restarted game), drawn from the first frame that reaches the host. */
	private final Map<Integer, GuestImport> incoming = new HashMap<>();
	/** Imports replaced by a newer generation, closed once the GPU has finished copying from their slots. */
	private final ArrayDeque<GuestImport> retired = new ArrayDeque<>();
	/** Seats whose game crashed: their last picture stays (under their card) until their restarted game is in the world. */
	private final Set<Integer> holding = new HashSet<>();
	/** Views as drawn this frame: computed once, shared by the overlay pass (which runs first) and compose. */
	private List<ViewFrame> frames = List.of();
	private boolean framesFresh;
	private final Map<Integer, Rect> drawn = new HashMap<>();
	private final Set<Integer> framed = new HashSet<>(), stale = new HashSet<>();
	private final Canvas canvas = new Canvas("hearthside canvas");
	/** The fallback's per-seat scratch pictures, for views drawn at another size than their frame's. */
	private final Map<Integer, Canvas> scratch = new HashMap<>();
	/** The fallback's one opaque texel, which puts the canvas's alpha back to 1 after the views are copied in. */
	private final Canvas opaqueTexel = new Canvas("hearthside opaque texel");
	private final CanvasReadback readback = new CanvasReadback();
	private Rect hostViewport;
	private double screenPerFb = 1.0;
	private int layoutWidth, layoutHeight;
	private boolean composeFailed;

	private Compositor(Minecraft minecraft, Runnable onWindowResized, IntConsumer onImportFailed) {
		this.minecraft = minecraft;
		this.onWindowResized = onWindowResized;
		this.onImportFailed = onImportFailed;
	}

	public static Compositor active() {
		return active;
	}

	/**
	 * Whether this game can composite: a 1×1 canvas can be created and the pipelines warmed (Spec C section 5). Called
	 * when the party session opens, before any guest starts. A view pipeline that fails to build leaves views on the
	 * fallback rather than refusing; anything else failing means tiled windows for the session.
	 */
	public static boolean canComposite() {
		try (Canvas probe = new Canvas("hearthside canvas check")) {
			probe.ensure(1, 1);
			ViewPipeline.warmUp();
			return true;
		} catch (RuntimeException | LinkageError e) {
			HearthsideMod.LOGGER.error("Hearthside can't composite here; tiled windows for this session", e);
			return false;
		}
	}

	/**
	 * Starts (or updates) compositing: {@code views} says where each player's view is drawn, in the window's
	 * framebuffer pixels for a {@code windowWidth}×{@code windowHeight} framebuffer.
	 */
	public static Compositor activate(Minecraft minecraft, Runnable onWindowResized, IntConsumer onImportFailed,
			ViewSource views, int windowWidth, int windowHeight) {
		if (active == null) {
			active = new Compositor(minecraft, onWindowResized, onImportFailed);
		}
		Compositor c = active;
		c.views = views;
		c.layoutWidth = windowWidth;
		c.layoutHeight = windowHeight;
		c.screenPerFb = screenPerFramebuffer(minecraft);
		c.retireGone();
		Rect host = views.target(0).orElse(new Rect(0, 0, windowWidth, windowHeight));
		if (c.hostViewport == null) {
			c.hostViewport = host;
		}
		// Player 1 renders at their view's final size at once; while it glides the frame is stretched to fit.
		VirtualSizing.apply(minecraft, ViewportMath.virtualSize(host, c.screenPerFb));
		return c;
	}

	/** Stops compositing: player 1 renders full-window again. */
	public static void deactivate(Minecraft minecraft) {
		Compositor c = active;
		if (c == null) {
			return;
		}
		active = null;
		VirtualSizing.clear(minecraft);
		// Minecraft destroys each texture once the GPU has finished with it.
		c.imports.values().forEach(GuestImport::close);
		c.imports.clear();
		c.incoming.values().forEach(GuestImport::close);
		c.incoming.clear();
		c.retired.forEach(GuestImport::close);
		c.retired.clear();
		c.scratch.values().forEach(Canvas::close);
		c.scratch.clear();
		c.opaqueTexel.close();
		c.canvas.close();
	}

	/** Window coordinates per framebuffer pixel (1.0 normally, 0.5 on a 2x display). */
	public static double screenPerFramebuffer(Minecraft minecraft) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1);
			SDLVideo.SDL_GetWindowSize(minecraft.getWindow().handle(), w, h);
			int fbWidth = minecraft.getWindow().queryFramebufferSize().width();
			return fbWidth < 1 ? 1.0 : (double) w.get(0) / fbWidth;
		}
	}

	/** The window's framebuffer size, as last presented (0 before the first frame). */
	public int windowWidth() {
		return layoutWidth;
	}

	public int windowHeight() {
		return layoutHeight;
	}

	/** Every view as drawn this frame, in window pixels from the top-left: player 1 (seat 0) first. */
	public List<ViewFrame> overlayViews() {
		frames = views.frames(System.nanoTime());
		framesFresh = true;
		return frames;
	}

	/** Whether a seat's view showed a picture from its game last frame (player 1's always does). */
	public boolean hasFrame(int seat) {
		return framed.contains(seat);
	}

	/** Whether a seat's picture was waiting for its new size last frame (drawn blurred). */
	public boolean isStale(int seat) {
		return stale.contains(seat);
	}

	/**
	 * While held, a seat keeps showing the last picture it had (its game crashed) and a restarted game's frames wait;
	 * once let go, they show from its next frame.
	 */
	public void holdLastFrame(int seat, boolean hold) {
		if (hold) {
			holding.add(seat);
		} else {
			holding.remove(seat);
		}
	}

	public Rect hostViewport() {
		return hostViewport;
	}

	/** Window coordinates per framebuffer pixel, as of the last layout. */
	public double screenPerFramebuffer() {
		return screenPerFb;
	}

	/**
	 * A guest (re)allocated its frame slots; import them. GPU-shared slots are imported only when the guest's GPU and
	 * driver are this game's (Spec C section 3.7, step 3); otherwise the guest is told to copy, and the host says why.
	 */
	public void onGuestSlots(int playerIndex, ControlMessage.FrameSlotsReady ready) {
		GuestImport previous = imports.get(playerIndex);
		GuestImport waiting = incoming.get(playerIndex);
		if ((previous != null && previous.generation() == ready.generation())
				|| (waiting != null && waiting.generation() == ready.generation())) {
			return;
		}
		SegmentSlotTable table;
		try {
			table = SegmentSlotTable.open(Path.of(ready.controlFile()));
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not open player {}'s frame slots at {}", playerIndex + 1, ready.controlFile(), e);
			return;
		}
		if (ready.transport() == Transport.GPU_SHARED) {
			Optional<CopyReason> copy = table.description().flatMap(description -> TransportChoice
					.decide(guestFacts(description), SharedImages.forThisGame().facts(), false).reason());
			if (copy.isPresent()) {
				HearthsideMod.LOGGER.info("Hearthside player {} copies frames: {}", playerIndex + 1, copy.get());
				table.close();
				onImportFailed.accept(playerIndex);
				return;
			}
		}
		GuestImport fresh;
		try {
			fresh = GuestImport.open(ready, table, SharedImages.forThisGame());
		} catch (ImportFailed | RuntimeException e) {
			HearthsideMod.LOGGER.error("Could not import player {}'s frames", playerIndex + 1, e);
			table.close();
			if (ready.transport() == Transport.GPU_SHARED) {
				// Spec B section 4: fall back to copying rather than leave this player frozen on their last frame.
				onImportFailed.accept(playerIndex);
			}
			return;
		}
		if (previous == null) {
			imports.put(playerIndex, fresh);
		} else {
			// The old frames stay up (stretched, blurred) until the new ones arrive: never a gap.
			GuestImport older = incoming.put(playerIndex, fresh);
			if (older != null) {
				retired.add(older);
			}
		}
		HearthsideMod.LOGGER.info("Hearthside compositor: player {} frames {} {}x{} backend={}", playerIndex + 1,
				ready.transport(), ready.width(), ready.height(),
				com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().backendName().toLowerCase(java.util.Locale.ROOT));
	}

	/**
	 * What a guest that exported GPU slots can do, from its slot table: it is on Windows and can share memory, or its
	 * own decision would not have exported them. A nil UUID (the guest's backend couldn't report one) is unknown, and
	 * never matches this game's.
	 */
	private static SharingFacts guestFacts(ImageDescription description) {
		UUID device = description.deviceUuid();
		if (Boolean.getBoolean("hearthside.e2e.fakeDeviceMismatch")) {
			// E2E: a guest on another GPU.
			device = UUID.randomUUID();
		}
		return new SharingFacts(true, true, known(device), known(description.driverUuid()));
	}

	private static UUID known(UUID uuid) {
		return SharedImages.UNKNOWN.equals(uuid) ? null : uuid;
	}

	/**
	 * Draws every view into the canvas and returns it for Minecraft to present, in place of player 1's frame
	 * ({@code hostView}). Called at Minecraft's one blit to the window, with that window surface's size. Returns
	 * {@code hostView} unchanged when there is nothing to draw into (a minimised window).
	 */
	public GpuTextureView compose(GpuTextureView hostView, CommandEncoder encoder, int surfaceWidth, int surfaceHeight) {
		long start = System.nanoTime();
		if (!canvas.ensure(surfaceWidth, surfaceHeight)) {
			return hostView;
		}
		if (surfaceWidth != layoutWidth || surfaceHeight != layoutHeight) {
			layoutWidth = surfaceWidth;
			layoutHeight = surfaceHeight;
			minecraft.execute(onWindowResized);
		}
		try {
			releaseFinished();
			if (!framesFresh) {
				frames = views.frames(System.nanoTime());
			}
			framesFresh = false;
			drawn.clear();
			framed.clear();
			stale.clear();
			List<Draw> draws = new ArrayList<>();
			List<CanvasReadback.GuestView> guests = new ArrayList<>();
			boolean tearCheck = FrameHooks.tearCheck();
			List<CanvasReadback.SteadyView> steady = new ArrayList<>();
			for (ViewFrame view : frames) {
				Rect rect = view.rect();
				drawn.put(view.seat(), rect);
				if (view.seat() == 0) {
					hostViewport = rect;
					draws.add(new Draw(0, hostView, rect, view.alpha(), false, false));
					framed.add(0);
					continue;
				}
				GuestImport imp = current(view.seat(), encoder);
				if (imp == null || !imp.hasFrame()) {
					continue; // the overlay shows the hearth there
				}
				Rect target = views.target(view.seat()).orElse(rect);
				boolean waiting = imp.width() != target.width() || imp.height() != target.height();
				if (waiting) {
					stale.add(view.seat());
				}
				draws.add(new Draw(view.seat(), imp.hostTexture(), rect, view.alpha(), waiting, imp.rowsTopDown()));
				framed.add(view.seat());
				guests.add(new CanvasReadback.GuestView(view.seat() + 1, imp.generation(), rect));
				if (tearCheck && view.alpha() >= 1f && !waiting && rect.equals(target)) {
					// Fully faded in, at its frame's size and not gliding: every pixel is the guest's own.
					steady.add(new CanvasReadback.SteadyView(view.seat() + 1, imp, rect));
				}
			}
			// While checking frames, nothing of Hearthside's (borders, name tags) lands on the sampled points.
			draw(encoder, draws, tearCheck ? Optional.empty() : OverlayPass.texture());
			retireGone();
			readback.afterCompose(encoder, canvas, guests, frames);
			if (tearCheck) {
				readback.checkTears(encoder, canvas, steady);
			}
		} catch (RuntimeException e) {
			// Never take the game down for a frame: show player 1's own view instead, and say why once.
			if (!composeFailed) {
				composeFailed = true;
				HearthsideMod.LOGGER.error("Hearthside could not compose the canvas; showing player 1's view", e);
			}
			return hostView;
		}
		readback.recordCost(System.nanoTime() - start, Math.max(0, drawn.size() - 1));
		return canvas.view();
	}

	/** The compose pass (§3.3): the divider, every view in order, then Hearthside's overlay over them all. */
	private void draw(CommandEncoder encoder, List<Draw> draws, Optional<GpuTextureView> overlay) {
		int width = canvas.width(), height = canvas.height();
		boolean overlaySameSize = overlay.isPresent() && overlay.get().getWidth(0) == width && overlay.get().getHeight(0) == height;
		if (!ViewPipeline.usingFallback()) {
			try (RenderPass pass = encoder.createRenderPass(() -> "Hearthside canvas", canvas.view(), Optional.of(DIVIDER))) {
				for (Draw d : draws) {
					ViewQuad.of(d.rect(), width, height, d.source().getWidth(0), d.source().getHeight(0), d.rowsTopDown(),
							d.alpha(), d.stale()).ifPresent(quad -> ViewPipeline.draw(pass, d.source(), quad));
				}
				overlay.ifPresent(texture -> ViewPipeline.drawOverlay(pass, texture, overlaySameSize));
			}
			return;
		}
		// Without our pipeline: no fades or blur, each view scaled if need be and copied into place.
		encoder.clearColorTexture(canvas.texture(), DIVIDER);
		for (Draw d : draws) {
			ViewPipeline.drawPlaced(encoder, canvas, d.source(), d.rect(),
					scratch.computeIfAbsent(d.seat(), seat -> new Canvas("hearthside fallback view " + seat)));
		}
		opaqueTexel.ensure(1, 1); // opaque black from creation
		try (RenderPass pass = encoder.createRenderPass(() -> "Hearthside overlay", canvas.view(), Optional.empty())) {
			// The copies brought each frame's alpha with them; views are opaque pictures, as the view pipeline draws them.
			ViewPipeline.makeOpaque(pass, opaqueTexel.view());
			overlay.ifPresent(texture -> ViewPipeline.drawOverlay(pass, texture, overlaySameSize));
		}
	}

	/** A guest's frames to draw: its newer import once that has a frame, else the one it had. */
	private GuestImport current(int seat, CommandEncoder encoder) {
		// A crashed game's last picture stays, and its restarted game's frames wait, until it is let go.
		boolean held = holding.contains(seat);
		GuestImport imp = imports.get(seat);
		if (imp != null && !held) {
			imp.pull(encoder);
		}
		GuestImport next = incoming.get(seat);
		if (next != null && !held) {
			next.pull(encoder);
		}
		if (next != null && ((next.hasFrame() && !held) || imp == null)) {
			incoming.remove(seat);
			if (imp != null) {
				retired.add(imp);
			}
			imports.put(seat, next);
			imp = next;
		}
		return imp;
	}

	/** Gives back every slot whose copy has finished; closes replaced imports once nothing is copied from them. */
	private void releaseFinished() {
		imports.values().forEach(GuestImport::releaseFinished);
		incoming.values().forEach(GuestImport::releaseFinished);
		retired.removeIf(imp -> {
			if (!imp.idle()) {
				return false;
			}
			imp.close();
			return true;
		});
	}

	/** A seat with no view any more (gone, or faded out after leaving) lets go of its game's frames. */
	private void retireGone() {
		Set<Integer> seats = views.seats();
		for (Integer gone : List.copyOf(imports.keySet())) {
			if (!seats.contains(gone)) {
				retired.add(imports.remove(gone));
			}
		}
		for (Integer gone : List.copyOf(incoming.keySet())) {
			if (!seats.contains(gone)) {
				retired.add(incoming.remove(gone));
			}
		}
		for (Integer gone : List.copyOf(scratch.keySet())) {
			if (!seats.contains(gone)) {
				scratch.remove(gone).close();
			}
		}
		holding.retainAll(seats);
	}

	/** E2E: where to save the next composited window as a PNG (what the players see), or null. */
	public static volatile Path e2eCapture;

	/** E2E: while set, every view's centre is read each frame and a bare divider colour is logged as a gap. */
	public static volatile boolean e2eGapSampling;

	/** E2E: a window point (pixels from the top-left) whose composited colour is logged too, or null. */
	public static volatile int[] e2eProbePoint;
}
