package dev.hearthside.fabric.client.compositor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuFence;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.hearthside.core.channel.ControlConnection;
import dev.hearthside.core.compositor.FrameNames;
import dev.hearthside.core.compositor.FrameSlots;
import dev.hearthside.core.compositor.ImageDescription;
import dev.hearthside.core.compositor.SegmentSlotTable;
import dev.hearthside.core.compositor.SharingFacts;
import dev.hearthside.core.compositor.Transport;
import dev.hearthside.core.compositor.TransportChoice;
import dev.hearthside.core.compositor.ViewportMath;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.core.session.InWorldSignal;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.compositor.backend.ExportDevice;
import dev.hearthside.fabric.client.compositor.backend.ExportedSlot;
import dev.hearthside.fabric.client.compositor.backend.ImportFailed;
import dev.hearthside.fabric.client.compositor.backend.SharedImages;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;

/**
 * Satellite side of the compositor (Spec C sections 3.4 and 4.1): renders at the viewport's size and hands every
 * finished frame to the host through shared frame slots instead of showing it in a window, through Minecraft's GPU API,
 * so it works on either backend. Render thread only, except {@link #install}.
 */
public final class FrameExporter {
	private static final int PATTERN_SIZE = 8;
	/** E2E: the 8×8 magenta square painted into each exported frame's top-left corner. */
	private static final ByteBuffer MAGENTA = magenta();
	private static final long FPS_INTERVAL_NANOS = 5_000_000_000L;
	private static volatile FrameExporter instance;

