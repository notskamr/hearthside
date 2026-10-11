package dev.hearthside.fabric.client.compositor;

import static org.lwjgl.opengl.GL45C.*;

import dev.hearthside.core.channel.ControlConnection;
import dev.hearthside.core.compositor.FrameNames;
import dev.hearthside.core.compositor.FrameSlots;
import dev.hearthside.core.compositor.SegmentSlotTable;
import dev.hearthside.core.compositor.Transport;
import dev.hearthside.core.compositor.TransportChoice;
import dev.hearthside.core.compositor.ViewportMath;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.fabric.HearthsideMod;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;

/**
 * Satellite side of the compositor: renders at the viewport's size and hands every finished frame to the host
 * through shared frame slots instead of showing it in a window. Render thread only, except {@link #install}.
 */
public final class FrameExporter {
	private static final int PATTERN_SIZE = 8;
	private static final ByteBuffer MAGENTA = BufferUtils.createByteBuffer(4).put((byte) 255).put((byte) 0)
			.put((byte) 255).put((byte) 255).flip();
	private static volatile FrameExporter instance;

	private record Pending(int slot, long fence, long frame) {}

	private final Minecraft minecraft;
	private final ControlConnection connection;
	private final Path dir;
	private final boolean pattern = "1".equals(System.getenv("HEARTHSIDE_E2E_PATTERN"));
	private final ArrayDeque<Pending> pending = new ArrayDeque<>();
	private int generation = FrameNames.firstGeneration() - 1;
	private SegmentSlotTable table;
	private FrameSlots slots;
	private FrameTransport transport;
	private Path controlFile;
	private long frame;
	private int readFbo, drawFbo;
	// The generation before the current one, kept until the next reallocation (see retireCurrent).
	private FrameTransport previousTransport;
	private SegmentSlotTable previousTable;
	private int previousGeneration = -1;
	private boolean copyOnly;
	private Transport probed;

	private FrameExporter(Minecraft minecraft, ControlConnection connection) {
		this.minecraft = minecraft;
		this.connection = connection;
		this.dir = minecraft.gameDirectory.toPath().toAbsolutePath();
	}

	/** Called once the control channel is up; any thread. */
	public static void install(Minecraft minecraft, ControlConnection connection) {
		instance = new FrameExporter(minecraft, connection);
		minecraft.execute(() -> instance.deleteOldFiles());
	}

	/** The satellite's exporter, or null before the control channel is up (or when the host tiles windows). */
	public static FrameExporter instance() {
		return instance;
	}

	/** The host gave this player a viewport: render at its size, paced to the host's display. Render thread. */
	public void onViewport(Rect rect, int refreshHz) {
		VirtualSizing.apply(minecraft, ViewportMath.virtualSize(rect, 1.0));
		minecraft.options.framerateLimit().set(Math.clamp(refreshHz, 10, 260));
		reallocate(rect.width(), rect.height());
	}

	/** Copies the finished frame into the next free slot. Called instead of Minecraft's blit to the window. */
	public void export(int sourceTexture, int sourceWidth, int sourceHeight) {
		publishFinished();
		if (transport == null) {
			return;
		}
		OptionalInt claimed = slots.beginWrite();
		if (claimed.isEmpty()) {
			return; // the host still holds every slot; drop this frame
		}
		int slot = claimed.getAsInt();
		int target = transport.glTexture(slot);
		if (readFbo == 0) {
			readFbo = glCreateFramebuffers();
			drawFbo = glCreateFramebuffers();
		}
		glNamedFramebufferTexture(readFbo, GL_COLOR_ATTACHMENT0, sourceTexture, 0);
		glNamedFramebufferTexture(drawFbo, GL_COLOR_ATTACHMENT0, target, 0);
		// A blit, not a raw copy: it converts formats (shader packs may use float targets) and scales mid-resize.
		glBlitNamedFramebuffer(readFbo, drawFbo, 0, 0, sourceWidth, sourceHeight, 0, 0, transport.width(), transport.height(),
				GL_COLOR_BUFFER_BIT, GL_LINEAR);
		if (pattern) {
			// E2E: paint the frame's top-left corner (GL rows count from the bottom) so the host can find it.
			glClearTexSubImage(target, 0, 0, transport.height() - PATTERN_SIZE, 0, PATTERN_SIZE, PATTERN_SIZE, 1,
					GL_RGBA, GL_UNSIGNED_BYTE, MAGENTA);
		}
		transport.afterDraw(slot);
		long fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
		// No buffer swap follows (the window is hidden), so make sure the GPU actually gets this frame's work.
		glFlush();
		pending.addLast(new Pending(slot, fence, ++frame));
	}

