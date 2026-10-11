package dev.hearthside.fabric.client.compositor;

import static org.lwjgl.opengl.GL45C.*;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import dev.hearthside.core.compositor.FrameSlots;
import dev.hearthside.core.compositor.SegmentSlotTable;
import dev.hearthside.core.compositor.Transport;
import dev.hearthside.core.compositor.ViewportMath;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.ViewFrame;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.fabric.HearthsideMod;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

/**
 * Host side of the compositor: draws player 1's own frame and every guest's newest shared frame into their views of
 * the one window, in place of Minecraft's single blit to the window. Views glide (their rects come from a
 * {@link ViewSource} each frame); a guest's frames at a new size replace the old ones only once one has arrived, and a
 * frame waiting for its new size is drawn softly blurred, so no view ever shows a gap. Render thread only.
 */
public final class Compositor {
	private static final float[] DIVIDER = {0x12 / 255f, 0x0c / 255f, 0x08 / 255f, 1f};
	private static final long PROBE_INTERVAL_NANOS = 1_000_000_000L;
	private static Compositor active;

	/** One guest's imported frame slots. */
	private static final class Import {
		final int generation;
		final SegmentSlotTable table;
		final FrameSlots slots;
		final FrameTransport transport;
		/** Slots this host still holds, each released once the fence of the last frame that drew it signals. */
		final ArrayDeque<long[]> releases = new ArrayDeque<>(); // {slot, fence}
		int held = -1;
		long lastFrame;
		long lastFence;

		Import(int generation, SegmentSlotTable table, FrameTransport transport) {
			this.generation = generation;
			this.table = table;
			this.slots = new FrameSlots(table);
			this.transport = transport;
		}

		void close() {
			for (long[] r : releases) {
				glDeleteSync(r[1]);
			}
			releases.clear();
			if (lastFence != 0) {
				glDeleteSync(lastFence);
				lastFence = 0;
			}
			transport.close();
			table.close();
		}
	}

	private final Minecraft minecraft;
	private final Runnable onWindowResized;
	private final java.util.function.IntConsumer onImportFailed;
	private ViewSource views;
	/** A guest's frames at a new size (or from a restarted game), drawn from the first frame they publish. */
	private final Map<Integer, Import> incoming = new HashMap<>();
	/** Views as drawn this frame: computed once, shared by the overlay pass (which runs first) and present. */
	private List<ViewFrame> frames = List.of();
	private boolean framesFresh;
	private final Map<Integer, Rect> drawn = new HashMap<>();
	private final Set<Integer> framed = new HashSet<>(), stale = new HashSet<>();
	private int blurFbo, blurTexture, blurWidth, blurHeight;
	private final Map<Integer, Import> imports = new HashMap<>();
	/** Imports replaced by a newer generation, closed once the GPU has finished drawing from them. */
	private final ArrayDeque<Import> retired = new ArrayDeque<>();
	private final boolean probe = "1".equals(System.getenv("HEARTHSIDE_E2E_PATTERN"));
	private final ByteBuffer probePixel = BufferUtils.createByteBuffer(4);
	private Rect hostViewport;
	private final OverlayBlitter overlayBlitter = new OverlayBlitter();
	private double screenPerFb = 1.0;
	private int layoutWidth, layoutHeight;
	private int readFbo;
	private long lastProbe;
	// Composition cost, logged as percentiles every 30 s.
	private final long[] costs = new long[4096];
	private int costCount;
	private long costWindowStart = System.nanoTime();

	private Compositor(Minecraft minecraft, Runnable onWindowResized, java.util.function.IntConsumer onImportFailed) {
		this.minecraft = minecraft;
		this.onWindowResized = onWindowResized;
		this.onImportFailed = onImportFailed;
	}

	public static Compositor active() {
		return active;
	}

