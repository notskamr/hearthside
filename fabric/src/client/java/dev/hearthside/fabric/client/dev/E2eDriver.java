package dev.hearthside.fabric.client.dev;

import dev.hearthside.core.input.Action;
import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.PadButton;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.layout.ConsoleLayout;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.TwoPlayerSplit;
import dev.hearthside.core.profile.GuestProfile;
import dev.hearthside.core.profile.ProfileStore;
import dev.hearthside.core.profile.SkinRef;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.host.HostSession;
import dev.hearthside.fabric.client.window.WindowPlacer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.Callable;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

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
	private Rect hostWindowBefore;

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
			hostWindowBefore = onRender(() -> WindowPlacer.bounds(minecraft.getWindow()));
			step("1 guest joins", this::guestJoins);
			step("2 satellite loads Sodium and Iris", this::satelliteHasCompatMods);
			step("3 crashed satellite is restarted", this::crashedSatelliteRestarts);
			step("4 guest named like the host is refused", this::hostNameRefused);
			step("4b opening and closing LAN keeps the guest", this::lanToggleKeepsGuest);
			step("5 leave closes the satellite and restores the host window", this::leaveClosesSatellite);
			if (Boolean.getBoolean("hearthside.e2e.fakePads")) {
				step("P0 Player2 profile rebinds JUMP to Y", this::rebindPlayer2Jump);
				step("P1 START on a new controller adds Player2", this::startJoins);
				step("P2 left stick moves the guest", this::stickMoves);
				step("P3 right stick turns the guest", this::stickTurns);
				step("P4 RB advances the guest's hotbar", this::bumperSteps);
				step("P7 rebound Y makes the guest jump", this::reboundJump);
				step("P5 unplug pauses, replug resumes", this::unplugAndReplug);
				step("P6 satellite input-isolation boot line present (behaviour checked by hand)", this::satelliteIgnoresOsInput);
				step("M1 inventory: X takes half a stack, A places it", this::inventoryClicks);
				step("M2 closing a menu with B does not leak sneak into the world", this::closeWithoutSneakLeak);
				step("M3 START opens the pause menu, B closes it", this::pauseMenuOpensAndCloses);
				step("P8 satellite got the PlayStation pad style", this::padStyleReached);
				step("P9 Player2 leaves", this::player2Leaves);
			}
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
		Rect actual = onRender(() -> WindowPlacer.bounds(minecraft.getWindow()));
		log("   host window " + actual + ", expected " + expected);
		check(actual.equals(expected), "host window tiled into the left half");
	}

	private void lanToggleKeepsGuest() throws Exception {
		int lanPort = HttpUtil.getAvailablePort();
		check(onRender(() -> minecraft.getSingleplayerServer().publishServer(MinecraftServer.MultiplayerScope.LAN, lanPort)),
				"world opened to LAN");
		check(onRender(() -> minecraft.getSingleplayerServer().unpublishServer()), "world closed to LAN");
		Thread.sleep(3000);
		check(playerCount() == 2, "guest still in the world after Stop Sharing");
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
		Rect after = onRender(() -> WindowPlacer.bounds(minecraft.getWindow()));
		log("   host window " + after + ", before split-screen " + hostWindowBefore);
		check(after.equals(hostWindowBefore), "host window restored");
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

	// ---- Controller steps (scripted pads) ----

	private static final String PAD_GUID = "fake-dualsense";
	private static final FakeGamepadSource PADS = FakeGamepadSource.INSTANCE;

	private void rebindPlayer2Jump() throws Exception {
		ProfileStore store = new ProfileStore(minecraft.gameDirectory.toPath().resolve("hearthside/profiles.json"));
		List<GuestProfile> profiles = new ArrayList<>(store.load());
		profiles.removeIf(p -> p.name().equalsIgnoreCase("Player2"));
		profiles.add(new GuestProfile(UUID.randomUUID(), "Player2", SkinRef.defaultSkin("alex"), null,
				ControlScheme.BEDROCK.withBinding(Action.JUMP, Control.Y)));
		store.save(profiles);
	}

	private void startJoins() throws Exception {
		PADS.plug(101, PAD_GUID, PadStyle.PLAYSTATION);
		Thread.sleep(300);
		press(101, PadButton.START);
		waitFor("Player2 in the world", Duration.ofSeconds(180), () -> guest("Player2") != null);
		// The server counts the guest as joined while its client still shows the terrain-loading
		// screen; controller input only drives the player once that screen is gone.
		waitFor("satellite in world mode", Duration.ofSeconds(60),
				() -> satelliteLog().contains("hearthside controller: world mode"));
	}

	/** A 21×21 barrier platform high above the terrain, so walking tests never hit a wall. */
	private void placeGuestOnTestPlatform() throws Exception {
		IntegratedServer server = minecraft.getSingleplayerServer();
		server.submit(() -> {
			var source = server.createCommandSourceStack().withSuppressedOutput();
			server.getCommands().performPrefixedCommand(source, "fill -10 249 -10 10 249 10 minecraft:barrier");
			server.getCommands().performPrefixedCommand(source, "tp Player2 0.5 250 0.5 0 0");
		}).join();
		Thread.sleep(1000);
	}

	private void stickMoves() throws Exception {
		placeGuestOnTestPlatform();
		ServerPlayer guest = guest("Player2");
		Vec3 start = guest.position();
		PADS.set(101, new InputFrame(0, 0, 0, -1, 0, 0, 0, 0));
		Thread.sleep(2000);
		PADS.set(101, InputFrame.NEUTRAL);
		double moved = guest.position().distanceTo(start);
		log("   guest moved " + String.format("%.2f", moved) + " blocks");
		check(moved > 1, "guest moved more than one block");
	}

	private void stickTurns() throws Exception {
		ServerPlayer guest = guest("Player2");
		float yaw = guest.getYRot();
		// 0.4 s at full deflection is ~120°: under 180°, so the wrapped difference is unambiguous.
		PADS.set(101, new InputFrame(0, 0, 0, 0, 1, 0, 0, 0));
		Thread.sleep(400);
		PADS.set(101, InputFrame.NEUTRAL);
		Thread.sleep(300);
		float turned = Math.abs(Mth.wrapDegrees(guest.getYRot() - yaw));
		log("   guest turned " + String.format("%.1f", turned) + " degrees");
		check(turned > 45, "guest turned more than 45 degrees");
	}

	private void bumperSteps() throws Exception {
		ServerPlayer guest = guest("Player2");
		int slot = guest.getInventory().getSelectedSlot();
		press(101, PadButton.RIGHT_BUMPER);
		waitFor("hotbar slot change", Duration.ofSeconds(5), () -> guest.getInventory().getSelectedSlot() == (slot + 1) % 9);
	}

	private void reboundJump() throws Exception {
		ServerPlayer guest = guest("Player2");
		double ground = guest.getY();
		PADS.set(101, new InputFrame(0, InputFrame.buttons(PadButton.Y), 0, 0, 0, 0, 0, 0));
		double peak = ground;
		long end = System.nanoTime() + Duration.ofSeconds(1).toNanos();
		while (System.nanoTime() < end) {
			peak = Math.max(peak, guest.getY());
			Thread.sleep(20);
		}
		PADS.set(101, InputFrame.NEUTRAL);
		log("   guest rose " + String.format("%.2f", peak - ground) + " blocks");
		check(peak - ground > 0.5, "guest jumped with Y");
	}

	private void unplugAndReplug() throws Exception {
		PADS.unplug(101);
		waitFor("satellite paused", Duration.ofSeconds(10), () -> satelliteLog().contains("hearthside satellite paused"));
		PADS.plug(102, PAD_GUID, PadStyle.PLAYSTATION);
		waitFor("satellite resumed", Duration.ofSeconds(10), () -> satelliteLog().contains("hearthside satellite resumed"));
		PADS.set(102, InputFrame.NEUTRAL);
		placeGuestOnTestPlatform();
		ServerPlayer guest = guest("Player2");
		Vec3 start = guest.position();
		PADS.set(102, new InputFrame(0, 0, 0, -1, 0, 0, 0, 0));
		Thread.sleep(2000);
		PADS.set(102, InputFrame.NEUTRAL);
		check(guest.position().distanceTo(start) > 1, "guest moves again after replug");
	}

	private void satelliteIgnoresOsInput() throws IOException {
		check(satelliteLog().contains("hearthside satellite ignores os keyboard and mouse"), "satellite OS-input line");
	}

	private void inventoryClicks() throws Exception {
		IntegratedServer server = minecraft.getSingleplayerServer();
		server.submit(() -> {
			var source = server.createCommandSourceStack().withSuppressedOutput();
			// Survival, so the inventory is the plain InventoryScreen rather than the creative item grid.
			server.getCommands().performPrefixedCommand(source, "gamemode survival Player2");
			for (int slot = 0; slot < 36; slot++) {
				server.getCommands().performPrefixedCommand(source, "item replace entity Player2 container." + slot + " with minecraft:stone 10");
			}
		}).join();
		int menusBefore = count("hearthside controller: menu mode (inventoryscreen)");
		// Player2's P0 profile moved Y to JUMP (unbinding INVENTORY); X (crafting) opens the same screen.
		press(102, PadButton.X);
		waitFor("inventory open on the guest", Duration.ofSeconds(10),
				() -> count("hearthside controller: menu mode (inventoryscreen)") > menusBefore);
		Thread.sleep(500);
		// The cursor starts mid-window, which may be over the recipe book; a D-pad hop lands on a slot.
		press(102, PadButton.DPAD_DOWN);
		Thread.sleep(300);
		ServerPlayer guest = guest("Player2");
		press(102, PadButton.X);
		waitFor("half a stack on the guest's cursor", Duration.ofSeconds(5),
				() -> guest.containerMenu.getCarried().getCount() == 5);
		press(102, PadButton.A);
		waitFor("stack placed back", Duration.ofSeconds(5), () -> guest.containerMenu.getCarried().isEmpty());
		server.submit(() -> server.getCommands().performPrefixedCommand(
				server.createCommandSourceStack().withSuppressedOutput(), "gamemode creative Player2")).join();
	}

	private void closeWithoutSneakLeak() throws Exception {
		int worldBefore = count("hearthside controller: world mode");
		// Hold B: it closes the inventory, then stays held in the world.
		PADS.set(102, new InputFrame(0, InputFrame.buttons(PadButton.B), 0, 0, 0, 0, 0, 0));
		waitFor("back in the world", Duration.ofSeconds(10), () -> count("hearthside controller: world mode") > worldBefore);
		Thread.sleep(1000);
		boolean sneaking = guest("Player2").isShiftKeyDown();
		PADS.set(102, InputFrame.NEUTRAL);
		check(!sneaking, "guest not sneaking while the closing B is still held");
	}

	private void pauseMenuOpensAndCloses() throws Exception {
		int pausesBefore = count("hearthside controller: menu mode (pausescreen)");
		int worldBefore = count("hearthside controller: world mode");
		press(102, PadButton.START);
		waitFor("pause menu on the guest", Duration.ofSeconds(10),
				() -> count("hearthside controller: menu mode (pausescreen)") > pausesBefore);
		press(102, PadButton.B);
		waitFor("pause menu closed", Duration.ofSeconds(10), () -> count("hearthside controller: world mode") > worldBefore);
	}

	private int count(String needle) throws IOException {
		String log = satelliteLog();
		int n = 0;
		for (int i = log.indexOf(needle); i >= 0; i = log.indexOf(needle, i + 1)) {
			n++;
		}
		return n;
	}

	private void padStyleReached() throws IOException {
		check(satelliteLog().contains("hearthside setpadstyle playstation"), "SetPadStyle PLAYSTATION handled");
	}

	private void player2Leaves() throws Exception {
		onRender(() -> {
			HostSession.current().leave("Player2");
			return null;
		});
		PADS.unplug(102);
		waitFor("1 player", Duration.ofSeconds(30), () -> playerCount() == 1);
		waitFor("satellite exit", Duration.ofSeconds(15), () -> satellites().isEmpty());
	}

	private void press(int pad, PadButton button) throws InterruptedException {
		PADS.set(pad, new InputFrame(0, InputFrame.buttons(button), 0, 0, 0, 0, 0, 0));
		Thread.sleep(250);
		PADS.set(pad, InputFrame.NEUTRAL);
		Thread.sleep(100);
	}

	private ServerPlayer guest(String name) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		return server == null ? null : server.getPlayerList().getPlayerByName(name);
	}

	private String satelliteLog() throws IOException {
		Path log = minecraft.gameDirectory.toPath().resolve("hearthside/satellite-1/logs/latest.log");
		return Files.exists(log) ? Files.readString(log).toLowerCase(Locale.ROOT) : "";
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
