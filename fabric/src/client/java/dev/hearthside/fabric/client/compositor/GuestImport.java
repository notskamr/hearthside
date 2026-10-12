package dev.hearthside.fabric.client.compositor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuFence;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.hearthside.core.compositor.FrameNames;
import dev.hearthside.core.compositor.FrameSlots;
import dev.hearthside.core.compositor.ImageDescription;
import dev.hearthside.core.compositor.SegmentSlotTable;
import dev.hearthside.core.compositor.Transport;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.fabric.client.compositor.backend.ImportFailed;
import dev.hearthside.fabric.client.compositor.backend.SharedImages;
import dev.hearthside.fabric.client.compositor.backend.SlotMemory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.OptionalInt;

/**
 * One guest's frames for one generation, on the host (Spec C sections 3.3 and 4.2). Each new frame is copied at once
 * into the host's own texture, which is all the compose pass ever reads: a GPU-shared slot is copied on the GPU and
 * given back once that copy's fence signals; a copy-transport slot is uploaded from the pixel file and given back at
 * once. The guest never waits for the host, and the host never waits for the GPU. Render thread only.
 */
public final class GuestImport implements AutoCloseable {
	/** A slot this host still holds, given back once the GPU has finished copying from it. */
	private record Release(int slot, GpuFence fence) {}

	private final int generation;
	private final int width, height;
	private final SegmentSlotTable table;
	private final FrameSlots slots;
	private final SharedImages images;
	/** GPU sharing: every slot's shared image as a texture of this game's own backend; null when copying. */
	private final GpuTexture[] shared;
	/** Copying: the guest's pixel file; null when sharing. */
	private final PixelFile pixels;
	private final GpuTexture hostTexture;
	private final GpuTextureView hostView;
	private final ArrayDeque<Release> releases = new ArrayDeque<>();
	private long lastFrame;
	private boolean pulled;
	private final boolean rowsTopDown;
	/** E2E: the pause halfway through reading a copied frame, and the buffer it is read into (see {@link #slowRead}). */
	private final int slowCopyMs = FrameHooks.slowCopyMs();
	private ByteBuffer slowCopy;

	private GuestImport(ControlMessage.FrameSlotsReady ready, SegmentSlotTable table, SharedImages images,
			GpuTexture[] shared, PixelFile pixels, GpuTexture hostTexture) {
		this.generation = ready.generation();
		this.width = ready.width();
		this.height = ready.height();
		this.table = table;
		this.slots = new FrameSlots(table);
		this.images = images;
		this.shared = shared;
		this.pixels = pixels;
		this.hostTexture = hostTexture;
		this.hostView = RenderSystem.getDevice().createTextureView(hostTexture);
		// The copy keeps the rows as the guest stored them; a copy-transport file holds no description (bottom-up).
		this.rowsTopDown = table.description().map(ImageDescription::rowsTopDown).orElse(false);
	}

	/**
	 * Imports a generation's slots: GPU-shared ones by name through {@code images}, or the copy transport's pixel file.
	 * Takes over {@code table} on success; on failure the caller still owns it.
	 */
	public static GuestImport open(ControlMessage.FrameSlotsReady ready, SegmentSlotTable table, SharedImages images)
			throws ImportFailed {
		GpuDevice device = RenderSystem.getDevice();
		GpuTexture[] shared = null;
		PixelFile pixels = null;
		GpuTexture hostTexture = null;
		try {
			if (ready.transport() == Transport.GPU_SHARED) {
				if (Boolean.getBoolean("hearthside.e2e.failGpuImport")) {
					throw new ImportFailed("E2E: simulated GPU import failure");
				}
				ImageDescription description = table.description()
						.orElseThrow(() -> new ImportFailed("The guest's slot table has no image description"));
				shared = new GpuTexture[table.slots()];
				for (int slot = 0; slot < shared.length; slot++) {
					String name = FrameNames.gpuMemory(ready.controlFile(), ready.generation(), slot);
					// Never cleared here: it is the guest's own memory (which the guest cleared), and may already hold a frame.
					shared[slot] = images.importSlot(description, new SlotMemory(table.memorySize(slot), table.memoryType(slot),
							new SlotMemory.Source.ByName(name)), "hearthside guest slot " + name);
				}
			} else {
				pixels = PixelFile.open(PixelFile.forControlFile(Path.of(ready.controlFile())), ready.width(), ready.height(),
						table.slots());
			}
			// A render attachment too, only so it can be cleared when created (OpenGL clears through a framebuffer).
			hostTexture = device.createTexture("hearthside guest " + ready.generation(),
					GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING
							| GpuTexture.USAGE_RENDER_ATTACHMENT,
					GpuFormat.RGBA8_UNORM, ready.width(), ready.height(), 1, 1);
			Canvas.startBlack(hostTexture);
			return new GuestImport(ready, table, images, shared, pixels, hostTexture);
		} catch (ImportFailed | RuntimeException e) {
			closeAll(shared);
			if (pixels != null) {
				pixels.close();
			}
			if (hostTexture != null) {
				hostTexture.close();
			}
			if (e instanceof RuntimeException runtime) {
				throw new ImportFailed("Importing " + ready.controlFile() + ": " + runtime, runtime);
			}
			throw (ImportFailed) e;
		}
	}

