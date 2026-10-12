package dev.hearthside.fabric.client.compositor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.ViewFrame;
import dev.hearthside.fabric.HearthsideMod;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * What the host learns from its own canvas (Spec C section 4.4): E2E probes, captures and gap sampling, all read back
 * from the canvas with {@code copyTextureToBuffer}, so they work alike on both backends; and the composition cost log.
 * Readbacks are asynchronous: each result is logged (or saved) from its callback a frame or two later. Render thread.
 */
final class CanvasReadback {
	private static final long PROBE_INTERVAL_NANOS = 1_000_000_000L;
	/** {@code Compositor}'s divider colour: a view's centre showing exactly this is a gap. */
	private static final int DIVIDER_RGB = 0x120c08;

	/** A guest's view as drawn this frame: the pixels just inside its top-left and bottom-left corners are probed. */
	record GuestView(int player, int generation, Rect rect) {}

	private final boolean probe = "1".equals(System.getenv("HEARTHSIDE_E2E_PATTERN"));
	private long lastProbe;
	private final Map<Integer, Long> lastGapLog = new HashMap<>();
	// Composition cost, logged as percentiles every 30 s.
	private final long[] costs = new long[4096];
	private int costCount;
	private long costWindowStart = System.nanoTime();

	/** After the compose pass: queues this frame's capture, probes and gap samples, as the E2E switches ask. */
	void afterCompose(CommandEncoder encoder, Canvas canvas, List<GuestView> guests, List<ViewFrame> frames) {
		Path capture = Compositor.e2eCapture;
		if (capture != null) {
			Compositor.e2eCapture = null;
			capture(encoder, canvas, capture);
		}
		if (probe) {
			probeGuests(encoder, canvas, guests);
		}
		if (Compositor.e2eGapSampling) {
			sampleGaps(encoder, canvas, frames);
		}
	}

