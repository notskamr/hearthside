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
import com.mojang.blaze3d.platform.InputConstants;
import dev.hearthside.fabric.client.compositor.Compositor;
import dev.hearthside.fabric.client.host.HostSession;
import dev.hearthside.fabric.client.mixin.ContainerScreenAccessor;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.sdl.SDLVideo;
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
			step("C1 guest window is hidden", this::guestWindowHidden);
			step("C2 guest frames reach the host's window", this::guestFramesComposited);
			step("C3 host window is not moved or resized by split-screen", this::hostWindowUntouched);
			step("C4 host window resize re-lays out the guest", this::resizeRelaysOut);
			step("C5 copy transport works", this::copyTransportWorks);
			step("C6 maximise and restore keep the guest composited", this::maximiseAndRestore);
			step("C12 a failed GPU import falls back to copying frames", this::failedImportFallsBackToCopy);
			step("C7 a third player and back re-lays out without GL errors", this::thirdPlayerAndBack);
			step("C9 the host's mouse works only inside its own view", this::hostMouseConfined);
			step("C11 in the world, the host's clicks count wherever the cursor drifted", this::hostWorldClicksAnywhere);
			step("3 crashed satellite is restarted", this::crashedSatelliteRestarts);
			step("C8 the restarted guest's frames come back", this::restartedGuestComposited);
			step("4 guest named like the host is refused", this::hostNameRefused);
			step("4b opening and closing LAN keeps the guest", this::lanToggleKeepsGuest);
			long beforeLastLeave = hostLogLength();
			step("5 leave closes the satellite and leaves the host window as it was", this::leaveClosesSatellite);
			step("C10 with the last guest gone the host renders full-window again", () -> fullWindowAgain(beforeLastLeave));
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
				step("A1 RT punches a mob", this::triggerPunches);
				step("A2 holding RT mines a block in the unfocused guest window", this::triggerMines);
				step("A3 LT places a block", this::triggerPlaces);
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
	}

	// ---- Compositor steps ----

	private static final String PATTERN = "rgb=ff00ff";

	private void guestWindowHidden() throws Exception {
		waitFor("satellite window flags line", Duration.ofSeconds(30),
				() -> satelliteLog().contains("hearthside window flags hidden=true"));
	}

	/** The guest paints its frame's top-left corner magenta in E2E; the host logs what it composited there. */
	private void guestFramesComposited() throws Exception {
		waitForProbe(2, hostLogLength(), Duration.ofSeconds(30));
	}

	private void hostWindowUntouched() throws Exception {
		Rect now = onRender(() -> WindowPlacer.bounds(minecraft.getWindow()));
		log("   host window " + now + ", before split-screen " + hostWindowBefore);
		check(now.equals(hostWindowBefore), "host window unchanged by split-screen");
	}

	private void resizeRelaysOut() throws Exception {
		long mark = hostLogLength();
		onRender(() -> SDLVideo.SDL_SetWindowSize(minecraft.getWindow().handle(), 1280, 720));
		try {
			int halfWidth = onRender(() -> minecraft.getWindow().queryFramebufferSize().width()) / 2;
			waitFor("guest re-sized to half the new window", Duration.ofSeconds(10),
					() -> satelliteLog().contains("hearthside virtual size " + halfWidth + "x"));
			waitForProbe(2, mark, Duration.ofSeconds(10));
		} finally {
			onRender(() -> SDLVideo.SDL_SetWindowSize(minecraft.getWindow().handle(), hostWindowBefore.width(), hostWindowBefore.height()));
		}
		// The transport is decided once; a resize must not re-probe the GPU (each probe creates Vulkan instances).
		int probes = count("hearthside gpu sharing probe");
		check(probes <= 1, "the guest probed the GPU once, not " + probes + " times");
	}

	private void copyTransportWorks() throws Exception {
		System.setProperty("hearthside.forceCopy", "true");
		try {
			onRender(() -> {
				HostSession.current().leave(GUEST);
				return null;
			});
			waitFor("guest gone", Duration.ofSeconds(30), () -> playerCount() == 1 && satellites().isEmpty());
			long mark = hostLogLength();
			onRender(() -> {
				HostSession.current().join(GUEST);
				return null;
			});
			waitFor("guest back", Duration.ofSeconds(180), () -> playerCount() == 2);
			waitFor("satellite on the copy transport", Duration.ofSeconds(30),
					() -> satelliteLog().contains("hearthside frame slots ready transport=copy"));
			waitForProbe(2, mark, Duration.ofSeconds(30));
			// Resize twice: each reallocates the copy transport's files; old ones must be unmapped and deleted.
			for (int[] size : new int[][] {{1280, 720}, {hostWindowBefore.width(), hostWindowBefore.height()}}) {
				int before = lastProbeGeneration(2);
				long resized = hostLogLength();
				onRender(() -> SDLVideo.SDL_SetWindowSize(minecraft.getWindow().handle(), size[0], size[1]));
				waitForProbe(2, resized, Duration.ofSeconds(15), before);
			}
			Thread.sleep(1000);
			long pixelFiles;
			try (var files = Files.list(minecraft.gameDirectory.toPath().resolve("hearthside/satellite-1"))) {
				pixelFiles = files.filter(f -> f.getFileName().toString().endsWith(".pixels")).count();
			}
			log("   copy-transport pixel files left: " + pixelFiles);
			check(pixelFiles <= 2, "old copy-transport files are cleaned up (found " + pixelFiles + ")");
		} finally {
			System.clearProperty("hearthside.forceCopy");
		}
	}

	private void maximiseAndRestore() throws Exception {
		int before = lastProbeGeneration(2);
		long mark = hostLogLength();
		onRender(() -> SDLVideo.SDL_MaximizeWindow(minecraft.getWindow().handle()));
		int maximised;
		try {
			waitFor("window maximised", Duration.ofSeconds(5),
					() -> onRender(() -> minecraft.getWindow().queryFramebufferSize().width()) > hostWindowBefore.width());
			maximised = waitForProbe(2, mark, Duration.ofSeconds(10), before);
		} finally {
			onRender(() -> SDLVideo.SDL_RestoreWindow(minecraft.getWindow().handle()));
		}
		long restored = hostLogLength();
		waitFor("window restored", Duration.ofSeconds(5),
				() -> onRender(() -> minecraft.getWindow().queryFramebufferSize().width()) == hostWindowBefore.width());
		waitForProbe(2, restored, Duration.ofSeconds(10), maximised);
	}

	/** A GPU import that fails must not freeze the guest: the host switches it to the copy transport. */
	private void failedImportFallsBackToCopy() throws Exception {
		// Start from a guest on GPU sharing (C5 left it on copy).
		onRender(() -> {
			HostSession.current().leave(GUEST);
			return null;
		});
		waitFor("guest gone", Duration.ofSeconds(30), () -> playerCount() == 1 && satellites().isEmpty());
		onRender(() -> {
			HostSession.current().join(GUEST);
			return null;
		});
		waitFor("guest back", Duration.ofSeconds(180), () -> playerCount() == 2);
		waitFor("guest on GPU sharing", Duration.ofSeconds(30),
				() -> satelliteLog().contains("hearthside frame slots ready transport=gpu_shared"));
		int before = lastProbeGeneration(2);
		long mark = hostLogLength();
		int satelliteMark = satelliteLog().length();
		System.setProperty("hearthside.e2e.failGpuImport", "true");
		try {
			onRender(() -> SDLVideo.SDL_SetWindowSize(minecraft.getWindow().handle(), 1280, 720));
			waitFor("guest switched to copy", Duration.ofSeconds(15),
					() -> satelliteLog().substring(Math.min(satelliteMark, satelliteLog().length()))
							.contains("hearthside frame slots ready transport=copy"));
			waitForProbe(2, mark, Duration.ofSeconds(15), before);
		} finally {
			System.clearProperty("hearthside.e2e.failGpuImport");
			onRender(() -> SDLVideo.SDL_SetWindowSize(minecraft.getWindow().handle(), hostWindowBefore.width(), hostWindowBefore.height()));
		}
	}

	/** In the world, player 1's mouse buttons always count, wherever the hidden OS cursor has drifted. */
	private void hostWorldClicksAnywhere() throws Exception {
		double guestX = onRender(() -> minecraft.getWindow().queryFramebufferSize().width() * 0.75 * Compositor.active().screenPerFramebuffer());
		double hostX = guestX / 3;
		check(onRender(() -> minecraft.gui.screen() == null), "host is in the world");
		try {
			osButton(guestX, 100, true);
			check(onRender(() -> minecraft.options.keyAttack.isDown()), "attack held by a press over another view");
			osButton(guestX, 100, false);
			check(onRender(() -> !minecraft.options.keyAttack.isDown()), "attack released over another view");
			// Pressed inside, released outside: the release must still land, or attack sticks down.
			osButton(hostX, 100, true);
			osButton(guestX, 100, false);
			check(onRender(() -> !minecraft.options.keyAttack.isDown()), "attack released after drifting out of the view");
		} finally {
			onRender(() -> {
				minecraft.options.keyAttack.setDown(false);
				return null;
			});
		}
	}

	private void osButton(double windowX, double windowY, boolean press) throws Exception {
		onRender(() -> {
			long handle = minecraft.getWindow().handle();
			minecraft.mouseHandler.onMove(handle, windowX, windowY, 0, 0);
			minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), press ? 1 : 0);
			return null;
		});
		Thread.sleep(100);
	}

	private void thirdPlayerAndBack() throws Exception {
		long mark = hostLogLength();
		onRender(() -> {
			HostSession.current().join("Guest3");
			return null;
		});
		try {
			waitFor("3 players", Duration.ofSeconds(180), () -> playerCount() == 3);
			waitForProbe(3, mark, Duration.ofSeconds(30));
			waitForProbe(2, mark, Duration.ofSeconds(10));
		} finally {
			onRender(() -> {
				HostSession.current().leave("Guest3");
				return null;
			});
		}
		long back = hostLogLength();
		waitFor("back to 2 players", Duration.ofSeconds(30), () -> playerCount() == 2);
		waitForProbe(2, back, Duration.ofSeconds(10));
		check(!hostLogSince(mark).contains("gl error"), "no GL errors while re-laying out");
	}

	private int generationBeforeCrash = -1;

	/** The host keeps the dead guest's last frame; only a probe from a new import proves the restart's frames arrive. */
	private void restartedGuestComposited() throws Exception {
		waitForProbe(2, 0, Duration.ofSeconds(30), generationBeforeCrash);
	}

	/** Clicks through MouseHandler as the OS would, in window coordinates: inside the host's view, then over the guest's. */
	private void hostMouseConfined() throws Exception {
		IntegratedServer server = minecraft.getSingleplayerServer();
		String host = minecraft.getUser().getName();
		server(("gamemode survival " + host), ("item replace entity " + host + " container.9 with minecraft:stone 10"));
		onRender(() -> {
			minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
			return null;
		});
		Thread.sleep(500);
		ServerPlayer hostPlayer = server.getPlayerList().getPlayerByName(host);
		try {
			// Slot 9 is the first main-inventory slot; find where it is drawn, in window coordinates.
			double[] slotWindow = onRender(() -> {
				var screen = (InventoryScreen) minecraft.gui.screen();
				var accessor = (ContainerScreenAccessor) screen;
				var slot = screen.getMenu().slots.stream().filter(s -> s.getContainerSlot() == 9 && s.container == minecraft.player.getInventory())
						.findFirst().orElseThrow();
				double guiX = accessor.hearthside$leftPos() + slot.x + 8, guiY = accessor.hearthside$topPos() + slot.y + 8;
				var window = minecraft.getWindow();
				double toScreen = (double) window.getScreenWidth() / window.getGuiScaledWidth();
				return new double[] {guiX * toScreen, guiY * toScreen};
			});
			osClick(slotWindow[0], slotWindow[1]);
			waitFor("stone on the host's cursor", Duration.ofSeconds(5), () -> !hostPlayer.containerMenu.getCarried().isEmpty());
			osClick(slotWindow[0], slotWindow[1]);
			waitFor("stone put back", Duration.ofSeconds(5), () -> hostPlayer.containerMenu.getCarried().isEmpty());
			// Over the guest's view (the right half of the window), the same click must do nothing.
			double guestX = onRender(() -> minecraft.getWindow().queryFramebufferSize().width() * 0.75 * Compositor.active().screenPerFramebuffer());
			osClick(guestX, slotWindow[1]);
			Thread.sleep(500);
			check(hostPlayer.containerMenu.getCarried().isEmpty(), "a click over the guest's view did nothing");
		} finally {
			onRender(() -> {
				minecraft.gui.setScreen(null);
				return null;
			});
			server("gamemode creative " + host);
		}
	}

	private void osClick(double windowX, double windowY) throws Exception {
		onRender(() -> {
			long handle = minecraft.getWindow().handle();
			minecraft.mouseHandler.onMove(handle, windowX, windowY, 0, 0);
			minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), 1);
			minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), 0);
			return null;
		});
		Thread.sleep(300);
	}

	private void fullWindowAgain(long fromOffset) throws Exception {
		waitFor("virtual size cleared", Duration.ofSeconds(10), () -> hostLogSince(fromOffset).contains("virtual size cleared"));
		boolean full = onRender(() -> minecraft.getWindow().getWidth() == minecraft.getWindow().queryFramebufferSize().width()
				&& minecraft.getWindow().getHeight() == minecraft.getWindow().queryFramebufferSize().height());
		check(full, "the host renders at its full window size");
	}

	private void waitForProbe(int player, long fromOffset, Duration timeout) throws Exception {
		waitForProbe(player, fromOffset, timeout, -1);
	}

	/** Waits for the host to log the pattern for {@code player} from an import other than generation {@code notGeneration}. */
	private int waitForProbe(int player, long fromOffset, Duration timeout, int notGeneration) throws Exception {
		Pattern line = Pattern.compile("hearthside compositor probe player=" + player + " gen=([0-9]+) " + PATTERN);
		int[] found = {-1};
		waitFor("player " + player + "'s pattern in the host window" + (notGeneration < 0 ? "" : " from a new import"), timeout, () -> {
			Matcher m = line.matcher(hostLogSince(fromOffset));
			while (m.find()) {
				int generation = Integer.parseInt(m.group(1));
				if (generation != notGeneration) {
					found[0] = generation;
					return true;
				}
			}
			return false;
		});
		return found[0];
	}

	/** The generation of the most recent probe the host logged for {@code player}. */
	private int lastProbeGeneration(int player) throws IOException {
		Matcher m = Pattern.compile("hearthside compositor probe player=" + player + " gen=([0-9]+)").matcher(hostLogSince(0));
		int generation = -1;
		while (m.find()) {
			generation = Integer.parseInt(m.group(1));
		}
		return generation;
	}

	private Path hostLogFile() {
		return minecraft.gameDirectory.toPath().resolve("logs/latest.log");
	}

	private long hostLogLength() throws IOException {
		return Files.size(hostLogFile());
	}

	private String hostLogSince(long offset) throws IOException {
		byte[] all = Files.readAllBytes(hostLogFile());
		int from = (int) Math.min(offset, all.length);
		return new String(all, from, all.length - from, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
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
		generationBeforeCrash = lastProbeGeneration(2);
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
		// Every controller step runs with the guest window unfocused, as in real play.
		checkGuestUnfocused();
	}

	private void checkGuestUnfocused() throws IOException {
		String log = satelliteLog();
		check(log.contains("window focused: false") && !log.contains("window focused: true"),
				"guest window never had OS focus");
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

	/** Survival on a cleared platform, looking 30° down at the block two ahead (0 250 2). */
	private void aimAtTarget(String... setup) throws Exception {
		List<String> commands = new ArrayList<>(List.of(
				"gamemode survival Player2",
				"fill -10 249 -10 10 249 10 minecraft:barrier",
				"fill -10 250 -10 10 253 10 minecraft:air",
				"kill @e[type=!player]"));
		commands.addAll(List.of(setup));
		commands.add("tp Player2 0.5 250 0.5 0 30");
		server(commands.toArray(String[]::new));
		Thread.sleep(1000);
	}

	private void triggerPunches() throws Exception {
		aimAtTarget("summon minecraft:pig 0.5 250 2.5 {NoAI:1b}");
		ServerPlayer guest = guest("Player2");
		AABB around = new AABB(-3, 249, -3, 4, 253, 5);
		Callable<Float> pigHealth = () -> minecraft.getSingleplayerServer().submit(() -> {
			List<Pig> pigs = guest.level().getEntitiesOfClass(Pig.class, around);
			return pigs.isEmpty() ? -1f : pigs.getFirst().getHealth();
		}).join();
		float before = pigHealth.call();
		check(before > 0, "pig spawned in front of the guest");
		pull(102, false);
		waitFor("pig hurt", Duration.ofSeconds(5), () -> pigHealth.call() < before);
	}

	private void triggerMines() throws Exception {
		BlockPos target = new BlockPos(0, 250, 2);
		aimAtTarget("setblock 0 250 2 minecraft:dirt");
		ServerPlayer guest = guest("Player2");
		// Vanilla only keeps mining while the mouse is grabbed, which normally needs focus.
		checkGuestUnfocused();
		PADS.set(102, new InputFrame(0, 0, 0, 0, 0, 0, 0, 1));
		try {
			waitFor("dirt mined", Duration.ofSeconds(5), () -> blockAt(guest, target).isAir());
		} finally {
			PADS.set(102, InputFrame.NEUTRAL);
		}
	}

	private void triggerPlaces() throws Exception {
		// Looking 30° down from the platform, the ray meets the barrier top at about z = 3.3.
		BlockPos expected = new BlockPos(0, 250, 3);
		aimAtTarget("item replace entity Player2 weapon.mainhand with minecraft:dirt 4");
		ServerPlayer guest = guest("Player2");
		pull(102, true);
		waitFor("dirt placed", Duration.ofSeconds(5), () -> blockAt(guest, expected).is(Blocks.DIRT));
		server("gamemode creative Player2");
	}

	private BlockState blockAt(ServerPlayer player, BlockPos pos) {
		return minecraft.getSingleplayerServer().submit(() -> player.level().getBlockState(pos)).join();
	}

	/** Pulls and releases a trigger. */
	private void pull(int pad, boolean left) throws InterruptedException {
		PADS.set(pad, new InputFrame(0, 0, 0, 0, 0, 0, left ? 1 : 0, left ? 0 : 1));
		Thread.sleep(250);
		PADS.set(pad, InputFrame.NEUTRAL);
		Thread.sleep(100);
	}

	private void server(String... commands) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		server.submit(() -> {
			var source = server.createCommandSourceStack().withSuppressedOutput();
			for (String command : commands) {
				server.getCommands().performPrefixedCommand(source, command);
			}
		}).join();
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