	/**
	 * Starts (or updates) compositing: {@code views} says where each player's view is drawn, in the window's
	 * framebuffer pixels for a {@code windowWidth}×{@code windowHeight} framebuffer.
	 */
	public static Compositor activate(Minecraft minecraft, Runnable onWindowResized, java.util.function.IntConsumer onImportFailed,
			ViewSource views, int windowWidth, int windowHeight) {
		if (active == null) {
			active = new Compositor(minecraft, onWindowResized, onImportFailed);
		}
		Compositor c = active;
		c.views = views;
		c.layoutWidth = windowWidth;
		c.layoutHeight = windowHeight;
		c.screenPerFb = screenPerFramebuffer(minecraft);
		Set<Integer> seats = views.seats();
		for (Integer gone : List.copyOf(c.imports.keySet())) {
			if (!seats.contains(gone)) {
				c.retired.add(c.imports.remove(gone));
			}
		}
		for (Integer gone : List.copyOf(c.incoming.keySet())) {
			if (!seats.contains(gone)) {
				c.retired.add(c.incoming.remove(gone));
			}
		}
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
		c.imports.values().forEach(Import::close);
		c.imports.clear();
		c.incoming.values().forEach(Import::close);
		c.incoming.clear();
		c.retired.forEach(Import::close);
		c.retired.clear();
		c.overlayBlitter.close();
		if (c.readFbo != 0) {
			glDeleteFramebuffers(c.readFbo);
		}
		if (c.blurFbo != 0) {
			glDeleteFramebuffers(c.blurFbo);
			glDeleteTextures(c.blurTexture);
		}
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

	public Rect hostViewport() {
		return hostViewport;
	}

	/** Window coordinates per framebuffer pixel, as of the last layout. */
	public double screenPerFramebuffer() {
		return screenPerFb;
	}

	/** A guest (re)allocated its frame slots; import them. */
	public void onGuestSlots(int playerIndex, ControlMessage.FrameSlotsReady ready) {
		Import previous = imports.get(playerIndex);
		Import waiting = incoming.get(playerIndex);
		if ((previous != null && previous.generation == ready.generation())
				|| (waiting != null && waiting.generation == ready.generation())) {
			return;
		}
		SegmentSlotTable table;
		try {
			table = SegmentSlotTable.open(Path.of(ready.controlFile()));
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not open player {}'s frame slots at {}", playerIndex + 1, ready.controlFile(), e);
			return;
		}
		FrameTransport transport;
		try {
			if (ready.transport() == Transport.GPU_SHARED && Boolean.getBoolean("hearthside.e2e.failGpuImport")) {
				throw new IllegalStateException("E2E: simulated GPU import failure");
			}
			transport = ready.transport() == Transport.GPU_SHARED
					? GpuSharedTransport.importByName(ready.controlFile(), table, ready.width(), ready.height())
					: CopyTransport.host(CopyTransport.pixelFileFor(Path.of(ready.controlFile())), ready.width(), ready.height(),
							table.slots());
		} catch (RuntimeException e) {
			HearthsideMod.LOGGER.error("Could not import player {}'s frames", playerIndex + 1, e);
			table.close();
			if (ready.transport() == Transport.GPU_SHARED) {
				// Spec B section 4: fall back to copying rather than leave this player frozen on their last frame.
				onImportFailed.accept(playerIndex);
			}
			return;
		}
		Import fresh = new Import(ready.generation(), table, transport);
		if (previous == null) {
			imports.put(playerIndex, fresh);
		} else {
			// The old frames stay up (stretched, blurred) until the new ones arrive: never a gap.
			Import older = incoming.put(playerIndex, fresh);
			if (older != null) {
				retired.add(older);
			}
		}
		HearthsideMod.LOGGER.info("Hearthside compositor: player {} frames {} {}x{}", playerIndex + 1,
				ready.transport(), ready.width(), ready.height());
	}

	/** Draws every view into the window's back buffer. Called in place of Minecraft's blit; the swap follows. */
	public void present(int hostTexture, int hostWidth, int hostHeight, int swapWidth, int swapHeight) {
		long start = System.nanoTime();
		if (swapWidth != layoutWidth || swapHeight != layoutHeight) {
			layoutWidth = swapWidth;
			layoutHeight = swapHeight;
			minecraft.execute(onWindowResized);
		}
		if (readFbo == 0) {
			readFbo = glCreateFramebuffers();
		}
		GlStateManager._disableScissorTest();
		GlStateManager._colorMask(15);
		glClearNamedFramebufferfv(0, GL_COLOR, 0, DIVIDER);

		if (!framesFresh) {
			frames = views.frames(System.nanoTime());
		}
		framesFresh = false;
		drawn.clear();
		framed.clear();
		stale.clear();
		for (ViewFrame view : frames) {
			Rect rect = view.rect();
			drawn.put(view.seat(), rect);
			if (view.seat() == 0) {
				hostViewport = rect;
				blit(hostTexture, hostWidth, hostHeight, rect, swapHeight);
				framed.add(0);
				continue;
			}
			Import imp = current(view.seat());
			if (imp == null || imp.held < 0) {
				continue; // the overlay shows the hearth there
			}
			Rect target = views.target(view.seat()).orElse(rect);
			int width = imp.transport.width(), height = imp.transport.height();
			int texture = imp.transport.glTexture(imp.held);
			if (width != target.width() || height != target.height()) {
				blur(texture, width, height, rect, swapHeight);
				stale.add(view.seat());
			} else {
				blit(texture, width, height, rect, swapHeight);
			}
			framed.add(view.seat());
		}
		int overlay = OverlayPass.textureId();
		if (overlay != 0) {
			// Hearthside's GUI over every view: cards, toasts, name tags, the party panel.
			overlayBlitter.draw(overlay, swapWidth, swapHeight);
		}
		List<Import> live = new java.util.ArrayList<>(imports.values());
		live.addAll(incoming.values());
		for (Import imp : live) {
			for (long[] r : imp.releases) {
				if (r[1] == 0) {
					// Signals once the GPU has finished every draw so far, including this frame's last use of the slot.
					r[1] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
				}
			}
			releaseFinished(imp);
		}
		closeRetired();
		if (probe) {
			probeGuests(swapHeight);
		}
		if (e2eGapSampling) {
			sampleGaps(swapHeight);
		}
		recordCost(System.nanoTime() - start);
	}

	private static void acquire(Import imp) {
		OptionalInt newer = imp.slots.acquireNewest(imp.lastFrame);
		if (newer.isPresent()) {
			if (imp.held >= 0) {
				imp.releases.add(new long[] {imp.held, 0}); // fence filled in after this frame's draws
			}
			imp.held = newer.getAsInt();
			imp.lastFrame = imp.table.frame(imp.held);
			imp.transport.beforeRead(imp.held);
		}
	}

	/** A guest's frames to draw: its newer import once that has a frame, else the one it had. */
	private Import current(int seat) {
		Import imp = imports.get(seat);
		if (imp != null) {
			acquire(imp);
		}
		Import next = incoming.get(seat);
		if (next != null) {
			acquire(next);
			if (next.held >= 0 || imp == null) {
				incoming.remove(seat);
				if (imp != null) {
					retired.add(imp);
				}
				imports.put(seat, next);
				imp = next;
			}
		}
		return imp;
	}

	/** A soft blur for a picture waiting for its new size: down to an eighth, then back up, both filtered. */
	private void blur(int texture, int width, int height, Rect target, int swapHeight) {
		int w = Math.max(1, width / 8), h = Math.max(1, height / 8);
		if (blurFbo == 0 || w != blurWidth || h != blurHeight) {
			if (blurFbo == 0) {
				blurFbo = glCreateFramebuffers();
			} else {
				glDeleteTextures(blurTexture);
			}
			blurTexture = glCreateTextures(GL_TEXTURE_2D);
			glTextureStorage2D(blurTexture, 1, GL_RGBA8, w, h);
			glNamedFramebufferTexture(blurFbo, GL_COLOR_ATTACHMENT0, blurTexture, 0);
			blurWidth = w;
			blurHeight = h;
		}
		glNamedFramebufferTexture(readFbo, GL_COLOR_ATTACHMENT0, texture, 0);
		glBlitNamedFramebuffer(readFbo, blurFbo, 0, 0, width, height, 0, 0, w, h, GL_COLOR_BUFFER_BIT, GL_LINEAR);
		Rect gl = ViewportMath.glFlip(target, swapHeight);
		glBlitNamedFramebuffer(blurFbo, 0, 0, 0, w, h, gl.x(), gl.y(), gl.x() + gl.width(), gl.y() + gl.height(),
				GL_COLOR_BUFFER_BIT, GL_LINEAR);
	}

	private void blit(int texture, int width, int height, Rect target, int swapHeight) {
		Rect gl = ViewportMath.glFlip(target, swapHeight);
		glNamedFramebufferTexture(readFbo, GL_COLOR_ATTACHMENT0, texture, 0);
		glBlitNamedFramebuffer(readFbo, 0, 0, 0, width, height, gl.x(), gl.y(), gl.x() + gl.width(), gl.y() + gl.height(),
				GL_COLOR_BUFFER_BIT, width == gl.width() && height == gl.height() ? GL_NEAREST : GL_LINEAR);
	}

	private static void releaseFinished(Import imp) {
		while (!imp.releases.isEmpty()) {
			long[] next = imp.releases.peekFirst();
			int status = glClientWaitSync(next[1], 0, 0);
			if (status != GL_ALREADY_SIGNALED && status != GL_CONDITION_SATISFIED) {
				return;
			}
			imp.releases.removeFirst();
			glDeleteSync(next[1]);
			imp.slots.release((int) next[0]);
		}
	}

	private void closeRetired() {
		// Safe straight away: GL defers freeing a deleted texture or memory object until the GPU has stopped using it,
		// and an imported memory object keeps the shared allocation alive for as long as it exists.
		while (!retired.isEmpty()) {
			retired.removeFirst().close();
		}
	}

	private void recordCost(long nanos) {
		if (costCount < costs.length) {
			costs[costCount++] = nanos;
		}
		long now = System.nanoTime();
		if (now - costWindowStart < 30_000_000_000L || costCount == 0) {
			return;
		}
		long[] sorted = java.util.Arrays.copyOf(costs, costCount);
		java.util.Arrays.sort(sorted);
		HearthsideMod.LOGGER.info("Hearthside compositor cost guests={} p50={} ms p95={} ms frames={}", Math.max(0, drawn.size() - 1),
				String.format("%.3f", sorted[sorted.length / 2] / 1e6), String.format("%.3f", sorted[(int) (sorted.length * 0.95)] / 1e6),
				costCount);
		costCount = 0;
		costWindowStart = now;
	}

	/** E2E: while set, every view's centre is read each frame and a bare divider colour is logged as a gap. */
	public static volatile boolean e2eGapSampling;

	/** E2E: a window point (pixels from the top-left) whose composited colour is logged too, or null. */
	public static volatile int[] e2eProbePoint;

	private final Map<Integer, Long> lastGapLog = new HashMap<>();

	/** E2E: reads the centre of every view this frame; the bare divider colour there is a gap (logged at most 4×/s). */
	private void sampleGaps(int swapHeight) {
		int previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
		glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
		long now = System.nanoTime();
		drawn.forEach((seat, rect) -> {
			glReadPixels(rect.x() + rect.width() / 2, swapHeight - (rect.y() + rect.height() / 2) - 1, 1, 1, GL_RGBA,
					GL_UNSIGNED_BYTE, probePixel);
			boolean bare = (probePixel.get(0) & 0xFF) == 0x12 && (probePixel.get(1) & 0xFF) == 0x0c
					&& (probePixel.get(2) & 0xFF) == 0x08;
			if (bare && now - lastGapLog.getOrDefault(seat, 0L) > 250_000_000L) {
				lastGapLog.put(seat, now);
				HearthsideMod.LOGGER.info("Hearthside compositor gap player={}", seat + 1);
			}
		});
		glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead);
	}