	/** Publishes, in order, every pending frame the GPU has finished. */
	private void publishFinished() {
		while (!pending.isEmpty()) {
			Pending next = pending.peekFirst();
			int status = glClientWaitSync(next.fence(), 0, 0);
			if (status != GL_ALREADY_SIGNALED && status != GL_CONDITION_SATISFIED) {
				return;
			}
			pending.removeFirst();
			glDeleteSync(next.fence());
			transport.beforePublish(next.slot());
			slots.publish(next.slot(), next.frame());
		}
	}

	/** The host could not use our shared GPU frames: copy them from now on. Render thread. */
	public void useCopy() {
		copyOnly = true;
		if (transport != null) {
			reallocate(transport.width(), transport.height(), true);
		}
	}

	/** GPU sharing or copying, decided once per process (each probe creates Vulkan instances). */
	private Transport transportChoice() {
		if (copyOnly || Presentation.forceCopy()) {
			return Transport.COPY;
		}
		if (probed == null) {
			probed = TransportChoice.choose(GlInterop.probe(false));
		}
		return probed;
	}

	private void reallocate(int width, int height) {
		reallocate(width, height, false);
	}

	private void reallocate(int width, int height, boolean force) {
		if (!force && transport != null && transport.width() == width && transport.height() == height) {
			return;
		}
		retireCurrent();
		generation++;
		controlFile = dir.resolve("frames-" + generation + ".slots");
		try {
			table = SegmentSlotTable.create(controlFile, FrameSlots.DEFAULT_SLOTS, generation);
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not create the frame slot table at {}", controlFile, e);
			return;
		}
		slots = new FrameSlots(table);
		Transport choice = transportChoice();
		if (choice == Transport.GPU_SHARED) {
			try {
				transport = GpuSharedTransport.export(controlFile.toString(), table, width, height);
			} catch (RuntimeException e) {
				HearthsideMod.LOGGER.warn("GPU frame sharing failed; copying frames instead", e);
				choice = Transport.COPY;
			}
		}
		if (choice == Transport.COPY) {
			transport = CopyTransport.guest(CopyTransport.pixelFileFor(controlFile), width, height, table.slots());
		}
		HearthsideMod.LOGGER.info("Hearthside frame slots ready transport={} {}x{}", choice.name().toLowerCase(java.util.Locale.ROOT),
				width, height);
		try {
			connection.send(new ControlMessage.FrameSlotsReady(choice, width, height, generation, controlFile.toString()));
		} catch (IOException e) {
			HearthsideMod.LOGGER.warn("Could not announce frame slots to the host: {}", e.toString());
		}
	}

	/**
	 * Stops writing the current generation but keeps it alive until the next reallocation: the host may not have
	 * imported it yet (two viewport changes in quick succession), and its named memory must still exist when it does.
	 */
	private void retireCurrent() {
		for (Pending p : pending) {
			glDeleteSync(p.fence());
		}
		pending.clear();
		if (previousTransport != null) {
			previousTransport.close();
		}
		if (previousTable != null) {
			previousTable.close();
		}
		previousTransport = transport;
		previousTable = table;
		previousGeneration = transport == null ? -1 : generation;
		transport = null;
		table = null;
		deleteOldFiles();
	}

	/** Removes slot files from generations before the previous one; any still open are left for next time. */
	private void deleteOldFiles() {
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "frames-*")) {
			for (Path file : files) {
				String name = file.getFileName().toString();
				if (!name.startsWith("frames-" + generation + ".") && !name.startsWith("frames-" + previousGeneration + ".")) {
					try {
						Files.deleteIfExists(file);
					} catch (IOException stillMapped) {
						// the host still has it open
					}
				}
			}
		} catch (IOException e) {
			HearthsideMod.LOGGER.debug("Could not list old frame files", e);
		}
	}
}