	private final Minecraft minecraft;
	private final ControlConnection connection;
	private final Path dir;
	private final boolean pattern = "1".equals(System.getenv("HEARTHSIDE_E2E_PATTERN"));
	/** E2E ({@link FrameHooks#SOLID_FRAMES}): the solid frames' alpha, or 0. */
	private final int solidAlpha = FrameHooks.solidFramesAlpha();
	private final InWorldSignal world = new InWorldSignal();
	/** When this game last told the host it was in the world (0 once checked). */
	private long liveSince;
	/** It left its world: no more frames, so the host fades out its last picture from the world. */
	private boolean leftWorld;
	private int generation = FrameNames.firstGeneration() - 1;
	/** The generation being written; null before the first viewport, or when it could not be allocated. */
	private Frames current;
	/** The generation before the current one, kept until the next reallocation (see {@link #retireCurrent}). */
	private Frames previous;
	private int previousGeneration = -1;
	private boolean copyOnly;
	private boolean probeLogged;
	private long frame;
	// Frames exported, logged as a rate every 5 s, and frames dropped because the host held every slot.
	private int exportedFrames;
	private int droppedFrames;
	private long fpsWindowStart = System.nanoTime();

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
		reallocate(rect.width(), rect.height(), false);
	}

	/**
	 * Draws Minecraft's finished frame ({@code mainColor}) into the next free slot. Called at Minecraft's blit to the
	 * window, which a satellite skips ({@code SatelliteFrameMixin}); Minecraft submits the frame's work right after.
	 */
	public void export(GpuTextureView mainColor) {
		logRate();
		if (current != null) {
			current.publishFinished();
		}
		// A server reconfiguring this player drops Minecraft's player (its connection reads as gone) behind this screen.
		boolean reconfiguring = minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
		if (world.observeLeft(minecraft.getConnection() != null, reconfiguring)) {
			HearthsideMod.LOGGER.info("Hearthside left the world");
			tell(new ControlMessage.LeftWorld());
			leftWorld = true;
		}
		if (current == null || leftWorld || (world.inWorld() && minecraft.level == null)) {
			// Leaving its world (or the server reconfiguring it): the host keeps its last picture from the world, never
			// the disconnecting and title screens in between.
			return;
		}
		OptionalInt claimed = current.slots.beginWrite();
		if (claimed.isEmpty()) {
			droppedFrames++;
			return; // the host still holds every slot; drop this frame
		}
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		if (solidAlpha > 0) {
			// E2E: the whole frame in one colour, the other one next frame, through the real export path.
			encoder.clearColorTexture(mainColor.texture(),
					FrameHooks.colour(frame % 2 == 0 ? FrameHooks.SOLID_A : FrameHooks.SOLID_B, solidAlpha));
		}
		current.draw(claimed.getAsInt(), mainColor, encoder, ++frame);
		exportedFrames++;
		observeWorld();
	}

	private void logRate() {
		long now = System.nanoTime();
		if (now - fpsWindowStart >= FPS_INTERVAL_NANOS) {
			HearthsideMod.LOGGER.info("Hearthside exporter fps={} dropped={}",
					Math.round(exportedFrames * 1e9 / (now - fpsWindowStart)), droppedFrames);
			exportedFrames = 0;
			droppedFrames = 0;
			fpsWindowStart = now;
		}
	}

	/** JoinWorld arrived: the first frame exported in the world, past its loading screens, tells the host. */
	public void joinRequested() {
		world.joinRequested();
		leftWorld = false;
	}

	private void observeWorld() {
		// In the world means nothing covers it: no screen, and not Minecraft's loading overlay (a game that has just
		// started may still be finishing its first resource load, drawn over everything as the Mojang screen).
		boolean covered = minecraft.gui.screen() != null || minecraft.gui.overlay() != null;
		if (world.observe(minecraft.level != null && minecraft.player != null, covered, true)) {
			HearthsideMod.LOGGER.info("Hearthside in world");
			tell(new ControlMessage.InWorld());
			liveSince = System.nanoTime();
		}
		if (liveSince != 0 && System.nanoTime() - liveSince < 5_000_000_000L && minecraft.gui.overlay() != null) {
			HearthsideMod.LOGGER.warn("Hearthside loading overlay over a live view");
			liveSince = 0;
		}
	}

	private void tell(ControlMessage message) {
		try {
			connection.send(message);
		} catch (IOException e) {
			HearthsideMod.LOGGER.warn("Could not tell the host {}: {}", message, e.toString());
		}
	}

	/** The host could not use our shared GPU frames: copy them from now on. Render thread. */
	public void useCopy() {
		copyOnly = true;
		if (current != null) {
			reallocate(current.width, current.height, true);
		}
	}

	/** GPU sharing when this game can both export and import shared memory (Spec C section 3.7, step 2). */
	private Transport transportChoice(SharingFacts facts) {
		if (!probeLogged) {
			// Once per process: the facts are the backend's, read when it started.
			probeLogged = true;
			HearthsideMod.LOGGER.info("Hearthside GPU sharing probe: {} -> {}", facts, TransportChoice.decide(facts, false));
		}
		return TransportChoice.decide(facts, copyOnly || Presentation.forceCopy()).transport();
	}

	private void reallocate(int width, int height, boolean force) {
		if (!force && current != null && current.width == width && current.height == height) {
			return;
		}
		retireCurrent();
		generation++;
		Path controlFile = dir.resolve("frames-" + generation + ".slots");
		SegmentSlotTable table;
		try {
			table = SegmentSlotTable.create(controlFile, FrameSlots.DEFAULT_SLOTS, generation);
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not create the frame slot table at {}", controlFile, e);
			return;
		}
		SharedImages images = SharedImages.forThisGame();
		Transport choice = transportChoice(images.facts());
		if (choice == Transport.GPU_SHARED) {
			try {
				current = SharedFrames.allocate(images, controlFile, table, width, height, pattern);
			} catch (ImportFailed | RuntimeException | LinkageError e) {
				// A LinkageError: no Vulkan runtime for the export device (an OpenGL game can still run without one).
				HearthsideMod.LOGGER.warn("GPU frame sharing failed; copying frames instead", e);
				choice = Transport.COPY;
			}
		}
		if (choice == Transport.COPY) {
			try {
				current = new CopiedFrames(controlFile, table, width, height, pattern, FrameHooks.slowCopyMs(),
						this::deleteOldFiles);
			} catch (RuntimeException e) {
				HearthsideMod.LOGGER.error("Could not set up the copy transport at {}", controlFile, e);
				table.close();
				return;
			}
		}
		HearthsideMod.LOGGER.info("Hearthside frame slots ready transport={} {}x{}", choice.name().toLowerCase(Locale.ROOT),
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
	 * Frames still on their way into it are dropped, never published.
	 */
	private void retireCurrent() {
		if (previous != null) {
			previous.close();
		}
		if (current != null) {
			current.retire();
		}
		previous = current;
		previousGeneration = current == null ? -1 : generation;
		current = null;
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
						// the host (or a readback still landing here) still has it open
					}
				}
			}
		} catch (IOException e) {
			HearthsideMod.LOGGER.debug("Could not list old frame files", e);
		}
	}

	/**
	 * Draws {@code source} over the whole of {@code target}, scaled with linear filtering: Minecraft's own blit pair, so
	 * it also converts formats (shader packs may render into float targets). In E2E runs it then paints the picture's
	 * top-left corner magenta, for the host's probe. Textures are stored bottom-up on both backends, so the top-left is
	 * rows {@code height - 8} to {@code height - 1}.
	 */
	private static void drawFrame(CommandEncoder encoder, GpuTextureView source, GpuTexture target, GpuTextureView targetView,
			boolean pattern) {
		try (RenderPass pass = encoder.createRenderPass(() -> "Hearthside export", targetView, Optional.empty())) {
			ViewPipeline.blit(pass, ViewPipeline.PLAIN_BLIT, source, FilterMode.LINEAR);
		}
		int width = target.getWidth(0), height = target.getHeight(0);
		if (pattern && width >= PATTERN_SIZE && height >= PATTERN_SIZE) {
			encoder.writeToTexture(target, MAGENTA.duplicate(), 0, 0, 0, height - PATTERN_SIZE, PATTERN_SIZE, PATTERN_SIZE);
		}
	}

	private static ByteBuffer magenta() {
		ByteBuffer pixels = BufferUtils.createByteBuffer(PATTERN_SIZE * PATTERN_SIZE * 4);
		while (pixels.hasRemaining()) {
			pixels.put((byte) 255).put((byte) 0).put((byte) 255).put((byte) 255);
		}
		return pixels.flip();
	}

	/** One generation of frame slots, on one transport. */
	private abstract static sealed class Frames permits SharedFrames, CopiedFrames {
		final int width, height;
		final SegmentSlotTable table;
		final FrameSlots slots;
		/** No longer written: a frame still on its way into it is dropped. */
		boolean retired;

		Frames(SegmentSlotTable table, int width, int height) {
			this.table = table;
			this.slots = new FrameSlots(table);
			this.width = width;
			this.height = height;
		}

		/** Draws {@code source} into the claimed {@code slot}; it is published once the GPU has finished it. */
		abstract void draw(int slot, GpuTextureView source, CommandEncoder encoder, long frame);

		/** Publishes, oldest first, every frame the GPU has finished. Never waits. */
		abstract void publishFinished();

		/** Stops writing: every frame not yet published is dropped. The slots stay readable by the host. */
		abstract void retire();

		/** Lets go of everything (Minecraft destroys textures and buffers once the GPU has finished with them). */
		abstract void close();
	}

	/**
	 * Zero-copy: each slot is an image the {@link ExportDevice} allocates and exports under a name, imported into this
	 * game by handle through its own backend's {@link SharedImages}; the host imports the same memory by name.
	 */
	private static final class SharedFrames extends Frames {
		private record Pending(int slot, GpuFence fence, long frame) {}

		private final SharedImages images;
		private final ExportedSlot[] exported;
		private final GpuTexture[] textures;
		private final GpuTextureView[] views;
		private final ArrayDeque<Pending> pending = new ArrayDeque<>();
		private final boolean pattern;

		private SharedFrames(SharedImages images, SegmentSlotTable table, int width, int height, boolean pattern) {
			super(table, width, height);
			this.images = images;
			this.exported = new ExportedSlot[table.slots()];
			this.textures = new GpuTexture[table.slots()];
			this.views = new GpuTextureView[table.slots()];
			this.pattern = pattern;
		}

		/**
		 * Allocates and exports {@code table.slots()} images, records each one's size and memory type in the table, then
		 * the images' description. Takes over {@code table} on success; on failure the caller still owns it.
		 */
		static SharedFrames allocate(SharedImages images, Path controlFile, SegmentSlotTable table, int width, int height,
				boolean pattern) throws ImportFailed {
			ImageDescription description = images.describe(width, height);
			ExportDevice device = ExportDevice.forGpu(images.facts().deviceUuid());
			GpuDevice gpu = RenderSystem.getDevice();
			SharedFrames frames = new SharedFrames(images, table, width, height, pattern);
			try {
				for (int slot = 0; slot < table.slots(); slot++) {
					String name = FrameNames.gpuMemory(controlFile.toString(), table.generation(), slot);
					ExportedSlot memory = device.export(description, name);
					frames.exported[slot] = memory;
					frames.textures[slot] = images.importSlot(description, memory.byHandle(), "hearthside slot " + name);
					frames.views[slot] = gpu.createTextureView(frames.textures[slot]);
					table.setMemorySize(slot, memory.allocationSize());
					table.setMemoryType(slot, memory.memoryType());
				}
				table.setDescription(description);
			} catch (ImportFailed | RuntimeException | LinkageError e) {
				frames.closeImages();
				throw e;
			}
			return frames;
		}

		@Override
		void draw(int slot, GpuTextureView source, CommandEncoder encoder, long frame) {
			images.beforeUse(textures[slot], encoder);
			drawFrame(encoder, source, textures[slot], views[slot], pattern);
			images.afterUse(textures[slot], encoder);
			// Signals once the submission Minecraft makes right after this blit (the frame's own) has finished.
			pending.addLast(new Pending(slot, encoder.createFence(), frame));
		}

		@Override
		void publishFinished() {
			while (!pending.isEmpty() && pending.peekFirst().fence().awaitCompletion(0)) {
				Pending done = pending.removeFirst();
				done.fence().close();
				slots.publish(done.slot(), done.frame());
			}
		}

		@Override
		void retire() {
			retired = true;
			for (Pending p : pending) {
				p.fence().close();
				slots.abandon(p.slot());
			}
			pending.clear();
		}

		@Override
		void close() {
			retire();
			closeImages();
			table.close();
		}

		private void closeImages() {
			for (int slot = 0; slot < textures.length; slot++) {
				if (views[slot] != null) {
					views[slot].close();
				}
				if (textures[slot] != null) {
					textures[slot].close();
				}
				if (exported[slot] != null) {
					// Minecraft's import keeps the memory alive until the GPU is done with it.
					exported[slot].close();
				}
			}
			// The export device itself is shared by every export in this process and lives as long as it does.
		}
	}

	/**
	 * The fallback: each frame is drawn into a texture of this game's own, read back into a slot's buffer, and copied
	 * into that slot's region of the shared {@link PixelFile} from the readback's callback, which then publishes the slot.
	 * A readback that lands after the generation was retired drops its frame; the pixel file, buffers and table are only
	 * closed once no readback is still on its way (Review Focus 2).
	 */
	private static final class CopiedFrames extends Frames {
		private final PixelFile pixels;
		private final GpuTexture staging;
		private final GpuTextureView stagingView;
		private final GpuBuffer[] buffers;
		private final boolean pattern;
		/** E2E: the pause halfway through writing a frame into the pixel file ({@link FrameHooks#SLOW_COPY_MS}). */
		private final int slowCopyMs;
		private final Runnable afterClose;
		private int inFlight;
		private boolean closeRequested;
		private boolean closed;

		CopiedFrames(Path controlFile, SegmentSlotTable table, int width, int height, boolean pattern, int slowCopyMs,
				Runnable afterClose) {
			super(table, width, height);
			this.pattern = pattern;
			this.slowCopyMs = slowCopyMs;
			this.afterClose = afterClose;
			GpuDevice device = RenderSystem.getDevice();
			PixelFile file = PixelFile.open(PixelFile.forControlFile(controlFile), width, height, table.slots());
			GpuTexture texture = null;
			GpuBuffer[] readbacks = new GpuBuffer[table.slots()];
			try {
				texture = device.createTexture("hearthside export " + table.generation(),
						GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC
								| GpuTexture.USAGE_COPY_DST, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
				Canvas.startBlack(texture);
				long bytes = (long) width * height * 4;
				for (int slot = 0; slot < readbacks.length; slot++) {
					int index = slot;
					readbacks[slot] = device.createBuffer(() -> "hearthside export readback " + index,
							GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, bytes);
				}
				this.stagingView = device.createTextureView(texture);
			} catch (RuntimeException e) {
				for (GpuBuffer buffer : readbacks) {
					if (buffer != null) {
						buffer.close();
					}
				}
				if (texture != null) {
					texture.close();
				}
				file.close();
				throw e;
			}
			this.pixels = file;
			this.staging = texture;
			this.buffers = readbacks;
		}

		@Override
		void draw(int slot, GpuTextureView source, CommandEncoder encoder, long frame) {
			drawFrame(encoder, source, staging, stagingView, pattern);
			inFlight++;
			// Rows land bottom-up, as the texture stores them: the pixel file's layout.
			encoder.copyTextureToBuffer(staging, buffers[slot], 0, () -> landed(slot, frame), 0);
		}

		/** A readback has landed (render thread, a frame or two later). */
		private void landed(int slot, long frame) {
			inFlight--;
			try {
				if (retired) {
					slots.abandon(slot);
				} else {
					try (GpuBufferSlice.MappedView read = buffers[slot].map(true, false)) {
						ByteBuffer region = pixels.region(slot);
						if (slowCopyMs > 0) {
							// E2E: the first half of the rows, a pause, then the rest, as a slow machine might.
							int half = height / 2 * width * 4;
							region.put(read.data().slice(0, half));
							FrameHooks.pause(slowCopyMs);
							region.put(read.data().slice(half, region.capacity() - half));
						} else {
							region.put(read.data());
						}
					}
					slots.publish(slot, frame);
				}
			} catch (RuntimeException e) {
				HearthsideMod.LOGGER.warn("Hearthside could not hand over a copied frame", e);
				slots.abandon(slot);
			} finally {
				if (closeRequested && inFlight == 0) {
					closeNow();
				}
			}
		}

		@Override
		void publishFinished() {
			// Published from each readback's callback.
		}

		@Override
		void retire() {
			retired = true;
		}

		@Override
		void close() {
			retired = true;
			closeRequested = true;
			if (inFlight == 0) {
				closeNow();
			}
		}

		private void closeNow() {
			if (closed) {
				return;
			}
			closed = true;
			for (GpuBuffer buffer : buffers) {
				buffer.close();
			}
			stagingView.close();
			staging.close();
			pixels.close();
			table.close();
			afterClose.run(); // its files can go now
		}
	}
}
