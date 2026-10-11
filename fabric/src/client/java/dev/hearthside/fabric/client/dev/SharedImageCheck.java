package dev.hearthside.fabric.client.dev;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.hearthside.core.compositor.ImageDescription;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.compositor.backend.ExportDevice;
import dev.hearthside.fabric.client.compositor.backend.ExportedSlot;
import dev.hearthside.fabric.client.compositor.backend.ImportFailed;
import dev.hearthside.fabric.client.compositor.backend.SharedImages;
import dev.hearthside.fabric.client.compositor.backend.SlotMemory;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.joml.Vector4f;

/**
 * E2E step X1's round trip (Spec C section 3.5): one image exported by this process's {@link ExportDevice} and imported
 * twice by name through this game's backend, as the host imports a guest's slot. Magenta cleared through one import
 * must read back through the other. Logs its result; the E2E driver checks the lines.
 */
public final class SharedImageCheck {
	private static final int SIZE = 64;

	private SharedImageCheck() {}

	/** Starts the check on the render thread; the readback finishes a frame or two later. */
	public static void start() {
		List<AutoCloseable> open = new ArrayList<>();
		try {
			SharedImages images = SharedImages.forThisGame();
			ImageDescription description = images.describe(SIZE, SIZE);
			String name = "hearthside-x1-" + ProcessHandle.current().pid();
			ExportedSlot slot = ExportDevice.forGpu(images.facts().deviceUuid()).export(description, name);
			open.add(slot);
			SlotMemory memory = slot.byName(name);
			GpuTexture sideA = images.importSlot(description, memory, "hearthside x1 side A");
			open.add(sideA);
			GpuTexture sideB = images.importSlot(description, memory, "hearthside x1 side B");
			open.add(sideB);

			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			images.beforeUse(sideA, encoder);
			encoder.clearColorTexture(sideA, new Vector4f(1, 0, 1, 1));
			images.afterUse(sideA, encoder);
			encoder.submit();

			refusesAMismatch(images, description, memory);

			GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "hearthside x1 readback",
					GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) SIZE * SIZE * 4);
			open.add(buffer);
			String backend = RenderSystem.getDevice().getDeviceInfo().backendName().toLowerCase(Locale.ROOT);
			images.beforeUse(sideB, encoder);
			encoder.copyTextureToBuffer(sideB, buffer, 0L, () -> {
				try (GpuBufferSlice.MappedView read = buffer.map(true, false)) {
					ByteBuffer pixels = read.data();
					HearthsideMod.LOGGER.info("Hearthside shared image round trip backend={} rgb={}", backend,
							String.format("%02x%02x%02x", pixels.get(0) & 0xff, pixels.get(1) & 0xff, pixels.get(2) & 0xff));
				} catch (RuntimeException e) {
					HearthsideMod.LOGGER.error("Hearthside shared image check failed", e);
				} finally {
					closeAll(open);
				}
			}, 0);
			images.afterUse(sideB, encoder);
		} catch (Exception e) {
			HearthsideMod.LOGGER.error("Hearthside shared image check failed", e);
			closeAll(open);
		}
	}

	/** An image that differs from this game's only in its usage bits (Review Focus 4) is refused, not imported. */
	private static void refusesAMismatch(SharedImages images, ImageDescription d, SlotMemory memory) {
		ImageDescription other = new ImageDescription(d.vkFormat(), d.width(), d.height(), d.tiling(), d.createFlags(),
				d.usage() + 1, d.rowsTopDown(), d.deviceUuid(), d.driverUuid());
		try {
			images.importSlot(other, memory, "hearthside x1 mismatch").close();
			throw new IllegalStateException("A mismatched image was imported");
		} catch (ImportFailed expected) {
			HearthsideMod.LOGGER.info("Hearthside shared image mismatch refused: {}", expected.getMessage());
		}
	}

	/** Imports first, then the export: the textures go before the memory they were made from. */
	private static void closeAll(List<AutoCloseable> open) {
		for (AutoCloseable closeable : open.reversed()) {
			try {
				closeable.close();
			} catch (Exception e) {
				HearthsideMod.LOGGER.error("Hearthside shared image check failed closing {}", closeable, e);
			}
		}
	}
}