	/**
	 * Copies the guest's newest frame, if it published one since the last pull, into the host texture. Returns whether
	 * it did. Every copy is recorded on {@code encoder}, ahead of the compose pass that draws from the host texture.
	 */
	public boolean pull(CommandEncoder encoder) {
		OptionalInt newer = slots.acquireNewest(lastFrame);
		if (newer.isEmpty()) {
			return false;
		}
		int slot = newer.getAsInt();
		lastFrame = table.frame(slot);
		if (pixels != null) {
			// Uploaded (into Minecraft's staging memory on Vulkan) before this returns: the slot is free again at once.
			encoder.writeToTexture(hostTexture, slowCopyMs > 0 ? slowRead(slot) : pixels.region(slot), 0, 0, 0, 0, width,
					height);
			slots.release(slot);
		} else {
			GpuTexture source = shared[slot];
			images.beforeUse(source, encoder);
			encoder.copyTextureToTexture(source, hostTexture, 0, 0, 0, 0, 0, width, height);
			images.afterUse(source, encoder);
			releases.addLast(new Release(slot, encoder.createFence()));
		}
		pulled = true;
		return true;
	}

	/**
	 * E2E ({@link FrameHooks#SLOW_COPY_MS}): reads a slot as a slow machine might, into a buffer of our own in two halves
	 * with a pause between, so a guest writing the slot meanwhile would tear what is uploaded.
	 */
	private ByteBuffer slowRead(int slot) {
		ByteBuffer region = pixels.region(slot);
		if (slowCopy == null) {
			slowCopy = ByteBuffer.allocateDirect(region.capacity()).order(ByteOrder.nativeOrder());
		}
		int half = height / 2 * width * 4;
		slowCopy.clear().put(region.slice(0, half));
		FrameHooks.pause(slowCopyMs);
		return slowCopy.put(region.slice(half, region.capacity() - half)).flip();
	}

	/** Whether a frame has reached the host texture yet (a new generation is drawn only from then on). */
	public boolean hasFrame() {
		return pulled;
	}

	/** Gives back, oldest first, every slot whose copy the GPU has finished. Never waits. */
	public void releaseFinished() {
		while (!releases.isEmpty() && releases.peekFirst().fence().awaitCompletion(0)) {
			Release done = releases.removeFirst();
			done.fence().close();
			slots.release(done.slot());
		}
	}

	/** Whether every copy from a slot has finished, so the import can be closed without the GPU still reading it. */
	public boolean idle() {
		releaseFinished();
		return releases.isEmpty();
	}

	public GpuTextureView hostTexture() {
		return hostView;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public int generation() {
		return generation;
	}

	/** Whether the host texture's row 0 is the picture's top (false for every Minecraft source today). */
	public boolean rowsTopDown() {
		return rowsTopDown;
	}

	/** Lets go of everything. Minecraft destroys the textures once the GPU has finished with them. */
	@Override
	public void close() {
		for (Release r : releases) {
			r.fence().close();
		}
		releases.clear();
		hostView.close();
		hostTexture.close();
		closeAll(shared);
		if (pixels != null) {
			pixels.close();
		}
		table.close();
	}

	private static void closeAll(GpuTexture[] textures) {
		if (textures != null) {
			for (GpuTexture texture : textures) {
				if (texture != null) {
					texture.close();
				}
			}
		}
	}
}
