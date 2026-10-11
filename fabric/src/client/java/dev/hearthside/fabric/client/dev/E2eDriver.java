package dev.hearthside.fabric.client.dev;

import dev.hearthside.core.layout.ConsoleLayout;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.TwoPlayerSplit;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.host.HostSession;
import dev.hearthside.fabric.client.window.WindowPlacer;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

/**
 * Developer-only end-to-end check of the walking skeleton, enabled with {@code -Dhearthside.e2e=true}.
 * It drives a real host through the Plan 2 checklist and writes {@code hearthside-e2e-report.txt}
 * in the game folder. The final "host killed" step is performed from outside the game.
 */
public final class E2eDriver {
	private static final String WORLD = "hearthside-e2e";
	private static final String GUEST = "Guest2";

	private final Minecraft minecraft;
	private final Path report;

	private E2eDriver(Minecraft minecraft) {
		this.minecraft = minecraft;
		this.report = minecraft.gameDirectory.toPath().resolve("hearthside-e2e-report.txt");
	}

	public static void registerIfEnabled() {
		if (!Boolean.getBoolean("hearthside.e2e")) {
			return;
		}
		ClientLifecycleEvents.CLIENT_STARTED.register(minecraft ->
				Thread.ofPlatform().daemon().name("hearthside-e2e").start(() -> new E2eDriver(minecraft).run()));
	}

	private void run() {
		try {
			Files.writeString(report, "Hearthside walking-skeleton E2E\n", StandardCharsets.UTF_8);
			// A fresh game folder may show first-launch screens instead of the title; any settled screen will do.
			waitFor("resources loaded", Duration.ofSeconds(180),
					() -> onRender(() -> minecraft.gui.overlay() == null && minecraft.gui.screen() != null));
			log("   first screen: " + onRender(() -> minecraft.gui.screen().getClass().getSimpleName()));

			openWorld();
			waitFor("host session", Duration.ofSeconds(180), () -> HostSession.current() != null);
			step("1 guest joins", this::guestJoins);
			step("2 satellite loads Sodium and Iris", this::satelliteHasCompatMods);
			step("3 crashed satellite is restarted", this::crashedSatelliteRestarts);
			step("4 guest named like the host is refused", this::hostNameRefused);
			step("5 leave closes the satellite", this::leaveClosesSatellite);
			step("6 closing the world closes satellites", this::closingWorldClosesSatellites);

			openWorld();
			waitFor("host session", Duration.ofSeconds(180), () -> HostSession.current() != null);
			onRender(() -> {
				HostSession.current().join(GUEST);
				return null;
			});
			waitFor("guest rejoin for host-kill step", Duration.ofSeconds(180), () -> playerCount() == 2);
			ProcessHandle satellite = satellites().getFirst();
			log("READY_FOR_HOST_KILL host=" + ProcessHandle.current().pid() + " satellite=" + satellite.pid());
		} catch (Throwable t) {
			log("ABORTED " + t);
			HearthsideMod.LOGGER.error("E2E driver aborted", t);
		}
	}

	private void guestJoins() throws Exception {
		onRender(() -> {
			HostSession.current().join(GUEST);
			return null;
		});
		waitFor("2 players", Duration.ofSeconds(180), () -> playerCount() == 2);
		// The host window should now fill the left half of the primary display's usable area.
		Rect expected = new ConsoleLayout(TwoPlayerSplit.VERTICAL).layout(2, onRender(WindowPlacer::primaryWorkArea)).get(0);
		Rect actual = onRender(this::hostWindowRect);
		log("   host window " + actual + ", expected " + expected);
		check(actual.equals(expected), "host window tiled into the left half");
	}