	/** E2E: logs the composited colour just inside each guest viewport's top-left corner, once a second. */
	private void probeGuests(int swapHeight) {
		long now = System.nanoTime();
		if (now - lastProbe < PROBE_INTERVAL_NANOS) {
			return;
		}
		lastProbe = now;
		int previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
		glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
		for (Map.Entry<Integer, Rect> guest : drawn.entrySet()) {
			Import imp = imports.get(guest.getKey());
			if (guest.getKey() == 0 || imp == null) {
				continue;
			}
			Rect fitted = guest.getValue();
			glReadPixels(fitted.x() + 4, swapHeight - (fitted.y() + 4) - 1, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, probePixel);
			HearthsideMod.LOGGER.info("Hearthside compositor probe player={} gen={} rgb={}", guest.getKey() + 1, imp.generation,
					String.format("%02x%02x%02x", probePixel.get(0) & 0xFF, probePixel.get(1) & 0xFF, probePixel.get(2) & 0xFF));
		}
		int[] point = e2eProbePoint;
		if (point != null) {
			glReadPixels(point[0], swapHeight - point[1] - 1, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, probePixel);
			HearthsideMod.LOGGER.info("Hearthside overlay probe rgb={}", String.format("%02x%02x%02x", probePixel.get(0) & 0xFF,
					probePixel.get(1) & 0xFF, probePixel.get(2) & 0xFF));
		}
		glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead);
	}
}
