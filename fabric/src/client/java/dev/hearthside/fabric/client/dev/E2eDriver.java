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
import dev.hearthside.core.profile.HostProfile;
import dev.hearthside.core.profile.PlayerColors;
import dev.hearthside.core.profile.ProfileCarousel;
import dev.hearthside.core.profile.ProfileFile;
import dev.hearthside.core.profile.ProfileStore;
import dev.hearthside.core.profile.QuickSetup;
import dev.hearthside.core.profile.SkinRef;
import dev.hearthside.fabric.HearthsideMod;
import com.mojang.blaze3d.platform.InputConstants;
import dev.hearthside.fabric.client.compositor.Compositor;
import dev.hearthside.fabric.client.host.LobbyScreen;
import dev.hearthside.fabric.client.input.VirtualCursor;
import dev.hearthside.fabric.client.ui.HearthSprites;
import dev.hearthside.fabric.client.ui.TitleStyle;
import dev.hearthside.fabric.client.host.Party;
import dev.hearthside.fabric.client.host.SeatCard;
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
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
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
			step("H1 every glyph and Hearthside sprite resolves", this::spritesResolve);

			if (Boolean.getBoolean("hearthside.e2e.fakePads")) {
				step("B7 the title screen has a Co-op button that opens the lobby", this::titleCoopOpensLobby);
				step("T3 the title screen draws Hearthside's background and buttons", this::titleStyled);
				step("T1 a controller moves focus on the title screen and A on Co-op opens the lobby", this::titleControllerOpensLobby);
				step("T2 the controller that opened Co-op is player 1's, not seated, and remembered", this::titleControllerIsPlayerOne);
				step("L5 player 1's card claims a controller; it drives the host and is not seated", this::hostCardClaims);
				step("L6 that controller is player 1's again next time the lobby opens", this::hostControllerRemembered);
				step("B6 cancelling the lobby stops every guest's game", this::lobbyCancelStopsGuests);
				step("B4 lobby: A seats a controller and its game starts before any world", this::lobbySeatsAndPrewarms);
				step("L1 a seated controller's last profile is pre-selected on its card", this::cardPreselects);
				step("L2 Y and the D-pad cycle profiles on the card; A confirms", this::cardCyclesAndConfirms);
				step("L9 Y on a ready card picks another profile; B keeps the current one", this::cardSwitchesProfile);
				step("B8 in the lobby a seated controller moves the host's cursor, not its own game", this::lobbySharedCursor);
				step("B10 a title screen while the party waits goes back to its lobby", this::orphanedTitleReturnsToLobby);
				step("B9 B on a seated controller gives up the seat", this::seatedBLeaves);
				step("L4 B steps back out of quick setup, then leaves the seat from the carousel", this::cardBackAndLeave);
				step("L8 unplugging mid-keyboard keeps the card; replugging resumes it", this::cardSurvivesUnplug);
				step("L10 two identical controllers never share a profile or a new name", this::twinsNeverClash);
				step("L3 New runs quick setup: A on the suggested name, skin and colour saves the profile", this::quickSetupAAA);
				step("B5 Menu on a seated controller continues; the world brings the guests in by name", this::lobbyIntoWorld);
			}
			if (onRender(() -> minecraft.level == null)) {
				openWorld();
			}
			waitFor("host session", Duration.ofSeconds(180), () -> Party.current() != null);
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
				step("B2 in a world, one A on a new controller does not join", this::oneADoesNotJoin);
				step("B3 the join prompt expires after 5 s", this::joinPromptExpires);
				step("P1 A, A on a new controller adds Player2", this::startJoins);
				step("P2 left stick moves the guest", this::stickMoves);
				step("P3 right stick turns the guest", this::stickTurns);
				step("P4 RB advances the guest's hotbar", this::bumperSteps);
				step("P7 rebound Y makes the guest jump", this::reboundJump);
				step("P5 unplug pauses, replug resumes", this::unplugAndReplug);
				step("P6 satellite input-isolation boot line present (behaviour checked by hand)", this::satelliteIgnoresOsInput);
				step("M1 inventory: X takes half a stack, A places it", this::inventoryClicks);
				step("H2 button hints draw glyph sprites for the guest's controller family", this::hintsUseGlyphSprites);
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
			waitFor("host session", Duration.ofSeconds(180), () -> Party.current() != null);
			onRender(() -> {
				Party.current().join(GUEST);
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
			Party.current().join(GUEST);
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
				Party.current().leave(GUEST);
				return null;
			});
			waitFor("guest gone", Duration.ofSeconds(30), () -> playerCount() == 1 && satellites().isEmpty());
			long mark = hostLogLength();
			onRender(() -> {
				Party.current().join(GUEST);
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
			Party.current().leave(GUEST);
			return null;
		});
		waitFor("guest gone", Duration.ofSeconds(30), () -> playerCount() == 1 && satellites().isEmpty());
		onRender(() -> {
			Party.current().join(GUEST);
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
			Party.current().join("Guest3");
			return null;
		});
		try {
			waitFor("3 players", Duration.ofSeconds(180), () -> playerCount() == 3);
			waitForProbe(3, mark, Duration.ofSeconds(30));
			waitForProbe(2, mark, Duration.ofSeconds(10));
		} finally {
			onRender(() -> {
				Party.current().leave("Guest3");
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
				Party.current().join(hostName);
				return null;
			});
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError("joining as the host's name (" + hostName + ") was accepted");
	}

	private void leaveClosesSatellite() throws Exception {
		onRender(() -> {
			Party.current().leave(GUEST);
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
			Party.current().join(GUEST);
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

	// ---- Lobby steps (before any world) ----

	private static final int LOBBY_PAD = 103;
	/** The lobby pad's own model GUID, so lobby profiles never match the in-world steps' pads. */
	private static final String LOBBY_GUID = "fake-lobby-pad";

	/** Opens the lobby the way a player does: Co-op on the title screen. */
	private LobbyScreen openLobby() throws Exception {
		return onRender(() -> {
			minecraft.gui.setScreen(new TitleScreen());
			coopButton().onPress(new KeyEvent(0, 0, 0));
			return (LobbyScreen) minecraft.gui.screen();
		});
	}

	/** The title screen's Co-op button, or null. Render thread. */
	private AbstractButton coopButton() {
		return Screens.getWidgets(minecraft.gui.screen()).stream()
				.filter(w -> w instanceof AbstractButton && w.getMessage().getString().startsWith("Co-op"))
				.map(w -> (AbstractButton) w).findFirst().orElse(null);
	}

	// ---- Hearthside look ----

	private void spritesResolve() throws Exception {
		List<String> missing = onRender(() -> {
			TextureAtlas gui = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
			List<Identifier> ids = new ArrayList<>(HearthSprites.PANELS);
			for (PadStyle style : PadStyle.values()) {
				for (Control control : Control.values()) {
					ids.add(HearthSprites.glyph(control, style));
				}
			}
			return ids.stream()
					.filter(id -> gui.getSprite(id).contents().name().equals(MissingTextureAtlasSprite.getLocation()))
					.map(Identifier::toString).toList();
		});
		check(missing.isEmpty(), "every sprite is in the GUI atlas (missing " + missing.size() + ": "
				+ missing.stream().limit(6).toList() + ")");
	}

	private void hintsUseGlyphSprites() throws Exception {
		// The guest's game (a fake PlayStation pad) logs each glyph sprite the first time it draws one.
		waitFor("a PlayStation glyph drawn in the guest's hints", Duration.ofSeconds(10),
				() -> satelliteLog().contains("hearthside hint glyph hearthside:glyph/playstation/"));
		check(!satelliteLog().contains("hearthside hint glyph hearthside:glyph/xbox/"), "no other family's glyphs");
	}

	private void titleCoopOpensLobby() throws Exception {
		PADS.plug(LOBBY_PAD, LOBBY_GUID, PadStyle.PLAYSTATION);
		onRender(() -> {
			minecraft.gui.setScreen(new TitleScreen());
			return null;
		});
		AbstractButton coop = onRender(this::coopButton);
		check(coop != null, "the title screen has a Co-op button");
		String offScreen = onRender(() -> {
			Screen title = minecraft.gui.screen();
			return Screens.getWidgets(title).stream()
					.filter(w -> w.getY() < 0 || w.getY() + w.getHeight() > title.height)
					.map(w -> w.getMessage().getString() + "@" + w.getY()).toList().toString();
		});
		check(offScreen.equals("[]"), "every title-screen widget is on screen (off: " + offScreen + ")");
		waitFor("the Co-op button counts the controller", Duration.ofSeconds(5),
				() -> onRender(() -> coop.getMessage().getString().equals("Co-op (1 controller)")));
		onRender(() -> {
			coop.onPress(new KeyEvent(0, 0, 0));
			return null;
		});
		check(onRender(() -> minecraft.gui.screen() instanceof LobbyScreen), "Co-op opens the lobby");
		onRender(() -> {
			minecraft.gui.screen().onClose();
			return null;
		});
		check(onRender(() -> minecraft.gui.screen() instanceof TitleScreen) && Party.current() == null,
				"Back returns to the title screen with no party");
		PADS.unplug(LOBBY_PAD);
	}

	private void lobbyCancelStopsGuests() throws Exception {
		PADS.plug(LOBBY_PAD, LOBBY_GUID, PadStyle.PLAYSTATION);
		Thread.sleep(300);
		LobbyScreen lobby = openLobby();
		press(LOBBY_PAD, PadButton.A);
		waitFor("a guest game warming up in the lobby", Duration.ofSeconds(30), () -> !satellites().isEmpty());
		onRender(() -> {
			lobby.onClose();
			return null;
		});
		check(onRender(() -> minecraft.gui.screen() instanceof ConfirmScreen), "leaving the lobby asks first");
		check(Party.current() != null && !satellites().isEmpty(), "nothing has stopped before the answer");
		onRender(() -> {
			Screens.getWidgets(minecraft.gui.screen()).stream()
					.filter(w -> w instanceof AbstractButton && w.getMessage().getString().equals("End party"))
					.map(w -> (AbstractButton) w).findFirst().orElseThrow().onPress(new KeyEvent(0, 0, 0));
			return null;
		});
		waitFor("every guest game stopped", Duration.ofSeconds(30), () -> satellites().isEmpty());
		check(Party.current() == null, "the party is gone");
	}

	private void lobbySharedCursor() throws Exception {
		check(onRender(() -> minecraft.gui.screen() instanceof LobbyScreen), "in the lobby");
		PADS.set(LOBBY_PAD, new InputFrame(0, 0, 1, 0, 0, 0, 0, 0));
		Thread.sleep(600);
		PADS.set(LOBBY_PAD, InputFrame.NEUTRAL);
		Thread.sleep(300);
		double x = onRender(VirtualCursor::x);
		int centre = onRender(() -> minecraft.getWindow().getScreenWidth() / 2);
		check(x > centre + 20, "the stick moved the host's cursor right (x " + x + ", centre " + centre + ")");
		check(onRender(() -> Party.current().sharedStyle()) == PadStyle.PLAYSTATION,
				"the shared hints show the PlayStation buttons of the controller driving the screen");
		check(!satelliteLog().contains("hearthside controller: menu mode"),
				"the guest's game, idle on its title screen, got no controller input");
	}

	private static final int SECOND_LOBBY_PAD = 104;
	private static final int HOST_PAD = 106;
	private static final String HOST_GUID = "fake-host-pad";

	/** Saves a screenshot of the host's window to {@code run-e2e/screenshots/<name>.png} for the docs. */
	private void screenshot(String name) throws Exception {
		Thread.sleep(400);
		onRender(() -> {
			Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.gameRenderer.mainRenderTarget(), 1, message -> {});
			return null;
		});
		Thread.sleep(400);
	}

	private void titleStyled() throws Exception {
		TitleStyle.drewBackground = false;
		TitleStyle.drewButtons = false;
		onRender(() -> {
			minecraft.gui.setScreen(new TitleScreen());
			return null;
		});
		Thread.sleep(600);
		check(TitleStyle.drewBackground, "the plank wall and firelight replace the panorama");
		check(TitleStyle.drewButtons, "the title's buttons are wooden");
		screenshot("title");
	}

	private static final int TITLE_PAD = 109;
	private static final String TITLE_GUID = "fake-title-pad";

	private void titleControllerOpensLobby() throws Exception {
		PADS.plug(TITLE_PAD, TITLE_GUID, PadStyle.PLAYSTATION);
		onRender(() -> {
			minecraft.gui.setScreen(new TitleScreen());
			return null;
		});
		Thread.sleep(500);
		// Co-op is beside Singleplayer: down to the menu, then right (and up, if focus started lower).
		PadButton[] path = {PadButton.DPAD_DOWN, PadButton.DPAD_RIGHT, PadButton.DPAD_UP, PadButton.DPAD_RIGHT,
				PadButton.DPAD_UP, PadButton.DPAD_RIGHT, PadButton.DPAD_UP, PadButton.DPAD_RIGHT};
		for (int i = 0; i < path.length && !onRender(this::coopFocused); i++) {
			press(TITLE_PAD, path[i]);
		}
		check(onRender(this::coopFocused), "the D-pad moved focus to Co-op");
		screenshot("title-focus");
		press(TITLE_PAD, PadButton.A);
		waitFor("the lobby", Duration.ofSeconds(5), () -> onRender(() -> minecraft.gui.screen() instanceof LobbyScreen));
	}

	/** Whether the title screen's focused widget is the Co-op button. Render thread. */
	private boolean coopFocused() {
		return minecraft.gui.screen() instanceof TitleScreen title && title.getFocused() instanceof AbstractButton button
				&& button.getMessage().getString().startsWith("Co-op");
	}

	private void titleControllerIsPlayerOne() throws Exception {
		waitFor("player 1 has the title controller", Duration.ofSeconds(5),
				() -> onRender(() -> Party.current() != null && Party.current().hostHasController()));
		Thread.sleep(500);
		check(onRender(() -> Party.current().seatedCount()) == 0, "the A that opened Co-op did not take a guest seat");
		check(TITLE_GUID.equals(profiles().loadFile().host().preferredControllerGuid()), "it is remembered as player 1's");
		onRender(() -> {
			Party.current().releaseController();
			minecraft.gui.screen().onClose();
			return null;
		});
		PADS.unplug(TITLE_PAD);
		check(Party.current() == null, "back on the title screen with no party");
	}

	private void hostCardClaims() throws Exception {
		// Start from player 1 having no remembered controller (a stopped run can leave one behind).
		ProfileStore store = profiles();
		ProfileFile file = store.loadFile();
		store.saveFile(new ProfileFile(file.profiles(), file.host().withController(null)));
		PADS.plug(HOST_PAD, HOST_GUID, PadStyle.XBOX);
		Thread.sleep(300);
		LobbyScreen lobby = openLobby();
		screenshot("lobby-empty");
		onRender(() -> {
			Screens.getWidgets(lobby).stream()
					.filter(w -> w instanceof AbstractButton && w.getMessage().getString().equals("+ Controller"))
					.map(w -> (AbstractButton) w).findFirst().orElseThrow().onPress(new KeyEvent(0, 0, 0));
			return null;
		});
		press(HOST_PAD, PadButton.A);
		waitFor("player 1 has the controller", Duration.ofSeconds(5), () -> onRender(() -> Party.current().hostHasController()));
		check(onRender(() -> Party.current().seatedCount()) == 0, "the controller did not take a guest seat");
		check(HOST_GUID.equals(profiles().loadFile().host().preferredControllerGuid()), "player 1's controller is remembered");
	}

	private void hostControllerRemembered() throws Exception {
		onRender(() -> {
			minecraft.gui.screen().onClose();
			return null;
		});
		check(Party.current() == null, "leaving the empty lobby ends the party at once");
		openLobby();
		waitFor("player 1's controller taken again", Duration.ofSeconds(5),
				() -> onRender(() -> Party.current().hostHasController()));
		// "− Controller" gives it back: it is free again, and forgotten.
		onRender(() -> {
			Screens.getWidgets(minecraft.gui.screen()).stream()
					.filter(w -> w instanceof AbstractButton && w.getMessage().getString().equals("− Controller"))
					.map(w -> (AbstractButton) w).findFirst().orElseThrow().onPress(new KeyEvent(0, 0, 0));
			return null;
		});
		check(onRender(() -> !Party.current().hostHasController()), "player 1 gave the controller back");
		check(profiles().loadFile().host().preferredControllerGuid() == null, "and it is forgotten");
		check(onRender(() -> Party.current().freeControllers()) == 1, "it is free to join as a guest");
		onRender(() -> {
			minecraft.gui.screen().onClose();
			return null;
		});
		PADS.unplug(HOST_PAD);
	}

	private ProfileStore profiles() {
		return new ProfileStore(minecraft.gameDirectory.toPath().resolve("hearthside/profiles.json"));
	}
	private static final int REPLUG_PAD = 105;

	private SeatCard card(int seat) {
		Party party = Party.current();
		return party == null ? null : party.seatCard(seat);
	}

	/** The name on a card's carousel, or "New". Render thread. */
	private String cardShows(int seat) {
		return card(seat).carousel().current() instanceof ProfileCarousel.Entry.Profile(GuestProfile p) ? p.name() : "New";
	}

	private Path satelliteLogFile() {
		return minecraft.gameDirectory.toPath().resolve("hearthside/satellite-1/logs/latest.log");
	}

	private void cardPreselects() throws Exception {
		check(onRender(() -> card(1) != null), "seat 1 is choosing on its card");
		screenshot("lobby-choosing");
		check(onRender(() -> cardShows(1)).equals("Hazel"), "Hazel, last used on this controller, is pre-selected");
	}

	private void cardCyclesAndConfirms() throws Exception {
		press(LOBBY_PAD, PadButton.Y);
		check(onRender(() -> cardShows(1)).equals("New"), "Y moves on to New");
		press(LOBBY_PAD, PadButton.DPAD_RIGHT);
		check(onRender(() -> cardShows(1)).equals("Birch"), "right wraps round to Birch");
		press(LOBBY_PAD, PadButton.DPAD_LEFT);
		press(LOBBY_PAD, PadButton.DPAD_LEFT);
		check(onRender(() -> cardShows(1)).equals("Hazel"), "left twice is back on Hazel");
		press(LOBBY_PAD, PadButton.A);
		check(onRender(() -> card(1) == null), "A confirms: the card closes");
		int[] lit = PADS.indicated(LOBBY_PAD);
		int hazelColour = onRender(() -> Party.current().colorOf(1));
		check(lit != null && lit[0] == 1 && lit[1] == hazelColour,
				"the controller is lit as player 2 in Hazel's colour (" + (lit == null ? "none" : lit[0] + "/" + Integer.toHexString(lit[1])) + ")");
		waitFor("the seat ready as Hazel", Duration.ofSeconds(60), () -> onRender(() -> {
			Party.SeatView seat = Party.current().seatView(1);
			return seat.name().equals("Hazel") && seat.status().equals("Ready");
		}));
	}

	private void cardSwitchesProfile() throws Exception {
		press(LOBBY_PAD, PadButton.Y);
		check(onRender(() -> card(1) != null && card(1).changing()), "Y reopens the ready seat's card");
		check(onRender(() -> cardShows(1)).equals("Hazel"), "the current profile is pre-selected");
		press(LOBBY_PAD, PadButton.DPAD_LEFT);
		screenshot("lobby-changing");
		press(LOBBY_PAD, PadButton.A);
		check(onRender(() -> card(1) == null && Party.current().profileOf(1).name().equals("Birch")),
				"A switches the seat to Birch");
		press(LOBBY_PAD, PadButton.Y);
		press(LOBBY_PAD, PadButton.DPAD_RIGHT);
		press(LOBBY_PAD, PadButton.B);
		check(onRender(() -> card(1) == null && Party.current().profileOf(1).name().equals("Birch")),
				"B backs out and keeps Birch");
		check(onRender(() -> Party.current().seatView(1).status()).equals("Ready"), "the seat is ready again");
	}

	private void cardBackAndLeave() throws Exception {
		press(LOBBY_PAD, PadButton.A);
		waitFor("a card for the seat", Duration.ofSeconds(10), () -> onRender(() -> card(1) != null));
		press(LOBBY_PAD, PadButton.DPAD_RIGHT);
		press(LOBBY_PAD, PadButton.A);
		check(onRender(() -> card(1).stage()) == SeatCard.Stage.SETUP, "A on New starts quick setup");
		press(LOBBY_PAD, PadButton.B);
		check(onRender(() -> card(1).stage()) == SeatCard.Stage.CAROUSEL, "B steps back to the carousel");
		press(LOBBY_PAD, PadButton.B);
		waitFor("the seat given up", Duration.ofSeconds(30),
				() -> satellites().isEmpty() && onRender(() -> Party.current().seatedCount()) == 0);
		Files.deleteIfExists(satelliteLogFile());
	}

	private void cardSurvivesUnplug() throws Exception {
		press(LOBBY_PAD, PadButton.A);
		waitFor("a card for the seat", Duration.ofSeconds(10), () -> onRender(() -> card(1) != null));
		press(LOBBY_PAD, PadButton.DPAD_RIGHT);
		press(LOBBY_PAD, PadButton.A);
		press(LOBBY_PAD, PadButton.DPAD_DOWN);
		press(LOBBY_PAD, PadButton.A);
		String typed = onRender(() -> card(1).keyboard().text());
		check(typed.equals("a"), "moving then A types on the keyboard (typed: " + typed + ")");
		PADS.unplug(LOBBY_PAD);
		Thread.sleep(1000);
		// The same model plugged back in under a new id resumes the seat.
		PADS.plug(REPLUG_PAD, LOBBY_GUID, PadStyle.PLAYSTATION);
		Thread.sleep(1000);
		check(onRender(() -> card(1) != null && card(1).stage() == SeatCard.Stage.SETUP
				&& card(1).keyboard().text().equals("a")), "the card and the typed name are kept");
		press(REPLUG_PAD, PadButton.X);
		check(onRender(() -> card(1).keyboard().text()).isEmpty(), "the replugged controller drives the card");
		press(REPLUG_PAD, PadButton.B);
		press(REPLUG_PAD, PadButton.B);
		waitFor("the seat given up", Duration.ofSeconds(30),
				() -> satellites().isEmpty() && onRender(() -> Party.current().seatedCount()) == 0);
		PADS.unplug(REPLUG_PAD);
		PADS.plug(LOBBY_PAD, LOBBY_GUID, PadStyle.PLAYSTATION);
		Thread.sleep(300);
		Files.deleteIfExists(satelliteLogFile());
	}

	private static final int TWIN_A = 107, TWIN_B = 108;

	/** Moves a seat's carousel to New. */
	private void toNew(int seat, int pad) throws Exception {
		for (int i = 0; i < 6 && !onRender(() -> cardShows(seat)).equals("New"); i++) {
			press(pad, PadButton.DPAD_RIGHT);
		}
		check(onRender(() -> cardShows(seat)).equals("New"), "seat " + seat + " reached New");
	}

	private void twinsNeverClash() throws Exception {
		PADS.plug(TWIN_A, "fake-twin", PadStyle.XBOX);
		PADS.plug(TWIN_B, "fake-twin", PadStyle.XBOX);
		Thread.sleep(300);
		press(TWIN_A, PadButton.A);
		waitFor("a card for twin A", Duration.ofSeconds(10), () -> onRender(() -> card(1) != null));
		press(TWIN_B, PadButton.A);
		waitFor("a card for twin B", Duration.ofSeconds(10), () -> onRender(() -> card(2) != null));
		String first = onRender(() -> cardShows(1));
		check(onRender(() -> cardShows(2)).equals(first), "both cards open on the same profile (" + first + ")");
		press(TWIN_A, PadButton.A);
		check(onRender(() -> card(1) == null), "twin A takes " + first);
		check(!onRender(() -> cardShows(2)).equals(first), "twin B's open card stops offering " + first + " at once");
		// Two new profiles at the same time: different names, and both save.
		toNew(2, TWIN_B);
		press(TWIN_B, PadButton.A);
		press(TWIN_A, PadButton.Y);
		check(onRender(() -> card(1) != null && card(1).changing()), "twin A reopens their card");
		toNew(1, TWIN_A);
		press(TWIN_A, PadButton.A);
		String nameA = onRender(() -> card(1).keyboard().text());
		String nameB = onRender(() -> card(2).keyboard().text());
		check(!nameA.equalsIgnoreCase(nameB), "two new players are offered different names (" + nameA + ", " + nameB + ")");
		for (int i = 0; i < 3; i++) {
			press(TWIN_A, PadButton.A);
			press(TWIN_B, PadButton.A);
		}
		check(onRender(() -> card(1) == null && card(2) == null), "both new profiles are saved");
		check(onRender(() -> Party.current().profileOf(1).name()).equals(nameA)
				&& onRender(() -> Party.current().profileOf(2).name()).equals(nameB), "each seat has its own new profile");
		press(TWIN_A, PadButton.B);
		press(TWIN_B, PadButton.B);
		waitFor("both twins gone", Duration.ofSeconds(30),
				() -> satellites().isEmpty() && onRender(() -> Party.current().seatedCount()) == 0);
		PADS.unplug(TWIN_A);
		PADS.unplug(TWIN_B);
		Thread.sleep(300);
		Files.deleteIfExists(satelliteLogFile());
		Files.deleteIfExists(minecraft.gameDirectory.toPath().resolve("hearthside/satellite-2/logs/latest.log"));
	}

	private void quickSetupAAA() throws Exception {
		press(LOBBY_PAD, PadButton.A);
		waitFor("a card for the seat", Duration.ofSeconds(10), () -> onRender(() -> card(1) != null));
		toNew(1, LOBBY_PAD);
		press(LOBBY_PAD, PadButton.A);
		String suggestion = onRender(() -> card(1).keyboard().text());
		int colour = onRender(() -> card(1).setup().color());
		screenshot("lobby-keyboard");
		press(LOBBY_PAD, PadButton.A);
		check(onRender(() -> card(1).setup().step()) == QuickSetup.Step.SKIN, "A keeps the suggested name " + suggestion);
		press(LOBBY_PAD, PadButton.A);
		press(LOBBY_PAD, PadButton.A);
		check(onRender(() -> card(1) == null), "A on the colour finishes: the card closes");
		GuestProfile saved = new ProfileStore(minecraft.gameDirectory.toPath().resolve("hearthside/profiles.json"))
				.load().getLast();
		check(saved.name().equals(suggestion) && LOBBY_GUID.equals(saved.preferredControllerGuid())
				&& saved.color() == colour, "the new profile is saved and remembered for this controller");
		check(colour == PlayerColors.DEFAULTS[1], "the first free colour was pre-picked");
		waitFor("the seat ready as " + suggestion, Duration.ofSeconds(120),
				() -> onRender(() -> Party.current().seatView(1).status()).equals("Ready"));
	}

	private void orphanedTitleReturnsToLobby() throws Exception {
		// What Minecraft does when a world fails to open: it shows the title screen.
		onRender(() -> {
			minecraft.gui.setScreen(new TitleScreen());
			return null;
		});
		waitFor("back in the lobby", Duration.ofSeconds(5),
				() -> onRender(() -> minecraft.gui.screen() instanceof LobbyScreen));
		check(Party.current() != null && Party.current().seatedCount() == 1 && !satellites().isEmpty(),
				"the seat and its game are kept");
	}

	private void seatedBLeaves() throws Exception {
		press(LOBBY_PAD, PadButton.B);
		waitFor("the seat's game stopped", Duration.ofSeconds(30), () -> satellites().isEmpty());
		check(onRender(() -> Party.current().seatedCount()) == 0, "the seat is free");
		check(onRender(() -> minecraft.gui.screen() instanceof LobbyScreen), "the lobby stays open");
		// The next game in this seat writes a new log; don't let B5 read this one's "connected" line.
		Files.deleteIfExists(minecraft.gameDirectory.toPath().resolve("hearthside/satellite-1/logs/latest.log"));
	}

	private void lobbySeatsAndPrewarms() throws Exception {
		// Known profiles for the card steps: Hazel last used the lobby pad.
		ProfileStore store = new ProfileStore(minecraft.gameDirectory.toPath().resolve("hearthside/profiles.json"));
		HostProfile host = store.loadFile().host();
		store.saveFile(new ProfileFile(List.of(
				new GuestProfile(UUID.nameUUIDFromBytes("Birch".getBytes()), "Birch", SkinRef.defaultSkin("steve"), null),
				new GuestProfile(UUID.nameUUIDFromBytes("Hazel".getBytes()), "Hazel", SkinRef.defaultSkin("alex"),
						LOBBY_GUID)), host));
		openLobby();
		press(LOBBY_PAD, PadButton.A);
		waitFor("a guest game warming up", Duration.ofSeconds(30), () -> !satellites().isEmpty());
		check(onRender(() -> minecraft.level == null), "no world is open yet");
		waitFor("the guest's game connected to the party", Duration.ofSeconds(120),
				() -> satelliteLog().contains("hearthside satellite connected as player"));
	}

	private void lobbyIntoWorld() throws Exception {
		// Seat 1 is ready as the profile quick setup made (L3). A second controller takes seat 2 and is still on its
		// card when the world opens: it goes in with what its card shows (the first profile nobody holds).
		String made = onRender(() -> Party.current().seatView(1).name());
		PADS.plug(SECOND_LOBBY_PAD, "fake-lobby-pad-2", PadStyle.XBOX);
		Thread.sleep(300);
		check(onRender(() -> Party.current().joinStyle()) == PadStyle.XBOX,
				"the Join hints show the free Xbox controller's buttons, not seat 1's PlayStation ones");
		press(SECOND_LOBBY_PAD, PadButton.A);
		waitFor("seat 2 choosing on its card", Duration.ofSeconds(10), () -> onRender(() -> card(2) != null));
		screenshot("lobby-three-players");
		String shown = onRender(() -> cardShows(2));
		press(LOBBY_PAD, PadButton.START);
		check(onRender(() -> minecraft.gui.screen() instanceof SelectWorldScreen), "Menu continues to world select");
		openWorld();
		waitFor("both lobby guests in the world", Duration.ofSeconds(240), () -> playerCount() == 3);
		check(guest(made) != null, made + " is in the world under that name (the game launched as Seat1)");
		check(guest(shown) != null, "seat 2 went in as what its card showed (" + shown + ")");
		// Back to the starting point for the rest of the run: the lobby guest leaves.
		onRender(() -> {
			Party party = Party.current();
			party.leaveAll();
			return null;
		});
		PADS.unplug(LOBBY_PAD);
		PADS.unplug(SECOND_LOBBY_PAD);
		waitFor("lobby guest gone", Duration.ofSeconds(30), () -> playerCount() == 1 && satellites().isEmpty());
	}

	private void oneADoesNotJoin() throws Exception {
		PADS.plug(101, PAD_GUID, PadStyle.PLAYSTATION);
		Thread.sleep(300);
		press(101, PadButton.A);
		Thread.sleep(2500);
		check(guest("Player2") == null && satellites().isEmpty(), "one A only asks; nobody joined");
	}

	private void joinPromptExpires() throws Exception {
		Thread.sleep(3500); // past the 5 s window of B2's press
		press(101, PadButton.A); // a fresh prompt, not a confirmation
		Thread.sleep(2500);
		check(guest("Player2") == null && satellites().isEmpty(), "an A after the prompt expired only asks again");
	}

	private void startJoins() throws Exception {
		Thread.sleep(5500); // let B3's prompt expire
		press(101, PadButton.A);
		press(101, PadButton.A);
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
			Party.current().leave("Player2");
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