	private Rect hostWindowRect() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer x = stack.mallocInt(1), y = stack.mallocInt(1), w = stack.mallocInt(1), h = stack.mallocInt(1);
			long handle = minecraft.getWindow().handle();
			SDLVideo.SDL_GetWindowPosition(handle, x, y);
			SDLVideo.SDL_GetWindowSize(handle, w, h);
			return new Rect(x.get(0), y.get(0), w.get(0), h.get(0));
		}
	}

	private void satelliteHasCompatMods() throws IOException {
		Path log = minecraft.gameDirectory.toPath().resolve("hearthside/satellite-1/logs/latest.log");
		String text = Files.readString(log).toLowerCase(Locale.ROOT);
		check(text.contains("hearthside client starting as satellite"), "satellite role line");
		check(text.contains("sodium"), "sodium loaded");
		check(text.contains("iris"), "iris loaded");
	}

	private void crashedSatelliteRestarts() throws Exception {
		satellites().forEach(ProcessHandle::destroyForcibly);
		waitFor("guest dropped", Duration.ofSeconds(30), () -> playerCount() == 1);
		waitFor("guest back", Duration.ofSeconds(180), () -> playerCount() == 2);
	}

	private void hostNameRefused() throws Exception {
		String hostName = minecraft.getUser().getName();
		try {
			onRender(() -> {
				HostSession.current().join(hostName);
				return null;
			});
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError("joining as the host's name (" + hostName + ") was accepted");
	}

	private void leaveClosesSatellite() throws Exception {
		onRender(() -> {
			HostSession.current().leave(GUEST);
			return null;
		});
		waitFor("1 player", Duration.ofSeconds(30), () -> playerCount() == 1);
		waitFor("satellite exit", Duration.ofSeconds(15), () -> satellites().isEmpty());
	}

	private void closingWorldClosesSatellites() throws Exception {
		onRender(() -> {
			HostSession.current().join(GUEST);
			return null;
		});
		waitFor("2 players", Duration.ofSeconds(180), () -> playerCount() == 2);
		long start = System.nanoTime();
		onRender(() -> {
			minecraft.disconnectFromWorld(Component.literal("E2E"));
			return null;
		});
		waitFor("satellites exit after world close", Duration.ofSeconds(15), () -> satellites().isEmpty());
		log("   satellites gone " + Duration.ofNanos(System.nanoTime() - start).toMillis() + " ms after world close");
		waitFor("back on a menu", Duration.ofSeconds(60),
				() -> onRender(() -> minecraft.level == null && minecraft.getSingleplayerServer() == null));
	}

	private void openWorld() throws Exception {
		onRender(() -> {
			if (minecraft.getLevelSource().levelExists(WORLD)) {
				minecraft.createWorldOpenFlows().openWorld(WORLD, () -> minecraft.gui.setScreen(new TitleScreen()));
			} else {
				LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT,
						true, WorldDataConfiguration.DEFAULT);
				minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, SelectWorldScreen.TEST_OPTIONS,
						WorldPresets::createNormalWorldDimensions, new TitleScreen());
			}
			return null;
		});
	}

	private int playerCount() {
		IntegratedServer server = minecraft.getSingleplayerServer();
		return server == null ? 0 : server.getPlayerList().getPlayerCount();
	}

	private static List<ProcessHandle> satellites() {
		return ProcessHandle.current().children()
				.filter(p -> p.info().command().map(c -> c.toLowerCase(Locale.ROOT).contains("java")).orElse(false))
				.toList();
	}

	private void step(String name, ThrowingRunnable body) {
		long start = System.nanoTime();
		try {
			body.run();
			log("PASS " + name + " (" + Duration.ofNanos(System.nanoTime() - start).toSeconds() + "s)");
		} catch (Throwable t) {
			log("FAIL " + name + ": " + t);
			HearthsideMod.LOGGER.error("E2E step failed: {}", name, t);
		}
	}

	private <T> T onRender(Callable<T> task) throws Exception {
		try {
			return minecraft.submit(() -> {
				try {
					return task.call();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}).join();
		} catch (java.util.concurrent.CompletionException e) {
			Throwable cause = e.getCause() instanceof RuntimeException r && r.getCause() != null ? r.getCause() : e.getCause();
			if (cause instanceof Exception ex) {
				throw ex;
			}
			throw e;
		}
	}

	private static void waitFor(String what, Duration timeout, ThrowingBooleanSupplier condition) throws Exception {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("timed out after " + timeout.toSeconds() + "s waiting for " + what);
			}
			Thread.sleep(250);
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("missing: " + what);
		}
	}

	private void log(String line) {
		HearthsideMod.LOGGER.info("[E2E] {}", line);
		try {
			Files.writeString(report, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		} catch (IOException e) {
			HearthsideMod.LOGGER.warn("Could not write E2E report", e);
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	@FunctionalInterface
	private interface ThrowingBooleanSupplier {
		boolean getAsBoolean() throws Exception;
	}
}
