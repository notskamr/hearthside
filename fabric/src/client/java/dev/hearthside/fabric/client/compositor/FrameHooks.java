package dev.hearthside.fabric.client.compositor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.util.Map;
import org.joml.Vector4f;

/**
 * E2E stress hooks for the frame hand-over (Spec D section 3.2). Each is a host system property that Party passes to the
 * guest games it launches as an environment variable (satellites get no {@code -Dhearthside.*}), so one accessor reads
 * whichever this process has. The host reads them when it creates what they affect, so a guest that rejoins after the
 * E2E changes them gets them on both sides.
 */
public final class FrameHooks {
	/** Guest: exported frames are solid {@link #SOLID_A} and {@link #SOLID_B} alternately, with this alpha (1–255). */
	public static final String SOLID_FRAMES = "hearthside.e2e.solidFrames";
	/** Both sides: the copy transport pauses this many ms halfway through writing (guest) or reading (host) a frame. */
	public static final String SLOW_COPY_MS = "hearthside.e2e.slowCopyMs";
	/** Both sides: every texture Hearthside creates starts as {@link #POISON}, as Intel's drivers can leave it. */
	public static final String POISON_TEXTURES = "hearthside.e2e.poisonTextures";
	/** Host only: judge every steady guest frame (see {@code CanvasReadback}), with the overlay left out. */
	public static final String TEAR_CHECK = "hearthside.e2e.tearCheck";
	/** Each hook's environment variable in a guest's game. */
	public static final Map<String, String> GUEST_ENV = Map.of(
			SOLID_FRAMES, "HEARTHSIDE_E2E_SOLID_FRAMES",
			SLOW_COPY_MS, "HEARTHSIDE_E2E_SLOW_COPY_MS",
			POISON_TEXTURES, "HEARTHSIDE_E2E_POISON_TEXTURES");

	public static final int SOLID_A = 0x33CC33, SOLID_B = 0x3333CC, POISON = 0x7F1010;

	private FrameHooks() {}

	/** The solid frames' alpha, 1–255, or 0 when frames are the game's own. */
	public static int solidFramesAlpha() {
		return Math.clamp(number(SOLID_FRAMES), 0, 255);
	}

	public static int slowCopyMs() {
		return Math.max(0, number(SLOW_COPY_MS));
	}

	public static boolean tearCheck() {
		return Boolean.getBoolean(TEAR_CHECK);
	}

	/** E2E: fills a texture just created with {@link #POISON}, standing in for memory nobody cleared. */
	static void poison(GpuTexture texture) {
		if (Boolean.parseBoolean(value(POISON_TEXTURES))) {
			RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, colour(POISON, 255));
		}
	}

	/** {@code 0xRRGGBB} with {@code alpha} (0–255) as a clear colour. */
	static Vector4f colour(int rgb, int alpha) {
		return new Vector4f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha / 255f);
	}

	/** A delay for E2E only; an interrupt just ends it early. */
	static void pause(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static String value(String property) {
		String value = System.getProperty(property);
		return value != null ? value : System.getenv(GUEST_ENV.get(property));
	}

	private static int number(String property) {
		String value = value(property);
		if (value == null) {
			return 0;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