	/** E2E: saves the canvas as drawn this frame (every view and the overlay): what the players see. */
	private static void capture(CommandEncoder encoder, Canvas canvas, Path file) {
		int width = canvas.width(), height = canvas.height();
		GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "hearthside capture",
				GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * 4);
		encoder.copyTextureToBuffer(canvas.texture(), buffer, 0, () -> {
			try (buffer; GpuBufferSlice.MappedView read = buffer.map(true, false)) {
				ByteBuffer pixels = read.data();
				var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						int i = ((height - 1 - y) * width + x) * 4; // the canvas's rows run bottom-up
						image.setRGB(x, y, (pixels.get(i) & 0xFF) << 16 | (pixels.get(i + 1) & 0xFF) << 8 | pixels.get(i + 2) & 0xFF);
					}
				}
				Files.createDirectories(file.toAbsolutePath().getParent());
				// Written aside, then moved: whoever waits for the file never sees half of it.
				Path partial = file.resolveSibling(file.getFileName() + ".part");
				ImageIO.write(image, "png", partial.toFile());
				Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException | RuntimeException e) {
				HearthsideMod.LOGGER.warn("Could not save the window capture {}", file, e);
			}
		}, 0);
	}

	/**
	 * E2E: logs, once a second, the composited colour just inside each guest view's top-left corner and its bottom-left
	 * corner (4 px in from each edge), and at the overlay's probe point if one is set.
	 */
	private void probeGuests(CommandEncoder encoder, Canvas canvas, List<GuestView> guests) {
		long now = System.nanoTime();
		if (now - lastProbe < PROBE_INTERVAL_NANOS) {
			return;
		}
		lastProbe = now;
		List<int[]> points = new ArrayList<>();
		for (GuestView guest : guests) {
			Rect r = guest.rect();
			points.add(new int[] {r.x() + 4, r.y() + 4});
			points.add(new int[] {r.x() + 4, r.y() + r.height() - 1 - 4});
		}
		int[] point = Compositor.e2eProbePoint;
		if (point != null) {
			points.add(point);
		}
		readPixels(encoder, canvas, points, rgb -> {
			for (int i = 0; i < guests.size(); i++) {
				GuestView guest = guests.get(i);
				if (rgb[2 * i] >= 0 && rgb[2 * i + 1] >= 0) {
					HearthsideMod.LOGGER.info("Hearthside compositor probe player={} gen={} rgb={} corner={}", guest.player(),
							guest.generation(), hex(rgb[2 * i]), hex(rgb[2 * i + 1]));
				}
			}
			if (point != null && rgb[rgb.length - 1] >= 0) {
				HearthsideMod.LOGGER.info("Hearthside overlay probe rgb={}", hex(rgb[rgb.length - 1]));
			}
		});
	}

	/**
	 * E2E: reads the centre of every settled view this frame; the bare divider colour there is a gap (logged at most
	 * 4×/s per seat). A view fading in or out shows the divider through it on purpose: its alpha is logged instead.
	 */
	private void sampleGaps(CommandEncoder encoder, Canvas canvas, List<ViewFrame> frames) {
		List<Integer> seats = new ArrayList<>();
		List<int[]> points = new ArrayList<>();
		for (ViewFrame view : frames) {
			if (view.alpha() < 1f) {
				HearthsideMod.LOGGER.info("Hearthside compositor fade seat={} alpha={}", view.seat(),
						String.format(Locale.ROOT, "%.2f", view.alpha()));
				continue;
			}
			Rect r = view.rect();
			seats.add(view.seat());
			points.add(new int[] {r.x() + r.width() / 2, r.y() + r.height() / 2});
		}
		readPixels(encoder, canvas, points, rgb -> {
			long now = System.nanoTime();
			for (int i = 0; i < rgb.length; i++) {
				int seat = seats.get(i);
				if (rgb[i] == DIVIDER_RGB && now - lastGapLog.getOrDefault(seat, 0L) > 250_000_000L) {
					lastGapLog.put(seat, now);
					HearthsideMod.LOGGER.info("Hearthside compositor gap player={}", seat + 1);
				}
			}
		});
	}

	/**
	 * Reads one pixel per point (window pixels from the top-left) into one buffer and hands their colours, as 0xRRGGBB
	 * in the same order, to {@code done} once the last copy has landed. A point off the canvas reads as -1.
	 */
	private static void readPixels(CommandEncoder encoder, Canvas canvas, List<int[]> points, Consumer<int[]> done) {
		if (points.isEmpty()) {
			return;
		}
		int width = canvas.width(), height = canvas.height();
		GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "hearthside probe",
				GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) points.size() * 4);
		boolean[] inside = new boolean[points.size()];
		int[] pending = {0};
		for (int i = 0; i < points.size(); i++) {
			int x = points.get(i)[0], y = points.get(i)[1];
			inside[i] = x >= 0 && y >= 0 && x < width && y < height;
			pending[0] += inside[i] ? 1 : 0;
		}
		if (pending[0] == 0) {
			buffer.close();
			return;
		}
		Runnable landed = () -> {
			if (--pending[0] > 0) {
				return;
			}
			int[] rgb = new int[points.size()];
			Arrays.fill(rgb, -1);
			try (buffer; GpuBufferSlice.MappedView read = buffer.map(true, false)) {
				ByteBuffer pixels = read.data();
				for (int i = 0; i < rgb.length; i++) {
					if (inside[i]) {
						rgb[i] = (pixels.get(i * 4) & 0xFF) << 16 | (pixels.get(i * 4 + 1) & 0xFF) << 8 | pixels.get(i * 4 + 2) & 0xFF;
					}
				}
				done.accept(rgb);
			} catch (RuntimeException e) {
				// A probe never takes the game down; the E2E step waiting for its line fails instead.
				HearthsideMod.LOGGER.warn("Hearthside canvas readback failed", e);
			}
		};
		for (int i = 0; i < points.size(); i++) {
			if (inside[i]) {
				// The canvas's rows run bottom-up on both backends.
				encoder.copyTextureToBuffer(canvas.texture(), buffer, i * 4L, landed, 0, points.get(i)[0],
						height - 1 - points.get(i)[1], 1, 1);
			}
		}
	}

	private static String hex(int rgb) {
		return String.format("%06x", rgb);
	}

	/** Adds one frame's composition time; every 30 s logs the percentiles for the guests drawn now. */
	void recordCost(long nanos, int guests) {
		if (costCount < costs.length) {
			costs[costCount++] = nanos;
		}
		long now = System.nanoTime();
		if (now - costWindowStart < 30_000_000_000L || costCount == 0) {
			return;
		}
		long[] sorted = Arrays.copyOf(costs, costCount);
		Arrays.sort(sorted);
		HearthsideMod.LOGGER.info("Hearthside compositor cost guests={} p50={} ms p95={} ms frames={}", guests,
				String.format("%.3f", sorted[sorted.length / 2] / 1e6), String.format("%.3f", sorted[(int) (sorted.length * 0.95)] / 1e6),
				costCount);
		costCount = 0;
		costWindowStart = now;
	}
}
