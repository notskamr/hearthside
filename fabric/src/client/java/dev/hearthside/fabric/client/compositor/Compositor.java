package dev.hearthside.fabric.client.compositor;

import static org.lwjgl.opengl.GL45C.*;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import dev.hearthside.core.compositor.FrameSlots;
import dev.hearthside.core.compositor.SegmentSlotTable;
import dev.hearthside.core.compositor.Transport;
import dev.hearthside.core.compositor.ViewportMath;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.fabric.HearthsideMod;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

/**
 * Host side of the compositor: draws player 1's own frame and every guest's newest shared frame into their viewports
 * of the one window, in place of Minecraft's single blit to the window. Render thread only.
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
	private final Map<Integer, Rect> guestViewports = new HashMap<>();
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
	 * Starts (or updates) compositing. {@code hostViewport} and {@code guests} (player index → viewport) are in the
	 * window's framebuffer pixels, laid out for a {@code windowWidth}×{@code windowHeight} framebuffer.
	 */
	public static Compositor activate(Minecraft minecraft, Runnable onWindowResized, java.util.function.IntConsumer onImportFailed,
			Rect hostViewport, Map<Integer, Rect> guests, int windowWidth, int windowHeight) {
		if (active == null) {
			active = new Compositor(minecraft, onWindowResized, onImportFailed);
		}
		Compositor c = active;
		c.hostViewport = hostViewport;
		c.guestViewports.clear();
		c.guestViewports.putAll(guests);
		c.layoutWidth = windowWidth;
		c.layoutHeight = windowHeight;
		c.screenPerFb = screenPerFramebuffer(minecraft);
		for (Integer gone : List.copyOf(c.imports.keySet())) {
			if (!guests.containsKey(gone)) {
				c.retired.add(c.imports.remove(gone));
			}
		}
		VirtualSizing.apply(minecraft, ViewportMath.virtualSize(hostViewport, c.screenPerFb));
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
		c.retired.forEach(Import::close);
		c.retired.clear();
		c.overlayBlitter.close();
		if (c.readFbo != 0) {
			glDeleteFramebuffers(c.readFbo);
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

	/** Every view as drawn now, in window pixels from the top-left: player 1 (seat 0), then the guests. */
	public java.util.List<dev.hearthside.core.layout.ViewFrame> overlayViews() {
		java.util.List<dev.hearthside.core.layout.ViewFrame> views = new java.util.ArrayList<>();
		views.add(new dev.hearthside.core.layout.ViewFrame(0, hostViewport, 1f));
		new java.util.TreeMap<>(guestViewports).forEach((seat, rect) -> views.add(new dev.hearthside.core.layout.ViewFrame(seat, rect, 1f)));
		return views;
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
		if (previous != null && previous.generation == ready.generation()) {
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
		if (previous != null) {
			retired.add(previous);
		}
		imports.put(playerIndex, new Import(ready.generation(), table, transport));
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

		blit(hostTexture, hostWidth, hostHeight, hostViewport, swapHeight);
		for (Map.Entry<Integer, Rect> guest : guestViewports.entrySet()) {
			Import imp = imports.get(guest.getKey());
			if (imp == null) {
				continue;
			}
			OptionalInt newer = imp.slots.acquireNewest(imp.lastFrame);
			if (newer.isPresent()) {
				if (imp.held >= 0) {
					imp.releases.add(new long[] {imp.held, 0}); // fence filled in below, after this frame's draws
				}
				imp.held = newer.getAsInt();
				imp.lastFrame = imp.table.frame(imp.held);
				imp.transport.beforeRead(imp.held);
			}
			if (imp.held >= 0) {
				Rect fitted = ViewportMath.letterbox(imp.transport.width(), imp.transport.height(), guest.getValue());
				blit(imp.transport.glTexture(imp.held), imp.transport.width(), imp.transport.height(), fitted, swapHeight);
			}
		}
		int overlay = OverlayPass.textureId();
		if (overlay != 0) {
			// Hearthside's GUI over every view: cards, toasts, name tags, the party panel.
			overlayBlitter.draw(overlay, swapWidth, swapHeight);
		}
		for (Import imp : imports.values()) {
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
		recordCost(System.nanoTime() - start);
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
		HearthsideMod.LOGGER.info("Hearthside compositor cost guests={} p50={} ms p95={} ms frames={}", guestViewports.size(),
				String.format("%.3f", sorted[sorted.length / 2] / 1e6), String.format("%.3f", sorted[(int) (sorted.length * 0.95)] / 1e6),
				costCount);
		costCount = 0;
		costWindowStart = now;
	}

	/** E2E: a window point (pixels from the top-left) whose composited colour is logged too, or null. */
	public static volatile int[] e2eProbePoint;

	/** E2E: logs the composited colour just inside each guest viewport's top-left corner, once a second. */
	private void probeGuests(int swapHeight) {
		long now = System.nanoTime();
		if (now - lastProbe < PROBE_INTERVAL_NANOS) {
			return;
		}
		lastProbe = now;
		int previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
		glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
		for (Map.Entry<Integer, Rect> guest : guestViewports.entrySet()) {
			Import imp = imports.get(guest.getKey());
			if (imp == null) {
				continue;
			}
			Rect fitted = ViewportMath.letterbox(imp.transport.width(), imp.transport.height(), guest.getValue());
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
