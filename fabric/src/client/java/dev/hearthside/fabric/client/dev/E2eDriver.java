package dev.hearthside.fabric.client.dev;

import dev.hearthside.core.e2e.StepSelection;
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
import dev.hearthside.core.session.CoopWorldsStore;
import dev.hearthside.core.profile.SkinRef;
import dev.hearthside.fabric.HearthsideMod;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.hearthside.fabric.client.compositor.Compositor;
import dev.hearthside.fabric.client.host.LobbyScreen;
import dev.hearthside.fabric.client.input.ButtonGuideRenderer;
import dev.hearthside.fabric.client.ui.HearthTextField;
import dev.hearthside.fabric.client.menu.TitleInput;
import dev.hearthside.fabric.client.input.VirtualCursor;
import dev.hearthside.fabric.client.ui.HearthSprites;
import dev.hearthside.fabric.client.ui.TitleStyle;
import dev.hearthside.fabric.client.host.Party;
import dev.hearthside.fabric.client.host.ControlsScreen;
import dev.hearthside.fabric.client.host.ManageProfilesScreen;
import dev.hearthside.fabric.client.host.NewWorldScreen;
import dev.hearthside.fabric.client.host.SeatCard;
import dev.hearthside.fabric.client.host.WorldsScreen;
import dev.hearthside.fabric.client.mixin.ContainerScreenAccessor;
import dev.hearthside.fabric.client.window.WindowPlacer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
	/**
	 * Every step in run order: its id, then the earlier steps whose effects its body relies on (the party's player
	 * count, an open screen, a seated profile, a session). {@code -Dhearthside.e2e.only=<ids>} runs the named steps and
	 * what they need, transitively; every other step is reported as SKIP. A step that works from its section's starting
	 * state (the title screen; the world with the host alone) needs nothing.
	 */
	private static final String[][] NEEDS = {
			{"X1"}, {"X1b"},
			{"H1"},
			// The lobby block (fake pads), from the title screen.
			{"B7"}, {"T3"}, {"T4"}, {"T1"}, {"T2", "T1"}, {"L5"},
			{"W3", "L5"}, {"W4", "L5"}, {"W5", "L5"}, {"W6", "L5"}, {"W7", "L5"}, {"W8", "L5"},
			{"W10", "L5"}, {"W11", "L5"}, {"W9", "L5"}, {"L6", "W9"},
			{"B6"}, {"B4", "B6"}, {"L1", "B4"}, {"L2", "L1"}, {"L9", "L2"}, {"R1", "L9"}, {"R2", "R1"}, {"L11", "R1"},
			{"B8", "L2"}, {"B10", "B4"}, {"B9", "B4"}, {"L4", "B9"}, {"L8", "L4"}, {"L10", "L8"}, {"L3", "L10"}, {"L12", "L3"},
			{"B5", "L3"}, {"W1", "B5"}, {"S1", "L2", "W1"}, {"W2", "B5"},
			// The in-world session: the world opens with the host alone.
			{"1"}, {"2", "1"}, {"C1", "1"}, {"C2", "1"}, {"Q1", "1"}, {"V1", "C2"}, {"V6", "1"}, {"O1", "C2"}, {"C3", "1"}, {"C4", "C2"},
			{"V2", "C2"}, {"V4", "C2"}, {"V5", "C2"}, {"V7", "C2"}, {"V8", "C2"}, {"C5", "1"}, {"C5b", "C5"},
			{"C6", "C2"}, {"C12", "1"}, {"V3", "1"}, {"V9", "1"}, {"C7", "C2"}, {"D4", "C7"}, {"C7b", "C7"},
			{"V10", "C7b"}, {"D5", "C7b"},
			{"D6", "D5"},
			{"C7c", "C7"}, {"G1", "C6", "D6"}, {"C9", "1"}, {"C11", "1"}, {"3", "1"}, {"C8", "3"}, {"4"}, {"4b", "1"},
			{"5", "1"}, {"C10", "5"}, {"D7"}, {"D8"},
			// The in-world pad block (fake pads).
			{"P0"}, {"B2"}, {"D3", "B2"}, {"B3", "B2"}, {"D2", "P0", "B2"}, {"D1", "D2"}, {"P1", "D2"},
			{"P2", "P1"}, {"P3", "P1"}, {"P4", "P1"}, {"P7", "P1"}, {"P5", "P1"}, {"P6", "P1"},
			{"M1", "P5"}, {"H2", "P1"}, {"M2", "M1"}, {"M3", "P5"}, {"A1", "P5"}, {"A2", "P5"}, {"A3", "P5"},
			{"P8", "P1"}, {"P9", "P1"}, {"S2", "B3", "P9"},
			{"6"},
			// The tiled session.
			{"D9"}, {"7"},
	};
	private static final List<String> ORDER = Arrays.stream(NEEDS).map(row -> row[0]).toList();
	private static final Map<String, List<String>> NEEDED = Arrays.stream(NEEDS)
			.collect(Collectors.toMap(row -> row[0], row -> List.of(row).subList(1, row.length)));
	/** Step 7 is done by the run script: it force-kills the host and checks the guest's game exits by itself. */
	private static final String HOST_KILL = "7 force-killing the host ends its guests' games";

	private static final String WORLD = "hearthside-e2e";
	private static final String GUEST = "Guest2";

	private final Minecraft minecraft;
	private final Path report;
	private Rect hostWindowBefore;
	private StepSelection selection;

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
			selection = StepSelection.parse(System.getProperty("hearthside.e2e.only"), ORDER, NEEDED);
			if (!selection.everything()) {
				log("   only " + ORDER.stream().filter(selection::runs).toList());
			}
			// A fresh game folder may show first-launch screens instead of the title; any settled screen will do.
			waitFor("resources loaded", Duration.ofSeconds(180),
					() -> onRender(() -> minecraft.gui.overlay() == null && minecraft.gui.screen() != null));
			log("   first screen: " + onRender(() -> minecraft.gui.screen().getClass().getSimpleName()));
			onRender(() -> {
				// Every sound the host plays from here on, for S1 and S2.
				minecraft.getSoundManager().addListener((sound, events, range) ->
						heard.add(new Heard(System.nanoTime(), sound.getIdentifier().toString())));
				return null;
			});
			step("X1 a shared image round-trips through this game's backend", this::sharedImageRoundTrips);
			step("X1b an import whose real image settings differ from the guest's is refused", this::realCreateInfoMismatchRefused);
			step("H1 every glyph and Hearthside sprite resolves", this::spritesResolve);

			// The lobby and in-world pad blocks are only steps: with none of theirs selected, each reports SKIP and
			// nothing else happens.
			if (Boolean.getBoolean("hearthside.e2e.fakePads")) {
				step("B7 the title screen has a Co-op button that opens the lobby", this::titleCoopOpensLobby);
				step("T3 the title screen draws Hearthside's background and buttons", this::titleStyled);
				step("T4 with Minecraft in the background, a controller does nothing on the title screen", this::titleIgnoresBackgroundPad);
				step("T1 a controller moves focus on the title screen and A on Co-op opens the lobby", this::titleControllerOpensLobby);
				step("T2 the controller that opened Co-op is player 1's, not seated, and remembered", this::titleControllerIsPlayerOne);
				step("L5 player 1's card claims a controller; it drives the host and is not seated", this::hostCardClaims);
				step("W3 a controller creates a Creative world named on the keyboard; it loads; it is cleaned up", this::createWorldWithController);
				step("W4 the picker scrolls down to Create past worlds that don't fit; Down stops there", this::pickerScrolls);
				step("W5 a world deleted in More… is gone from the picker on return", this::pickerRefreshesAfterMore);
				step("W6 B on New World goes back one screen, not two", this::newWorldBackOnce);
				step("W7 B on the name keyboard closes only the keyboard", this::keyboardBackOnce);
				step("W8 with no worlds yet the picker shows one Create card", this::pickerWithNoWorlds);
				step("W10 Cheats toggles with A, a click or left/right, and its row always says which", this::cheatsToggle);
				step("W11 the world picker's \"Last played with …\" line is warm ink, not an alarm red", this::playedWithInk);
				step("W9 back in the lobby with player 1's controller", this::ensureLobby);
				step("L6 that controller is player 1's again next time the lobby opens", this::hostControllerRemembered);
				step("B6 cancelling the lobby stops every guest's game", this::lobbyCancelStopsGuests);
				step("B4 lobby: A seats a controller and its game starts before any world", this::lobbySeatsAndPrewarms);
				step("L1 a seated controller's last profile is pre-selected on its card", this::cardPreselects);
				long lobbySounds = System.nanoTime();
				step("L2 Y and the D-pad cycle profiles on the card; A confirms", this::cardCyclesAndConfirms);
				step("L9 Y on a ready card picks another profile; B keeps the current one", this::cardSwitchesProfile);
				step("R1 X on a ready card → Controls: Jump rebound to Y, presets tried and back, saved to the profile and the guest",
						this::controlsRemap);
				step("R2 the Controls screen: one selected row, D-pad directions told apart", this::controlsSelectionAndDpad);
				step("L11 holding Y deletes an unused profile; a profile in use is refused; letting go early cancels",
						this::manageProfiles);
				step("B8 in the lobby a seated controller moves the host's cursor, not its own game", this::lobbySharedCursor);
				step("B10 a title screen while the party waits goes back to its lobby", this::orphanedTitleReturnsToLobby);
				step("B9 B on a seated controller gives up the seat", this::seatedBLeaves);
				step("L4 B steps back out of quick setup, then leaves the seat from the carousel", this::cardBackAndLeave);
				step("L8 unplugging mid-keyboard keeps the card; replugging resumes it", this::cardSurvivesUnplug);
				step("L10 two identical controllers never share a profile or a new name", this::twinsNeverClash);
				step("L3 New runs quick setup: A on the suggested name, skin and colour saves the profile", this::quickSetupAAA);
				step("L12 the typing card's keyboard shows every key it uses, and B closes only the keyboard", this::cardKeyboardLegend);
				step("B5 Menu on a seated controller opens the world picker", this::lobbyToPicker);
				step("W1 the world picker puts the last co-op world first, focused, with who played it", this::pickerFocusesLastCoopWorld);
				step("S1 Hearthside's screens, cards and keyboards make their warm sounds", () -> screensMakeSounds(lobbySounds));
				step("W2 A on it opens the world; the guests come in by name; the co-op record has them", this::pickerOpensWorld);
			}
			if (selection.anyRuns(between("1", "6"))) {
				if (onRender(() -> minecraft.level == null)) {
					openWorld();
				}
				waitFor("host session", Duration.ofSeconds(180), () -> Party.current() != null);
				hostWindowBefore = onRender(() -> WindowPlacer.bounds(minecraft.getWindow()));
			}
			step("1 guest joins", this::guestJoins);
			step("2 satellite loads Sodium and Iris", this::satelliteHasCompatMods);
			step("C1 guest window is hidden", this::guestWindowHidden);
			step("C2 guest frames reach the host's window", this::guestFramesComposited);
			step("Q1 each guest's heap is the configured size and its memory is logged", this::guestMemoryConfigured);
			step("V1 frames are shared zero-copy on this backend", this::zeroCopyOnThisBackend);
			step("V6 the guest's window is hidden on its backend", this::guestWindowHiddenOnItsBackend);
			step("O1 the overlay pass draws over every view", this::overlayDrawsOverViews);
			step("C3 host window is not moved or resized by split-screen", this::hostWindowUntouched);
			step("C4 host window resize re-lays out the guest", this::resizeRelaysOut);
			step("V2 views are the right way up", this::viewsUpright);
			step("V4 a resource reload while composited keeps every view composited", this::reloadKeepsViews);
			step("V5 composite cost within budget", this::compositeCostWithinBudget);
			step("V7 minimise, restore and fullscreen keep views composited", this::minimiseRestoreFullscreen);
			step("V8 a broken view shader falls back to a plain blit", this::brokenViewShaderFallsBack);
			step("C5 copy transport works", this::copyTransportWorks);
			step("C5b two quick resizes on the copy transport keep the guest composited", this::quickResizesOnCopy);
			step("C6 maximise and restore keep the guest composited", () -> sampleGaps(this::maximiseAndRestore));
			step("C12 a failed GPU import falls back to copying frames", this::failedImportFallsBackToCopy);
			step("V3 a device mismatch copies frames and says why", this::deviceMismatchCopies);
			step("V9 a game without Vulkan copies frames instead of crashing", this::noVulkanCopies);
			step("C7 a third player joins and the layout makes room without GL errors", () -> sampleGaps(this::thirdPlayerJoins));
			step("D4 with three players the free quarter shows the party map", this::partyPanelShows);
			step("C7b a fourth player joins the four-view layout", () -> sampleGaps(this::fourthJoins));
			step("V10 with four players the guests drop almost no frames", this::fourPlayersDropAlmostNothing);
			step("D5 a leaving view fades out, then the others glide", () -> sampleGaps(this::leavingFades));
			step("D6 a guest who quits from their own game leaves the party", () -> sampleGaps(this::guestQuitsItself));
			step("C7c back to two players, re-laid out without GL errors", this::backToTwo);
			step("G1 no gap or brown shows inside a view while a player joins and leaves", this::noGapsSampled);
			step("C9 the host's mouse works only inside its own view", this::hostMouseConfined);
			step("C11 in the world, the host's clicks count wherever the cursor drifted", this::hostWorldClicksAnywhere);
			step("3 crashed satellite is restarted", this::crashedSatelliteRestarts);
			step("C8 the restarted guest's frames come back", this::restartedGuestComposited);
			step("4 guest named like the host is refused", this::hostNameRefused);
			step("4b opening and closing LAN keeps the guest", this::lanToggleKeepsGuest);
			long beforeLastLeave = hostLogLength();
			step("5 leave closes the satellite and leaves the host window as it was", this::leaveClosesSatellite);
			step("C10 with the last guest gone the host renders full-window again", () -> fullWindowAgain(beforeLastLeave));
			step("D7 a guest whose game gives up is shown, then removed", this::gaveUpShownThenRemoved);
			step("D8 a guest whose game dies while joining keeps their card on the plank wall", this::gaveUpWhileJoining);
			if (Boolean.getBoolean("hearthside.e2e.fakePads")) {
				step("P0 Player2 profile rebinds JUMP to Y", this::rebindPlayer2Jump);
				long dropInSounds = System.nanoTime();
				step("B2 in a world, one A on a new controller does not join", this::oneADoesNotJoin);
				step("D3 the drop-in toast shows that controller's own button and slides", this::toastShowsTheControllersButton);
				step("B3 the join prompt expires after 5 s", this::joinPromptExpires);
				step("D2 a newcomer picks their profile inside their own view", this::pickInOwnView);
				step("D1 a joining guest's view shows the card, never its loading screens, until it is in the world", this::joiningShowsTheCard);
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
				step("S2 the join prompt, a drop-in and a leave make their warm sounds", () -> dropInMakesSounds(dropInSounds));
			}
			step("6 closing the world closes satellites", this::closingWorldClosesSatellites);

			if (selection.anyRuns(List.of("D9", "7"))) {
				if (onRender(() -> minecraft.level != null)) {
					// Step 6 closes the first world; a focused run that skipped it closes it here.
					onRender(() -> {
						minecraft.disconnectFromWorld(Component.literal("E2E"));
						return null;
					});
					waitFor("back on a menu", Duration.ofSeconds(60),
							() -> onRender(() -> minecraft.level == null && minecraft.getSingleplayerServer() == null));
				}
				if (Boolean.getBoolean("hearthside.e2e.fakePads")) {
					// The last session tiles real windows, as on a machine that can't composite (D9 and step 7 run in it).
					System.setProperty("hearthside.e2e.forceTiled", "true");
				}
				openWorld();
				waitFor("host session", Duration.ofSeconds(180), () -> Party.current() != null);
			}
			if (Boolean.getBoolean("hearthside.e2e.fakePads")) {
				step("D9 with tiled windows, a drop-in joins as its controller's profile, with no card", this::tiledDropIn);
			}
			if (!selection.runs("7")) {
				log("SKIP " + HOST_KILL);
				log("DONE");
				return;
			}
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

	/** Where the host log stood when the last guest was asked in (its "seat N in world" line comes after). */
	private long joinMark;

	/** Waits for the host to hear from a guest's game that it is in the world (past its loading screens). */
	private void waitInWorld(int seat) throws Exception {
		waitFor("seat " + seat + " in world", Duration.ofSeconds(120),
				() -> hostLogSince(joinMark).contains("hearthside seat " + seat + " in world"));
	}

	/** Saves the whole window as players see it (every view and Hearthside's overlay), unlike a game screenshot. */
	private void windowShot(String name) throws Exception {
		Path file = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(name + ".png");
		Files.deleteIfExists(file);
		Compositor.e2eCapture = file;
		waitFor("window capture " + name, Duration.ofSeconds(5), () -> Files.exists(file) && Files.size(file) > 0);
		Thread.sleep(200);
	}

	private void guestJoins() throws Exception {
		joinMark = hostLogLength();
		onRender(() -> {
			Party.current().join(GUEST);
			return null;
		});
		Thread.sleep(3000);
		windowShot("joining");
		waitFor("2 players", Duration.ofSeconds(180), () -> playerCount() == 2);
	}

	// ---- Compositor steps ----

	/**
	 * X1: memory exported by the export device and imported twice through this game's backend is one image: magenta
	 * cleared through one import reads back through the other. An image whose description doesn't match this game's is
	 * refused (Spec C section 3.5 and Review Focus 4).
	 */
	private void sharedImageRoundTrips() throws Exception {
		long mark = hostLogLength();
		onRender(() -> {
			SharedImageCheck.start();
			return null;
		});
		// The readback is asynchronous: the result is logged from its callback a frame or two later.
		waitFor("shared image round trip", Duration.ofSeconds(15),
				() -> hostLogSince(mark).contains("hearthside shared image round trip backend="));
		String log = hostLogSince(mark);
		check(Pattern.compile("hearthside shared image round trip backend=(opengl|vulkan) rgb=ff00ff").matcher(log).find(),
				"magenta cleared through one import reads back through the other (got: "
						+ log.lines().filter(l -> l.contains("round trip")).findFirst().orElse("nothing") + ")");
		check(log.contains("hearthside shared image mismatch refused"),
				"an image whose description doesn't match this game's is refused, not imported");
		check(!log.contains("validation error"), "no Vulkan validation errors");
		check(!log.contains("hearthside shared image check failed"), "the check ran to the end");
	}

	/** X1b: the import compares Minecraft's real image settings with the shared image's description (final review I1). */
	private void realCreateInfoMismatchRefused() throws Exception {
		boolean vulkan = hostLogSince(0).contains("hearthside graphics backend=vulkan")
				|| onRender(() -> RenderSystem.getDevice().getDeviceInfo().backendName()).equals("Vulkan");
		if (!vulkan) {
			log("   OpenGL imports have no create info to compare; Vulkan only");
			return;
		}
		long mark = hostLogLength();
		System.setProperty("hearthside.e2e.perturbImportCreateInfo", "true");
		try {
			onRender(() -> {
				SharedImageCheck.importOnce();
				return null;
			});
			waitFor("the import's outcome", Duration.ofSeconds(10), () -> {
				String log = hostLogSince(mark);
				return log.contains("hearthside shared image mismatch refused") || log.contains("hearthside shared image imported");
			});
		} finally {
			System.clearProperty("hearthside.e2e.perturbImportCreateInfo");
		}
		String log = hostLogSince(mark);
		check(log.contains("hearthside shared image mismatch refused") && !log.contains("hearthside shared image imported"),
				"an import whose real create info differs is refused, not imported");
		check(!log.contains("validation error"), "no Vulkan validation errors");
	}

	private static final String PATTERN = "rgb=ff00ff";

	private void guestWindowHidden() throws Exception {
		waitFor("satellite window flags line", Duration.ofSeconds(30),
				() -> satelliteLog().contains("hearthside window flags hidden=true"));
	}

	/** The guest paints its frame's top-left corner magenta in E2E; the host logs what it composited there. */
	private void guestFramesComposited() throws Exception {
		waitInWorld(1);
		waitForProbe(2, hostLogLength(), Duration.ofSeconds(30));
		windowShot("two-players");
		check(!satelliteLog().contains("hearthside loading overlay over a live view"),
				"the guest's view went live only once Minecraft's loading screen was gone");
		check(satelliteLog().contains("hearthside skipped the chat-verification toast"),
				"a guest arriving isn't greeted with Minecraft's chat-verification warning");
	}

	/** V1: on a GPU that can share, the guest's frames reach the host zero-copy, whatever the backend (Spec C 3.5). */
	private void zeroCopyOnThisBackend() throws Exception {
		String host = hostLogSince(0);
		Matcher backend = Pattern.compile("hearthside graphics backend=(opengl|vulkan)").matcher(host);
		check(backend.find(), "the host logged its graphics backend");
		String expected = "frames gpu_shared";
		Matcher frames = Pattern.compile("hearthside compositor: player 2 frames ([a-z_]+) [0-9]+x[0-9]+ backend=([a-z]+)").matcher(host);
		String last = null;
		while (frames.find()) {
			last = frames.group(0);
		}
		log("   " + last);
		check(last != null && last.contains(expected) && last.endsWith("backend=" + backend.group(1)),
				"player 2's frames are shared, not copied, on a " + backend.group(1) + " host");
	}

	/** Q1: the guest's heap is the configured 1536 MB, its render distance is capped at 8, and memory is logged (Task 8b). */
	private void guestMemoryConfigured() throws Exception {
		waitFor("the guest reported its heap", Duration.ofSeconds(30), () -> satelliteLog().contains("hearthside guest heap max="));
		Matcher m = Pattern.compile("hearthside guest heap max=([0-9]+) mb render distance=([0-9]+)").matcher(satelliteLog());
		check(m.find(), "the guest logged its heap and render distance");
		long maxMb = Long.parseLong(m.group(1));
		int renderDistance = Integer.parseInt(m.group(2));
		check(maxMb <= 1536 * 105 / 100, "the guest's max heap " + maxMb + " MB is the configured 1536 MB");
		check(renderDistance <= 8, "the guest's render distance " + renderDistance + " is capped at 8");
		log("   memory host=" + workingSetMb(ProcessHandle.current().pid()) + " MB guest="
				+ workingSetMb(satellites().getFirst().pid()) + " MB");
	}

	/** A process's resident memory in MB: PowerShell's working set on Windows, ps's RSS on macOS. */
	private static long workingSetMb(long pid) throws Exception {
		boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
		Process p = new ProcessBuilder(windows
				? List.of("powershell", "-NoProfile", "-Command", "(Get-Process -Id " + pid + ").WorkingSet64")
				: List.of("ps", "-o", "rss=", "-p", Long.toString(pid))).redirectErrorStream(true).start();
		String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
		p.waitFor();
		long value = Long.parseLong(out.lines().reduce((a, b) -> b).orElse("0").trim());
		return windows ? value / (1024 * 1024) : value / 1024;
	}

	/** V6: the guest's window is created hidden whichever backend its game started on (Spec C 3.4). */
	private void guestWindowHiddenOnItsBackend() throws Exception {
		waitFor("satellite window flags line", Duration.ofSeconds(30),
				() -> satelliteLog().contains("hearthside window flags hidden=true"));
		Matcher m = Pattern.compile("using graphics backend (opengl|vulkan)").matcher(satelliteLog());
		check(m.find(), "the guest's game logged its backend");
		log("   guest backend " + m.group(1));
	}

	private void overlayDrawsOverViews() throws Exception {
		int[] scale = {0};
		dev.hearthside.fabric.client.compositor.OverlayLayer square = (g, context) -> {
			g.fill(20, 20, 26, 26, 0xFF00FFFF);
			scale[0] = context.guiScale();
		};
		dev.hearthside.fabric.client.compositor.OverlayPass.register(square);
		try {
			waitFor("the overlay layer drawn", Duration.ofSeconds(5), () -> scale[0] > 0);
			long mark = hostLogLength();
			Compositor.e2eProbePoint = new int[] {23 * scale[0], 23 * scale[0]};
			waitFor("the overlay over the views", Duration.ofSeconds(10),
					() -> hostLogSince(mark).contains("hearthside overlay probe rgb=00ffff"));
		} finally {
			dev.hearthside.fabric.client.compositor.OverlayPass.unregister(square);
			Compositor.e2eProbePoint = null;
		}
	}

	private void hostWindowUntouched() throws Exception {
		Rect now = onRender(() -> WindowPlacer.bounds(minecraft.getWindow()));
		log("   host window " + now + ", before split-screen " + hostWindowBefore);
		check(now.equals(hostWindowBefore), "host window unchanged by split-screen");
	}

	private void resizeRelaysOut() throws Exception {
		long mark = hostLogLength();
		onRender(() -> SDLVideo.SDL_SetWindowSize(minecraft.getWindow().handle(), 1280, 720));
		// Until the guest's frames at its new size arrive, its old ones are drawn stretched and softly blurred.
		windowShot("resize-stale");
		try {
			int halfWidth = onRender(() -> {
					var size = minecraft.getWindow().queryFramebufferSize();
					return dev.hearthside.core.layout.SplitLayout.plan(2, new Rect(0, 0, size.width(), size.height()),
							dev.hearthside.core.layout.LayoutSettings.DEFAULTS).players().get(1).width();
			});
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

	/** V2: the guest's magenta corner lands at the top-left of its view, and the bottom-left is the game (Spec C 4.4). */
	private void viewsUpright() throws Exception {
		long mark = hostLogLength();
		waitForProbe(2, mark, Duration.ofSeconds(10));
		Matcher m = Pattern.compile("hearthside compositor probe player=2 gen=[0-9]+ rgb=([0-9a-f]{6}) corner=([0-9a-f]{6})")
				.matcher(hostLogSince(mark));
		String last = null;
		while (m.find()) {
			last = "rgb=" + m.group(1) + " corner=" + m.group(2);
		}
		log("   probe " + last);
	}

	/** V4: a resource reload rebuilds Minecraft's pipelines; the views stay composited and ours is warmed again. */
	private void reloadKeepsViews() throws Exception {
		long mark = hostLogLength();
		onRender(() -> minecraft.reloadResourcePacks()).get(60, TimeUnit.SECONDS);
		check(hostLogSince(mark).contains("hearthside view pipeline warmed"), "the view pipeline was warmed again after the reload");
		waitForProbe(2, hostLogLength(), Duration.ofSeconds(15));
	}

	/** V5: the host's CPU time to composite stays within Spec C's budget per guest (zero-copy 0.3 ms, copying 1 ms). */
	private void compositeCostWithinBudget() throws Exception {
		long mark = hostLogLength();
		// The cost line is logged every 30 s; take the first one from here on.
		waitFor("a compositor cost line", Duration.ofSeconds(45), () -> hostLogSince(mark).contains("hearthside compositor cost"));
		Matcher m = Pattern.compile("hearthside compositor cost guests=([0-9]+) p50=([0-9.]+) ms p95=([0-9.]+) ms")
				.matcher(hostLogSince(mark));
		check(m.find(), "the cost line parses");
		int guests = Math.max(1, Integer.parseInt(m.group(1)));
		double p95 = Double.parseDouble(m.group(3));
		Matcher transport = Pattern.compile("hearthside compositor: player 2 frames ([a-z_]+)").matcher(hostLogSince(0));
		String last = "gpu_shared";
		while (transport.find()) {
			last = transport.group(1);
		}
		// Spec C's budget is for normal play; Vulkan's validation layer checks every call and roughly doubles the cost.
		boolean validating = hostLogSince(0).contains("enabling vulkan validation layers");
		double budget = ("copy".equals(last) ? 1.0 : 0.3) * guests * (validating ? 2 : 1);
		log("   compositor cost guests=" + guests + " p95=" + p95 + " ms, budget " + budget + " ms (" + last
				+ (validating ? ", validation on" : "") + ")");
		check(p95 < budget, "p95 " + p95 + " ms is within the " + budget + " ms budget");
	}

	/** V7: minimising, restoring and going fullscreen and back keep every view composited (Review Focus 1). */
	private void minimiseRestoreFullscreen() throws Exception {
		long mark = hostLogLength();
		long handle = onRender(() -> minecraft.getWindow().handle());
		onRender(() -> SDLVideo.SDL_MinimizeWindow(handle));
		Thread.sleep(2000);
		onRender(() -> SDLVideo.SDL_RestoreWindow(handle));
		waitForProbe(2, hostLogLength(), Duration.ofSeconds(10));
		onRender(() -> {
			toggleFullscreen();
			return null;
		});
		try {
			waitForProbe(2, hostLogLength(), Duration.ofSeconds(15));
		} finally {
			onRender(() -> {
				toggleFullscreen();
				return null;
			});
		}
		waitFor("window back to its size", Duration.ofSeconds(10),
				() -> onRender(() -> minecraft.getWindow().queryFramebufferSize().width()) == hostWindowBefore.width());
		waitForProbe(2, hostLogLength(), Duration.ofSeconds(15));
		check(!hostLogSince(mark).contains("exception"), "no exception in the host log");
	}

	/** What Minecraft's fullscreen key does (26.3's {@code Window} has no toggle), without saving the setting. */
	private void toggleFullscreen() {
		minecraft.options.fullscreen().set(!minecraft.options.fullscreen().get());
	}

	/** V8: a resource pack with a broken view shader leaves every view drawn, and is only compiled once (Review Focus 3). */
	private void brokenViewShaderFallsBack() throws Exception {
		long mark = hostLogLength();
		Collection<String> before = onRender(() -> List.copyOf(minecraft.getResourcePackRepository().getSelectedIds()));
		BrokenViewPack.write(minecraft.gameDirectory.toPath());
		try {
			onRender(() -> BrokenViewPack.select(minecraft, true)).get(120, TimeUnit.SECONDS);
			waitForProbe(2, hostLogLength(), Duration.ofSeconds(15));
			Thread.sleep(3000); // a few hundred frames on the fallback
			String log = hostLogSince(mark);
			int warnings = log.split("hearthside view pipeline unavailable; drawing views without fades", -1).length - 1;
			check(warnings == 1, "the fallback was logged once, not " + warnings + " times");
			int compiles = log.split("hearthside view pipeline compiling", -1).length - 1;
			check(compiles <= 2, "the broken shader was compiled once per reload, not every frame (" + compiles + " compiles)");
		} finally {
			onRender(() -> BrokenViewPack.restore(minecraft, before)).get(120, TimeUnit.SECONDS);
		}
		long restored = hostLogLength();
		waitForProbe(2, restored, Duration.ofSeconds(15));
		check(hostLogSince(mark).contains("hearthside view pipeline warmed"), "the view pipeline is back after the pack is removed");
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

	/** C5b: two resizes in quick succession while copying frames: readbacks still in flight are dropped (Review Focus 2). */
	private void quickResizesOnCopy() throws Exception {
		check(satelliteLog().contains("hearthside frame slots ready transport=copy"), "the guest is copying frames (after C5)");
		long mark = hostLogLength();
		int satMark = satelliteLog().length();
		int before = lastProbeGeneration(2);
		long handle = onRender(() -> minecraft.getWindow().handle());
		onRender(() -> SDLVideo.SDL_SetWindowSize(handle, 1280, 720));
		Thread.sleep(150);
		onRender(() -> SDLVideo.SDL_SetWindowSize(handle, hostWindowBefore.width(), hostWindowBefore.height()));
		waitForProbe(2, hostLogLength(), Duration.ofSeconds(20), before);
		check(!hostLogSince(mark).contains("exception"), "no exception in the host log");
		check(!satelliteLog().substring(satMark).contains("exception"), "no exception in the guest's log");
	}

	/** Where the host log stood when gap sampling first started; G1 reads every gap line after it. */
	private long gapMark = -1;

	/** Runs a step with the compositor checking every view's centre each frame for the bare divider colour. */
	private void sampleGaps(ThrowingRunnable body) throws Exception {
		if (gapMark < 0) {
			gapMark = hostLogLength();
		}
		Compositor.e2eGapSampling = true;
		try {
			body.run();
		} finally {
			Compositor.e2eGapSampling = false;
		}
	}

	private void noGapsSampled() throws Exception {
		check(gapMark >= 0, "gaps were sampled");
		String log = hostLogSince(gapMark);
		int gaps = log.split("hearthside compositor gap", -1).length - 1;
		check(gaps == 0, "no view showed the bare divider colour (" + gaps + " sampled gaps)");
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

	/** V3: when a guest's GPU or driver differs from the host's, its frames are copied and the host says why (Spec C 3.7). */
	private void deviceMismatchCopies() throws Exception {
		System.setProperty("hearthside.e2e.fakeDeviceMismatch", "true");
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
			waitFor("the host chose copying, with the reason", Duration.ofSeconds(60),
					() -> hostLogSince(mark).contains("copies frames: device_mismatch"));
			waitFor("satellite on the copy transport", Duration.ofSeconds(30),
					() -> satelliteLog().contains("hearthside frame slots ready transport=copy"));
			waitForProbe(2, hostLogLength(), Duration.ofSeconds(30));
		} finally {
			System.clearProperty("hearthside.e2e.fakeDeviceMismatch");
		}
	}

	/** V9: a guest whose machine has no Vulkan runtime copies its frames instead of crashing (final review M6). */
	private void noVulkanCopies() throws Exception {
		System.setProperty("hearthside.e2e.noVulkanLoader", "true");
		try {
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
			waitFor("satellite on the copy transport", Duration.ofSeconds(30),
					() -> satelliteLog().contains("hearthside frame slots ready transport=copy"));
			waitForProbe(2, hostLogLength(), Duration.ofSeconds(30));
			check(!satelliteLog().contains("unsatisfiedlinkerror") || satelliteLog().contains("copying frames instead"),
					"the guest caught the missing Vulkan runtime and copies frames");
		} finally {
			System.clearProperty("hearthside.e2e.noVulkanLoader");
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

	private long sevenMark;

	/** C7: a third player joins (and a fresh pad's join toast over split play). */
	private void thirdPlayerJoins() throws Exception {
		sevenMark = hostLogLength();
		joinMark = sevenMark;
		onRender(() -> {
			Party.current().join("Guest3");
			return null;
		});
		waitFor("3 players", Duration.ofSeconds(180), () -> playerCount() == 3);
		waitInWorld(2);
		waitForProbe(3, sevenMark, Duration.ofSeconds(30));
		waitForProbe(2, sevenMark, Duration.ofSeconds(10));
		// The join toast over split play (a pad nobody uses yet presses A once).
		PADS.plug(110, "fake-toast-pad", PadStyle.XBOX);
		Thread.sleep(300);
		press(110, PadButton.A);
		Thread.sleep(400);
		windowShot("toast-split");
		PADS.unplug(110);
	}

	/** D4: with three players the free quarter shows the party panel and its live map. */
	private void partyPanelShows() throws Exception {
		waitFor("the party map filled in", Duration.ofSeconds(5), () -> onRender(() -> Party.current().mapFilled()) >= 0.4f);
		check(hostLogSince(sevenMark).contains("hearthside party map markers=3"), "an arrow for each of the three players");
		Rect panel = dev.hearthside.fabric.client.ui.PartyPanel.lastPanel;
		Rect bounds = dev.hearthside.fabric.client.ui.PartyPanel.lastBounds;
		check(panel != null && bounds != null && panel.contains(bounds), "everything on the panel stays inside its quarter");
		Thread.sleep(4500); // past the name tags, for a calm picture
		windowShot("three-players");
	}

	/** C7b: a fourth player for the four-view layout. */
	private void fourthJoins() throws Exception {
		joinMark = hostLogLength();
		onRender(() -> {
			Party.current().join("Guest4");
			return null;
		});
		waitFor("4 players", Duration.ofSeconds(180), () -> playerCount() == 4);
		waitInWorld(3);
		Thread.sleep(1200);
		windowShot("four-players");
	}

	/** V10: with four players each guest finds a free slot for nearly every frame (final review I2). */
	private void fourPlayersDropAlmostNothing() throws Exception {
		Thread.sleep(11_000); // two exporter reports
		Pattern line = Pattern.compile("hearthside exporter fps=([0-9]+) dropped=([0-9]+)");
		for (int seat = 1; seat <= 3; seat++) {
			Path file = minecraft.gameDirectory.toPath().resolve("hearthside/satellite-" + seat + "/logs/latest.log");
			Matcher m = line.matcher(Files.readString(file).toLowerCase(Locale.ROOT));
			long fps = 0, dropped = 0;
			while (m.find()) {
				fps = Long.parseLong(m.group(1));
				dropped = Long.parseLong(m.group(2));
			}
			log("   guest " + seat + ": fps " + fps + ", dropped " + dropped + " in 5 s");
			check(fps > 0, "guest " + seat + " reported its frame rate");
			check(dropped * 100 <= fps * 5, "guest " + seat + " dropped at most 1% of its frames (" + dropped + ")");
		}
	}

	/** D5: Guest4 leaves: their view fades out where it is, then the others glide into its space. */
	private void leavingFades() throws Exception {
		long mark = hostLogLength();
		onRender(() -> {
			Party.current().leave("Guest4");
			return null;
		});
		Thread.sleep(100);
		windowShot("leaving");
		String settled = waitForSettle(mark);
		int ms = Integer.parseInt(settled);
		log("   the others settled " + ms + " ms after the leave");
		check(ms >= 450 && ms <= 650, "the others reach their new places 550 ms (±100) after the leave");
		// It faded rather than vanished: the compositor drew it at several alphas on the way out.
		Matcher fade = Pattern.compile("hearthside compositor fade seat=3 alpha=([0-9.]+)").matcher(hostLogSince(mark));
		java.util.Set<String> alphas = new java.util.TreeSet<>();
		while (fade.find()) {
			float alpha = Float.parseFloat(fade.group(1));
			if (alpha > 0.05f && alpha < 0.95f) {
				alphas.add(fade.group(1));
			}
		}
		log("   leaving alphas " + alphas);
		check(alphas.size() >= 3, "the leaving view was drawn fading out (" + alphas.size() + " alphas between 0 and 1)");
		waitFor("3 players", Duration.ofSeconds(30), () -> playerCount() == 3);
		check(onRender(() -> !Compositor.active().hasFrame(3)), "the leaving view is gone once it has faded");
		checkPanelFaded(mark, true);
	}

	/** Waits for the host's "layout settled" line after {@code mark}; returns its milliseconds. */
	private String waitForSettle(long mark) throws Exception {
		Pattern line = Pattern.compile("hearthside layout settled ([0-9]+) ms after a leave");
		String[] found = {null};
		waitFor("the layout settled after the leave", Duration.ofSeconds(10), () -> {
			Matcher m = line.matcher(hostLogSince(mark));
			if (m.find()) {
				found[0] = m.group(1);
			}
			return found[0] != null;
		});
		return found[0];
	}

	/** D6: Guest3's own game leaves the world (as its pause menu would): the party lets them go. */
	private void guestQuitsItself() throws Exception {
		long mark = hostLogLength();
		int before = satellites().size();
		onRender(() -> {
			Party.current().e2eDisconnectGuest(2);
			return null;
		});
		waitFor("seat 2 left its world", Duration.ofSeconds(30), () -> hostLogSince(mark).contains("hearthside seat 2 left its world"));
		windowShot("quit-fading");
		waitForSettle(mark);
		waitFor("2 players", Duration.ofSeconds(30), () -> playerCount() == 2);
		waitFor("its game closed", Duration.ofSeconds(15), () -> satellites().size() == before - 1);
		check(onRender(() -> Party.current().seatedCount()) == 1, "the seat is free");
		checkPanelFaded(mark, false);
	}

	/** The party panel was drawn at several alphas between 0 and 1 since {@code mark}, rising (in) or falling (out). */
	private void checkPanelFaded(long mark, boolean in) throws Exception {
		Matcher m = Pattern.compile("hearthside panel alpha=([0-9.]+)").matcher(hostLogSince(mark));
		List<Float> alphas = new ArrayList<>();
		while (m.find()) {
			float alpha = Float.parseFloat(m.group(1));
			if (alpha > 0.05f && alpha < 0.95f) {
				alphas.add(alpha);
			}
		}
		log("   panel alphas " + alphas);
		boolean direction = alphas.size() >= 2 && (in ? alphas.getFirst() < alphas.getLast() : alphas.getFirst() > alphas.getLast());
		check(direction, "the party panel faded " + (in ? "in" : "out") + " (" + alphas.size() + " alphas between 0 and 1)");
	}

	/** C7c: back to two (whoever is still here of Guest3 and Guest4 leaves), with no GL errors throughout. */
	private void backToTwo() throws Exception {
		onRender(() -> {
			for (String guest : List.of("Guest4", "Guest3")) {
				try {
					Party.current().leave(guest);
				} catch (IllegalArgumentException alreadyGone) {
					// it never came, or left already
				}
			}
			return null;
		});
		long back = hostLogLength();
		waitFor("back to 2 players", Duration.ofSeconds(30), () -> playerCount() == 2);
		waitForProbe(2, back, Duration.ofSeconds(10));
		// The E2E's own report lines mention "GL errors": look only at the game's lines.
		boolean glErrors = hostLogSince(sevenMark).lines().filter(l -> !l.contains("[e2e]") && !l.contains("e2e step failed"))
				.anyMatch(l -> l.contains("gl error"));
		check(!glErrors, "no GL errors while re-laying out");
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
		// The guest paints only its frame's top-left corner magenta: magenta there and not at the bottom-left means the
		// view is the right way up (Spec C section 4.4).
		Pattern line = Pattern.compile(
				"hearthside compositor probe player=" + player + " gen=([0-9]+) " + PATTERN + " corner=([0-9a-f]{6})");
		int[] found = {-1};
		waitFor("player " + player + "'s pattern in the host window" + (notGeneration < 0 ? "" : " from a new import"), timeout, () -> {
			Matcher m = line.matcher(hostLogSince(fromOffset));
			while (m.find()) {
				int generation = Integer.parseInt(m.group(1));
				if (generation != notGeneration && !m.group(2).equals("ff00ff")) {
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
		// Iris 1.11.7 calls OpenGL directly and crashes Minecraft on Vulkan, so Vulkan runs disable it (Spec C, Task 4).
		if (System.getProperty("fabric.debug.disableModIds", "").contains("iris")) {
			log("   iris is off in this run (it can't run on Vulkan)");
		} else {
			check(text.contains("iris"), "iris loaded");
		}
	}

	private void crashedSatelliteRestarts() throws Exception {
		generationBeforeCrash = lastProbeGeneration(2);
		satellites().forEach(ProcessHandle::destroyForcibly);
		waitFor("guest dropped", Duration.ofSeconds(30), () -> playerCount() == 1);
		waitFor("their card says they are reconnecting", Duration.ofSeconds(10),
				() -> onRender(() -> Party.current().viewPhaseOf(1)) == dev.hearthside.core.session.ViewPhase.RECONNECTING);
		Thread.sleep(1500); // the restarted game is starting up: its title and loading screens must not show
		windowShot("reconnecting");
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

	/**
	 * D8: a guest's game dies while it is still starting and the supervisor gives up: there is no picture of theirs
	 * to show, so their card says "Couldn't start" on the plank wall (never over their loading screens), then fades.
	 */
	private void gaveUpWhileJoining() throws Exception {
		System.setProperty("hearthside.e2e.failRestarts", "true");
		try {
			long mark = hostLogLength();
			onRender(() -> {
				Party.current().join(GUEST);
				return null;
			});
			waitFor("its game starting", Duration.ofSeconds(30), () -> !satellites().isEmpty());
			Thread.sleep(2000); // still starting up: nowhere near the world
			check(onRender(() -> Party.current().viewPhaseOf(1)) == dev.hearthside.core.session.ViewPhase.JOINING,
					"still joining");
			satellites().forEach(ProcessHandle::destroyForcibly);
			waitFor("the card says the game couldn't start", Duration.ofSeconds(30),
					() -> onRender(() -> Party.current().viewPhaseOf(1)) == dev.hearthside.core.session.ViewPhase.FAILED);
			check(!onRender(() -> Party.current().cardOverLastFrame(1)),
					"the card stays on the plank wall: they have no picture from the world to show");
			Thread.sleep(300);
			windowShot("couldnt-start-joining");
			waitForSettle(mark);
			check(onRender(() -> Party.current().seatedCount()) == 0, "the seat is free");
			waitFor("split-screen over", Duration.ofSeconds(10), () -> hostLogSince(mark).contains("virtual size cleared"));
		} finally {
			System.clearProperty("hearthside.e2e.failRestarts");
		}
	}

	private static final int TILED_PAD = 130;

	/** D9: with tiled windows nothing would draw a card, so A, A joins straight away as that controller's profile. */
	private void tiledDropIn() throws Exception {
		check(onRender(() -> Compositor.active() == null), "windows are tiled in this session");
		PADS.plug(TILED_PAD, "fake-tiled-pad", PadStyle.XBOX);
		Thread.sleep(300);
		try {
			press(TILED_PAD, PadButton.A);
			press(TILED_PAD, PadButton.A);
			waitFor("a seat taken", Duration.ofSeconds(10), () -> onRender(() -> Party.current().seatedCount()) == 1);
			check(onRender(() -> Party.current().seatCard(1)) == null, "no card: nothing would draw it");
			check(onRender(() -> Party.current().profileOf(1)) != null, "a profile was picked for the controller");
			waitFor("the newcomer in the world", Duration.ofSeconds(180), () -> playerCount() == 2);
		} finally {
			onRender(() -> {
				Party.current().leaveAll();
				return null;
			});
			PADS.unplug(TILED_PAD);
			waitFor("back to 1 player", Duration.ofSeconds(30), () -> playerCount() == 1);
		}
	}

	/** D7: a guest's game crashes and its supervisor gives up: their card says so, then their view fades out. */
	private void gaveUpShownThenRemoved() throws Exception {
		System.setProperty("hearthside.e2e.failRestarts", "true");
		try {
			joinMark = hostLogLength();
			onRender(() -> {
				Party.current().join(GUEST);
				return null;
			});
			waitFor("2 players", Duration.ofSeconds(180), () -> playerCount() == 2);
			waitInWorld(1);
			long mark = hostLogLength();
			satellites().forEach(ProcessHandle::destroyForcibly);
			waitFor("the card says the game couldn't start", Duration.ofSeconds(30),
					() -> onRender(() -> Party.current().viewPhaseOf(1)) == dev.hearthside.core.session.ViewPhase.FAILED);
			Thread.sleep(400);
			windowShot("couldnt-start");
			waitForSettle(mark);
			check(onRender(() -> Party.current().seatedCount()) == 0, "the seat is free");
			waitFor("split-screen over", Duration.ofSeconds(10), () -> hostLogSince(mark).contains("virtual size cleared"));
		} finally {
			System.clearProperty("hearthside.e2e.failRestarts");
		}
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
		// Last used on the drop-in pad, so its card shows Player2 first.
		profiles.add(new GuestProfile(UUID.randomUUID(), "Player2", SkinRef.defaultSkin("alex"), PAD_GUID,
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

	private void titleIgnoresBackgroundPad() throws Exception {
		PADS.plug(TITLE_PAD, TITLE_GUID, PadStyle.PLAYSTATION);
		onRender(() -> {
			minecraft.gui.setScreen(new TitleScreen());
			return null;
		});
		Thread.sleep(500);
		TitleInput.e2eBackground = true;
		try {
			Object before = onRender(() -> minecraft.gui.screen().getFocused());
			press(TITLE_PAD, PadButton.DPAD_DOWN);
			press(TITLE_PAD, PadButton.DPAD_DOWN);
			check(onRender(() -> minecraft.gui.screen() instanceof TitleScreen && minecraft.gui.screen().getFocused() == before),
					"the D-pad moved nothing while Minecraft was in the background");
		} finally {
			TitleInput.e2eBackground = false;
			PADS.unplug(TITLE_PAD);
		}
	}

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

	/** The screen draws its own hint chips, so the generic controller hints stay off it (no doubled chips). */
	private void checkOwnHintsOnly(String where) throws Exception {
		Screen screen = onRender(() -> minecraft.gui.screen());
		ButtonGuideRenderer.drewOn = null;
		Thread.sleep(300);
		check(ButtonGuideRenderer.drewOn != screen, "only " + where + "'s own hint chips are drawn");
	}

	/** Backs out of the picker, New World or Minecraft's world list to the lobby (never closing the lobby itself). */
	private void backToLobby() throws Exception {
		for (int i = 0; i < 4; i++) {
			boolean closed = onRender(() -> {
				Screen screen = minecraft.gui.screen();
				if (screen instanceof WorldsScreen || screen instanceof NewWorldScreen
						|| screen instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen) {
					screen.onClose();
					return true;
				}
				return false;
			});
			if (!closed) {
				return;
			}
			Thread.sleep(300);
		}
	}

	/** In the party's lobby with player 1's controller, whatever the step before left behind. */
	private void ensureLobby() throws Exception {
		backToLobby();
		if (!onRender(() -> Party.current() != null && minecraft.gui.screen() instanceof LobbyScreen)) {
			openLobby();
		}
		waitFor("player 1's controller in the lobby", Duration.ofSeconds(5),
				() -> onRender(() -> Party.current() != null && Party.current().hostHasController()));
		Thread.sleep(300);
	}

	private WorldsScreen openPicker() throws Exception {
		press(HOST_PAD, PadButton.START);
		waitFor("the world picker", Duration.ofSeconds(15),
				() -> onRender(() -> minecraft.gui.screen() instanceof WorldsScreen worlds && worlds.loaded()));
		return onRender(() -> (WorldsScreen) minecraft.gui.screen());
	}

	/** Adds listable copies of the E2E world (its level.dat only), to have more worlds than fit. */
	private List<Path> copyWorlds(int count) throws IOException {
		Path saves = minecraft.gameDirectory.toPath().resolve("saves");
		Path levelDat = saves.resolve(WORLD).resolve("level.dat");
		check(Files.exists(levelDat), "the E2E world exists to copy");
		List<Path> copies = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			Path dir = saves.resolve(WORLD + "-copy-" + i);
			Files.createDirectories(dir);
			Files.copy(levelDat, dir.resolve("level.dat"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			copies.add(dir);
		}
		return copies;
	}

	private static void deleteTree(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		try (var walk = Files.walk(dir)) {
			for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(p);
			}
		}
	}

	private void pickerScrolls() throws Exception {
		ensureLobby();
		List<Path> copies = copyWorlds(5);
		try {
			WorldsScreen picker = openPicker();
			check(onRender(picker::cardLevelIds).size() >= 7, "the picker lists every world and Create");
			pressUntil(HOST_PAD, PadButton.DPAD_DOWN, 20, () -> "".equals(picker.focusedLevelId()));
			check("".equals(onRender(picker::focusedLevelId)), "Down reaches Create past the worlds that don't fit");
			press(HOST_PAD, PadButton.DPAD_DOWN);
			press(HOST_PAD, PadButton.DPAD_DOWN);
			check("".equals(onRender(picker::focusedLevelId)), "Down stops at Create (focus never lands on a hint chip)");
		} finally {
			backToLobby();
			for (Path copy : copies) {
				deleteTree(copy);
			}
		}
	}

	private void pickerRefreshesAfterMore() throws Exception {
		ensureLobby();
		List<Path> copies = copyWorlds(1);
		String gone = copies.getFirst().getFileName().toString();
		try {
			WorldsScreen picker = openPicker();
			check(onRender(picker::cardLevelIds).contains(gone), "the copy is listed");
			press(HOST_PAD, PadButton.X);
			check(onRender(() -> minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen), "X opens Minecraft's world list");
			deleteTree(copies.getFirst());
			onRender(() -> {
				minecraft.gui.screen().onClose();
				return null;
			});
			waitFor("the picker again", Duration.ofSeconds(15),
					() -> onRender(() -> minecraft.gui.screen() instanceof WorldsScreen worlds && worlds.loaded()));
			check(!onRender(() -> ((WorldsScreen) minecraft.gui.screen()).cardLevelIds()).contains(gone),
					"the world deleted there is gone from the picker");
		} finally {
			backToLobby();
			deleteTree(copies.getFirst());
		}
	}

	/** From the lobby to New World with player 1's controller. */
	private NewWorldScreen openNewWorld() throws Exception {
		WorldsScreen picker = openPicker();
		pressUntil(HOST_PAD, PadButton.DPAD_DOWN, 12, () -> "".equals(picker.focusedLevelId()));
		press(HOST_PAD, PadButton.A);
		check(onRender(() -> minecraft.gui.screen() instanceof NewWorldScreen), "Create opens New World");
		return onRender(() -> (NewWorldScreen) minecraft.gui.screen());
	}

	private void newWorldBackOnce() throws Exception {
		ensureLobby();
		try {
			openNewWorld();
			press(HOST_PAD, PadButton.B);
			Thread.sleep(600);
			check(onRender(() -> minecraft.gui.screen() instanceof WorldsScreen), "one B is one screen back: the picker");
		} finally {
			backToLobby();
		}
	}

	private void keyboardBackOnce() throws Exception {
		ensureLobby();
		try {
			NewWorldScreen screen = openNewWorld();
			pressUntil(HOST_PAD, PadButton.DPAD_UP, 6, () -> screen.focusedRow().equals("name"));
			press(HOST_PAD, PadButton.A);
			check(onRender(screen::typing), "A on the name opens the keyboard");
			press(HOST_PAD, PadButton.B);
			Thread.sleep(600);
			check(onRender(() -> minecraft.gui.screen() == screen && !screen.typing()),
					"B closes the keyboard and stays on New World");
		} finally {
			backToLobby();
		}
	}

	private void pickerWithNoWorlds() throws Exception {
		ensureLobby();
		Path empty = Files.createTempDirectory("hearthside-no-worlds").resolve("saves");
		Files.createDirectories(empty);
		// Opened the way a player opens it (Menu, from the controller's frame hook), not from a queued task: an empty
		// list finishes loading at once only there.
		WorldsScreen.e2eSource = net.minecraft.world.level.storage.LevelStorageSource.createDefault(empty);
		try {
			press(HOST_PAD, PadButton.START);
			waitFor("the picker", Duration.ofSeconds(10),
					() -> onRender(() -> minecraft.gui.screen() instanceof WorldsScreen worlds && worlds.loaded()));
			long cards = onRender(() -> minecraft.gui.screen().children().stream()
					.filter(c -> c.getClass().getSimpleName().equals("WorldCard")).count());
			long chips = onRender(() -> minecraft.gui.screen().children().stream()
					.filter(dev.hearthside.fabric.client.ui.HintChip.class::isInstance).count());
			check(cards == 1 && chips == 3, "one Create card and one set of hints (cards " + cards + ", chips " + chips + ")");
			check("".equals(onRender(() -> ((WorldsScreen) minecraft.gui.screen()).focusedLevelId())), "Create is focused");
		} finally {
			WorldsScreen.e2eSource = null;
			backToLobby();
			deleteTree(empty.getParent());
		}
	}

	/** Moves player 1's mouse to a point given in GUI coordinates, and optionally clicks there. Render thread. */
	private void mouseAt(double guiX, double guiY, boolean click) {
		var window = minecraft.getWindow();
		double toScreen = (double) window.getScreenWidth() / window.getGuiScaledWidth();
		long handle = window.handle();
		minecraft.mouseHandler.onMove(handle, guiX * toScreen, guiY * toScreen, 0, 0);
		if (click) {
			minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), 1);
			minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), 0);
		}
	}

	private void cheatsToggle() throws Exception {
		ensureLobby();
		String levelId = null;
		try {
			NewWorldScreen screen = openNewWorld();
			pressUntil(HOST_PAD, PadButton.DPAD_UP, 4, () -> screen.focusedRow().equals("cheats"));
			check(onRender(screen::focusedRow).equals("cheats"), "the Cheats row is focused");
			check(onRender(screen::cheatsLabel).equals("Cheats:  ‹ Off ›") && !onRender(screen::cheats),
					"Cheats starts Off (" + onRender(screen::cheatsLabel) + ")");
			press(HOST_PAD, PadButton.A);
			check(onRender(screen::cheatsLabel).equals("Cheats:  ‹ On ›") && onRender(screen::cheats),
					"A turns Cheats on and the row says so (" + onRender(screen::cheatsLabel) + ")");
			onRender(() -> {
				var row = Screens.getWidgets(screen).stream().filter(w -> w.getMessage().getString().startsWith("Cheats"))
						.findFirst().orElseThrow();
				mouseAt(row.getX() + row.getWidth() / 2.0, row.getY() + row.getHeight() / 2.0, true);
				return null;
			});
			Thread.sleep(300);
			check(onRender(screen::cheatsLabel).equals("Cheats:  ‹ Off ›") && !onRender(screen::cheats),
					"a click turns it off and the row says so (" + onRender(screen::cheatsLabel) + ")");
			pressUntil(HOST_PAD, PadButton.DPAD_UP, 4, () -> screen.focusedRow().equals("cheats"));
			press(HOST_PAD, PadButton.DPAD_RIGHT);
			check(onRender(screen::cheatsLabel).equals("Cheats:  ‹ On ›") && onRender(screen::cheats), "right: On");
			press(HOST_PAD, PadButton.DPAD_LEFT);
			check(onRender(screen::cheatsLabel).equals("Cheats:  ‹ Off ›") && !onRender(screen::cheats), "left: Off");
			press(HOST_PAD, PadButton.DPAD_RIGHT);
			pressUntil(HOST_PAD, PadButton.DPAD_UP, 4, () -> screen.focusedRow().equals("mode"));
			press(HOST_PAD, PadButton.DPAD_LEFT);
			check(onRender(screen::mode) == NewWorldScreen.Mode.HARDCORE, "left on Mode from Survival is Hardcore");
			check(onRender(screen::cheatsLabel).equals("Cheats:  Off") && !onRender(screen::cheatsActive),
					"in Hardcore the row reads Off, greyed (" + onRender(screen::cheatsLabel) + ")");
			press(HOST_PAD, PadButton.DPAD_RIGHT);
			check(onRender(screen::mode) == NewWorldScreen.Mode.SURVIVAL, "right is Survival again");
			check(onRender(screen::cheatsLabel).equals("Cheats:  ‹ On ›") && onRender(screen::cheatsActive),
					"back in Survival the last value is back (" + onRender(screen::cheatsLabel) + ")");
			screenshot("new-world-cheats");
			pressUntil(HOST_PAD, PadButton.DPAD_DOWN, 6, () -> screen.focusedRow().equals("create"));
			press(HOST_PAD, PadButton.A);
			waitFor("the new world", Duration.ofSeconds(180), () -> onRender(() -> minecraft.level != null
					&& minecraft.getSingleplayerServer() != null && minecraft.player != null));
			levelId = onRender(() -> minecraft.getSingleplayerServer()
					.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize()
					.getFileName().toString());
			check(onRender(() -> minecraft.getSingleplayerServer().getWorldData().isAllowCommands()),
					"the world created with Cheats On allows commands");
		} finally {
			if (onRender(() -> minecraft.level != null)) {
				onRender(() -> {
					minecraft.disconnectFromWorld(Component.literal("E2E"));
					return null;
				});
				waitFor("back on a menu", Duration.ofSeconds(60),
						() -> onRender(() -> minecraft.level == null && minecraft.getSingleplayerServer() == null));
			}
			if (levelId != null) {
				String id = levelId;
				onRender(() -> {
					try (var access = minecraft.getLevelSource().createAccess(id)) {
						access.deleteLevel();
					}
					return null;
				});
				check(!onRender(() -> minecraft.getLevelSource().levelExists(id)), "the test world is deleted");
			}
			backToLobby();
			if (!onRender(() -> Party.current() != null && minecraft.gui.screen() instanceof LobbyScreen)) {
				openLobby();
			}
			waitFor("player 1's controller again", Duration.ofSeconds(5),
					() -> onRender(() -> Party.current() != null && Party.current().hostHasController()));
		}
	}

	private void playedWithInk() throws Exception {
		ensureLobby();
		check(onRender(() -> minecraft.getLevelSource().levelExists(WORLD)), "the E2E world exists (created by earlier runs)");
		Path record = minecraft.gameDirectory.toPath().resolve("hearthside/coop-worlds.json");
		byte[] before = Files.exists(record) ? Files.readAllBytes(record) : null;
		try {
			coopStore().played(WORLD, System.currentTimeMillis(), List.of("Ember", "Birch"));
			WorldsScreen picker = openPicker();
			check("Last played with Ember and Birch".equals(onRender(() -> picker.playedWithText(WORLD))),
					"the E2E world's card says who played it");
			Thread.sleep(300);
			int colour = onRender(() -> picker.playedWithColour(WORLD));
			int r = colour >> 16 & 0xFF, gr = colour >> 8 & 0xFF, b = colour & 0xFF;
			check(!(r >= 0xC0 && gr <= 0x50 && b <= 0x50) && !(r >= 0xA0 && gr <= 0x40 && b <= 0x40),
					"the line is not an alarm red (" + Integer.toHexString(colour) + ")");
			check(colour == dev.hearthside.fabric.client.ui.HearthDraw.MUTED,
					"it is Hearthside's warm ink for secondary text (" + Integer.toHexString(colour) + ")");
			screenshot("worlds-played-with");
		} finally {
			backToLobby();
			if (before == null) {
				Files.deleteIfExists(record);
			} else {
				Files.write(record, before);
			}
		}
	}

	/** Presses a direction until the condition holds (at most {@code tries} times). */
	private void pressUntil(int pad, PadButton button, int tries, Callable<Boolean> done) throws Exception {
		for (int i = 0; i < tries && !onRender(done); i++) {
			press(pad, button);
		}
	}

	private void createWorldWithController() throws Exception {
		check(onRender(() -> minecraft.gui.screen() instanceof LobbyScreen && Party.current().hostHasController()),
				"in the lobby with player 1's controller");
		checkOwnHintsOnly("the lobby");
		press(HOST_PAD, PadButton.START);
		waitFor("the world picker", Duration.ofSeconds(15),
				() -> onRender(() -> minecraft.gui.screen() instanceof WorldsScreen worlds && worlds.loaded()));
		check(onRender(() -> minecraft.gui.screen() instanceof WorldsScreen w
				&& w.cardLevelIds().getFirst().equals(w.focusedLevelId())), "a controller arrives focused on the first card");
		pressUntil(HOST_PAD, PadButton.DPAD_DOWN, 12,
				() -> minecraft.gui.screen() instanceof WorldsScreen w && "".equals(w.focusedLevelId()));
		press(HOST_PAD, PadButton.A);
		check(onRender(() -> minecraft.gui.screen() instanceof NewWorldScreen), "Create opens the new-world screen");
		NewWorldScreen screen = onRender(() -> (NewWorldScreen) minecraft.gui.screen());
		String suggested = onRender(screen::worldName);
		check(suggested.startsWith("Co-op World"), "a name is ready (" + suggested + ")");
		check(onRender(screen::focusedRow).equals("create"), "focus starts on Create");
		checkOwnHintsOnly("the new-world screen");
		HearthTextField.drewParchment = false;
		Thread.sleep(300);
		check(HearthTextField.drewParchment, "the name is on parchment, not vanilla's black box");
		screenshot("new-world");
		// The name on the on-screen keyboard: X deletes a letter, Menu keeps it.
		pressUntil(HOST_PAD, PadButton.DPAD_UP, 6, () -> screen.focusedRow().equals("name"));
		press(HOST_PAD, PadButton.A);
		check(onRender(screen::typing), "A on the name opens the keyboard");
		press(HOST_PAD, PadButton.X);
		press(HOST_PAD, PadButton.START);
		String name = onRender(screen::worldName);
		check(!onRender(screen::typing) && name.equals(suggested.substring(0, suggested.length() - 1)),
				"the keyboard changed the name (" + name + ")");
		pressUntil(HOST_PAD, PadButton.DPAD_DOWN, 4, () -> screen.focusedRow().equals("mode"));
		press(HOST_PAD, PadButton.DPAD_RIGHT);
		check(onRender(screen::mode) == NewWorldScreen.Mode.CREATIVE, "right on Mode picks Creative");
		pressUntil(HOST_PAD, PadButton.DPAD_DOWN, 6, () -> screen.focusedRow().equals("create"));
		press(HOST_PAD, PadButton.A);
		waitFor("the new world", Duration.ofSeconds(180), () -> onRender(() -> minecraft.level != null
				&& minecraft.getSingleplayerServer() != null && minecraft.player != null));
		String levelId = onRender(() -> minecraft.getSingleplayerServer()
				.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize()
				.getFileName().toString());
		check(onRender(() -> minecraft.getSingleplayerServer().getWorldData().getLevelName()).equals(name),
				"the world has the name typed");
		check(onRender(() -> minecraft.player.isCreative()), "and is Creative");
		// Mods' data (recipes, tags, worldgen) comes with the quick path too: every required pack is selected.
		log("   data packs: " + onRender(() -> minecraft.getSingleplayerServer().getPackRepository().getSelectedIds()));
		check(onRender(() -> {
			var packs = minecraft.getSingleplayerServer().getPackRepository();
			var selected = packs.getSelectedIds();
			return packs.getAvailablePacks().stream().filter(net.minecraft.server.packs.repository.Pack::isRequired)
					.allMatch(pack -> selected.contains(pack.getId()))
					&& selected.stream().anyMatch(id -> !id.equals("vanilla"));
		}), "the world has every mod's data pack");
		// Clean up: leave, delete it, and go back to the lobby for the next step.
		onRender(() -> {
			minecraft.disconnectFromWorld(Component.literal("E2E"));
			return null;
		});
		waitFor("back on a menu", Duration.ofSeconds(60),
				() -> onRender(() -> minecraft.level == null && minecraft.getSingleplayerServer() == null));
		onRender(() -> {
			try (var access = minecraft.getLevelSource().createAccess(levelId)) {
				access.deleteLevel();
			}
			return null;
		});
		check(!onRender(() -> minecraft.getLevelSource().levelExists(levelId)), "the test world is deleted");
		openLobby();
		waitFor("player 1's controller again", Duration.ofSeconds(5),
				() -> onRender(() -> Party.current() != null && Party.current().hostHasController()));
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

	private GuestProfile storedProfile(String name) throws IOException {
		return new ProfileStore(minecraft.gameDirectory.toPath().resolve("hearthside/profiles.json")).load().stream()
				.filter(p -> p.name().equals(name)).findFirst().orElseThrow();
	}

	private void controlsRemap() throws Exception {
		check(onRender(() -> minecraft.gui.screen().children().stream()
				.filter(dev.hearthside.fabric.client.ui.HintChip.class::isInstance).count()) == 2,
				"the lobby's footer has only Back and Start (a seat's own buttons are on its card)");
		screenshot("lobby-ready");
		press(LOBBY_PAD, PadButton.X);
		check(onRender(() -> card(1) != null && card(1).stage() == SeatCard.Stage.MORE), "X on the ready card opens More");
		check(onRender(() -> card(1).moreFocus()) == SeatCard.MoreEntry.CONTROLS, "More starts on Controls");
		check(onRender(() -> minecraft.gui.screen().getFocused()) == null,
				"nothing in the lobby looks selected (its controllers drive cards and the pointer, not focus)");
		screenshot("lobby-more");
		press(LOBBY_PAD, PadButton.A);
		check(onRender(() -> minecraft.gui.screen() instanceof ControlsScreen c && c.seat() == 1),
				"A on Controls opens that seat's controls");
		ControlsScreen screen = onRender(() -> (ControlsScreen) minecraft.gui.screen());
		check(onRender(screen::focusedAction) == Action.JUMP, "focus starts on Jump");
		checkOwnHintsOnly("the controls screen");
		press(LOBBY_PAD, PadButton.A);
		check(onRender(() -> screen.model().listening()) == Action.JUMP, "A waits for Jump's button");
		press(LOBBY_PAD, PadButton.Y);
		check(onRender(() -> screen.model().scheme().controlFor(Action.JUMP).orElse(null)) == Control.Y
				&& onRender(() -> screen.model().scheme().controlFor(Action.INVENTORY).orElse(null)) == Control.A,
				"Y is Jump now, and Inventory took A (a swap)");
		check(onRender(() -> screen.model().name()).equals("Custom"), "the layout is Custom");
		screenshot("controls");
		press(LOBBY_PAD, PadButton.RIGHT_BUMPER);
		press(LOBBY_PAD, PadButton.RIGHT_BUMPER);
		press(LOBBY_PAD, PadButton.RIGHT_BUMPER);
		check(onRender(() -> screen.model().name()).equals("Southpaw") && onRender(() -> screen.model().scheme().swapSticks()),
				"RB tries the presets: Southpaw swaps the sticks");
		press(LOBBY_PAD, PadButton.LEFT_BUMPER);
		press(LOBBY_PAD, PadButton.LEFT_BUMPER);
		press(LOBBY_PAD, PadButton.LEFT_BUMPER);
		check(onRender(() -> screen.model().name()).equals("Custom")
				&& onRender(() -> screen.model().scheme().controlFor(Action.JUMP).orElse(null)) == Control.Y,
				"LB comes back to the edited layout");
		press(LOBBY_PAD, PadButton.START);
		check(onRender(() -> minecraft.gui.screen() instanceof LobbyScreen && card(1) != null
				&& card(1).stage() == SeatCard.Stage.MORE), "Menu saves and goes back to the card's More");
		check(storedProfile("Birch").controls().controlFor(Action.JUMP).orElse(null) == Control.Y,
				"the profile is saved with Y for Jump");
		check(onRender(() -> Party.current().profileOf(1).controls().controlFor(Action.JUMP).orElse(null)) == Control.Y,
				"the seat plays with it");
		waitFor("the guest's game told", Duration.ofSeconds(10), () -> satelliteLog().contains("setcontrols jump=y"));
		press(LOBBY_PAD, PadButton.B);
		check(onRender(() -> card(1) == null && Party.current().seatView(1).status().equals("Ready")),
				"B in More goes back to the ready seat");
		check(storedProfile("Birch").controls().controlFor(Action.JUMP).orElse(null) == Control.Y,
				"leaving More keeps the saved controls");
		// Back to Bedrock for the steps after, through the screen again: RB from the saved layout is Bedrock.
		press(LOBBY_PAD, PadButton.X);
		press(LOBBY_PAD, PadButton.A);
		check(onRender(() -> minecraft.gui.screen() instanceof ControlsScreen), "the controls open again");
		press(LOBBY_PAD, PadButton.RIGHT_BUMPER);
		check(onRender(() -> ((ControlsScreen) minecraft.gui.screen()).model().name()).equals("Bedrock"),
				"RB from Custom is Bedrock");
		press(LOBBY_PAD, PadButton.START);
		press(LOBBY_PAD, PadButton.B);
		check(ControlScheme.presetName(storedProfile("Birch").controls()).orElse("").equals("Bedrock")
				&& onRender(() -> card(1) == null), "Birch is back on Bedrock and ready");
	}

	private void controlsSelectionAndDpad() throws Exception {
		press(LOBBY_PAD, PadButton.X);
		press(LOBBY_PAD, PadButton.A);
		check(onRender(() -> minecraft.gui.screen() instanceof ControlsScreen c && c.seat() == 1), "the seat's controls open");
		ControlsScreen screen = onRender(() -> (ControlsScreen) minecraft.gui.screen());
		try {
			press(LOBBY_PAD, PadButton.DPAD_DOWN);
			press(LOBBY_PAD, PadButton.DPAD_UP);
			check(onRender(screen::focusedAction) == Action.JUMP, "the pad focuses Jump");
			// The pointer over another row (Drop, in the same column).
			onRender(() -> {
				double[] centre = screen.rowCentre(Action.DROP);
				mouseAt(centre[0], centre[1], false);
				return null;
			});
			Thread.sleep(400);
			check(onRender(screen::hoveredAction) == Action.DROP, "the pointer is over Drop");
			check(onRender(screen::selectedRowCount) == 1 && onRender(screen::selectedAction) == Action.JUMP,
					"exactly one row draws as selected, the focused one (" + onRender(screen::selectedRowCount) + " drawn, "
							+ onRender(screen::selectedAction) + ")");
			screenshot("controls-selection");
			List<String> dpad = new ArrayList<>();
			for (Action action : List.of(Action.DROP, Action.PERSPECTIVE, Action.SWAP_OFFHAND, Action.CHAT)) {
				dpad.add(onRender(() -> screen.drawnGlyph(action)));
			}
			log("   D-pad glyphs: " + dpad);
			check(dpad.stream().distinct().count() == 4
					&& dpad.stream().allMatch(id -> id != null && id.matches("hearthside:glyph/playstation/dpad_(up|down|left|right)")),
					"the four D-pad bindings draw four different D-pad glyphs in the seat's family");
			check(onRender(() -> dev.hearthside.fabric.client.ui.HearthDraw.dpadArmsDrawn) > 0,
					"the pressed arm is painted in the accent at draw time");
		} finally {
			onRender(() -> {
				mouseAt(0, 0, false);
				return null;
			});
			if (onRender(() -> minecraft.gui.screen() == screen)) {
				press(LOBBY_PAD, PadButton.B);
			}
			if (onRender(() -> card(1) != null)) {
				press(LOBBY_PAD, PadButton.B);
			}
		}
		check(onRender(() -> minecraft.gui.screen() instanceof LobbyScreen && card(1) == null), "back on the ready seat");
	}

	/** Holds a button down for a while, then lets go. */
	private void hold(int pad, PadButton button, long millis) throws InterruptedException {
		PADS.set(pad, new InputFrame(0, InputFrame.buttons(button), 0, 0, 0, 0, 0, 0));
		Thread.sleep(millis);
		PADS.set(pad, InputFrame.NEUTRAL);
		Thread.sleep(150);
	}

	private boolean stored(String name) throws IOException {
		return new ProfileStore(minecraft.gameDirectory.toPath().resolve("hearthside/profiles.json")).load().stream()
				.anyMatch(p -> p.name().equals(name));
	}

	private void manageProfiles() throws Exception {
		// A spare profile nobody plays, first in the list.
		ProfileStore store = new ProfileStore(minecraft.gameDirectory.toPath().resolve("hearthside/profiles.json"));
		List<GuestProfile> saved = new ArrayList<>(store.load());
		saved.add(0, new GuestProfile(UUID.nameUUIDFromBytes("Spare".getBytes()), "Spare", SkinRef.defaultSkin("steve"),
				null));
		store.save(saved);
		press(LOBBY_PAD, PadButton.X);
		pressUntil(LOBBY_PAD, PadButton.DPAD_DOWN, 3, () -> card(1).moreFocus() == SeatCard.MoreEntry.MANAGE);
		press(LOBBY_PAD, PadButton.A);
		check(onRender(() -> minecraft.gui.screen() instanceof ManageProfilesScreen m && m.seat() == 1
				&& m.names().contains("Spare") && m.names().contains("Birch")), "Profiles lists the saved profiles");
		ManageProfilesScreen screen = onRender(() -> (ManageProfilesScreen) minecraft.gui.screen());
		checkOwnHintsOnly("the profiles screen");
		pressUntil(LOBBY_PAD, PadButton.DPAD_RIGHT, 8, () -> "Birch".equals(screen.focusedName()));
		hold(LOBBY_PAD, PadButton.Y, 2000);
		check(stored("Birch") && onRender(screen::problem) != null && onRender(screen::problem).contains("playing"),
				"Birch is seat 2's: holding Y is refused, and the card says why");
		// Still holding Y while moving to Spare must not start deleting it: a delete takes a fresh press.
		PADS.set(LOBBY_PAD, new InputFrame(0, InputFrame.buttons(PadButton.Y), 0, 0, 0, 0, 0, 0));
		Thread.sleep(300);
		for (int i = 0; i < 4 && !"Spare".equals(onRender(screen::focusedName)); i++) {
			PADS.set(LOBBY_PAD, new InputFrame(0, InputFrame.buttons(PadButton.Y, PadButton.DPAD_LEFT), 0, 0, 0, 0, 0, 0));
			Thread.sleep(250);
			PADS.set(LOBBY_PAD, new InputFrame(0, InputFrame.buttons(PadButton.Y), 0, 0, 0, 0, 0, 0));
			Thread.sleep(150);
		}
		Thread.sleep(2000);
		PADS.set(LOBBY_PAD, InputFrame.NEUTRAL);
		Thread.sleep(200);
		check(stored("Spare"), "a Y held over from another card deletes nothing");
		pressUntil(LOBBY_PAD, PadButton.DPAD_LEFT, 8, () -> "Spare".equals(screen.focusedName()));
		hold(LOBBY_PAD, PadButton.Y, 600);
		check(stored("Spare") && onRender(screen::progress) == 0, "letting go early keeps it");
		PADS.set(LOBBY_PAD, new InputFrame(0, InputFrame.buttons(PadButton.Y), 0, 0, 0, 0, 0, 0));
		Thread.sleep(300);
		screenshot("manage-profiles");
		Thread.sleep(1200);
		PADS.set(LOBBY_PAD, InputFrame.NEUTRAL);
		Thread.sleep(300);
		check(!stored("Spare") && !onRender(screen::names).contains("Spare"), "holding Y for 1.5 s deletes it");
		check(stored("Birch") && stored("Hazel"), "nothing else is deleted");
		press(LOBBY_PAD, PadButton.B);
		check(onRender(() -> minecraft.gui.screen() instanceof LobbyScreen && card(1) != null
				&& card(1).stage() == SeatCard.Stage.MORE), "B goes back to the card's More");
		press(LOBBY_PAD, PadButton.B);
		check(onRender(() -> card(1) == null && Party.current().seatView(1).status().equals("Ready")),
				"and B again to the ready seat");
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

	/** The glyphs the shared screen's hint chips draw, after a moment. */
	private java.util.Set<String> chipGlyphs() throws InterruptedException {
		dev.hearthside.fabric.client.ui.HintChip.drawn.clear();
		Thread.sleep(400);
		return new java.util.TreeSet<>(dev.hearthside.fabric.client.ui.HintChip.drawn);
	}

	private static boolean allFamily(java.util.Set<String> glyphs, String family) {
		return !glyphs.isEmpty() && glyphs.stream().allMatch(id -> id.startsWith("hearthside:glyph/" + family + "/"));
	}

	private void cardKeyboardLegend() throws Exception {
		String made = onRender(() -> Party.current().seatView(1).name());
		LobbyScreen lobby = onRender(() -> (LobbyScreen) minecraft.gui.screen());
		List<Object> chips = onRender(() -> lobby.children().stream()
				.filter(dev.hearthside.fabric.client.ui.HintChip.class::isInstance).map(c -> (Object) c).toList());
		// Player2 types a new name on their card.
		press(LOBBY_PAD, PadButton.Y);
		check(onRender(() -> card(1) != null && card(1).changing()), "Y reopens Player2's card");
		toNew(1, LOBBY_PAD);
		press(LOBBY_PAD, PadButton.A);
		check(onRender(() -> card(1).keyboard() != null), "New opens the name keyboard");
		Thread.sleep(300);
		List<String> legend = dev.hearthside.fabric.client.ui.KeyboardWidget.drawnLegend;
		log("   legend: " + legend);
		List<String> labels = legend.stream().map(entry -> entry.substring(entry.indexOf(' ') + 1)).toList();
		check(labels.equals(List.of("Type", "Close", "Space", "Del", "Aa", "Done")),
				"the legend says A Type, B Close, Y Space, X Del, LB/RB Aa and Menu Done (" + labels + ")");
		check(legend.stream().anyMatch(e -> e.startsWith("hearthside:glyph/playstation/a "))
				&& legend.stream().anyMatch(e -> e.startsWith("hearthside:glyph/playstation/b "))
				&& legend.stream().anyMatch(e -> e.startsWith("hearthside:glyph/playstation/left_bumper+hearthside:glyph/playstation/right_bumper "))
				&& legend.stream().anyMatch(e -> e.startsWith("hearthside:glyph/playstation/start ")),
				"in the PlayStation pad's own glyphs (L1/R1 for the layers)");
		screenshot("lobby-keyboard-legend");
		// B closes only the keyboard: the card stays on its carousel, the lobby stays, its chips are the same.
		press(LOBBY_PAD, PadButton.B);
		check(onRender(() -> card(1) != null && card(1).keyboard() == null && card(1).stage() == SeatCard.Stage.CAROUSEL),
				"B closes the keyboard; the card stays");
		check(onRender(() -> minecraft.gui.screen()) == lobby, "the lobby does not go back");
		check(onRender(() -> lobby.children().stream().filter(dev.hearthside.fabric.client.ui.HintChip.class::isInstance)
				.map(c -> (Object) c).toList()).equals(chips), "the lobby's chips are unchanged");
		press(LOBBY_PAD, PadButton.B);
		check(onRender(() -> card(1) == null && Party.current().seatView(1).name().equals(made)),
				"B on the carousel keeps " + made);
		// The shared screens' chips follow the controller driving them.
		PADS.plug(HOST_PAD, HOST_GUID, PadStyle.XBOX);
		Thread.sleep(300);
		try {
			// Player 1 takes it (a run that skipped L6 still remembers it as theirs, and it is taken at once).
			if (!onRender(() -> Party.current().hostHasController())) {
				onRender(() -> {
					Screens.getWidgets(lobby).stream()
							.filter(w -> w instanceof AbstractButton && w.getMessage().getString().equals("+ Controller"))
							.map(w -> (AbstractButton) w).findFirst().orElseThrow().onPress(new KeyEvent(0, 0, 0));
					return null;
				});
				press(HOST_PAD, PadButton.A);
			}
			waitFor("player 1 has the Xbox pad", Duration.ofSeconds(5), () -> onRender(() -> Party.current().hostHasController()));
			// Player 1 nudges the pointer: the lobby's chips are theirs.
			PADS.set(HOST_PAD, new InputFrame(0, 0, 0.6f, 0, 0, 0, 0, 0));
			Thread.sleep(150);
			PADS.set(HOST_PAD, InputFrame.NEUTRAL);
			java.util.Set<String> lobbyGlyphs = chipGlyphs();
			check(allFamily(lobbyGlyphs, "xbox"), "player 1's Xbox pad moved the pointer: the lobby's chips are Xbox " + lobbyGlyphs);
			// Menu on Player2's PlayStation pad opens the picker: its chips are theirs at once.
			press(LOBBY_PAD, PadButton.START);
			waitFor("the world picker", Duration.ofSeconds(15),
					() -> onRender(() -> minecraft.gui.screen() instanceof WorldsScreen worlds && worlds.loaded()));
			java.util.Set<String> pickerGlyphs = chipGlyphs();
			check(allFamily(pickerGlyphs, "playstation"), "Menu on the PlayStation pad: the picker's chips are PlayStation " + pickerGlyphs);
			WorldsScreen picker = onRender(() -> (WorldsScreen) minecraft.gui.screen());
			pressUntil(LOBBY_PAD, PadButton.DPAD_DOWN, 12, () -> "".equals(picker.focusedLevelId()));
			press(LOBBY_PAD, PadButton.A);
			check(onRender(() -> minecraft.gui.screen() instanceof NewWorldScreen), "Create opens New World");
			java.util.Set<String> newWorldGlyphs = chipGlyphs();
			check(allFamily(newWorldGlyphs, "playstation"), "New World driven by the PlayStation pad: PlayStation chips " + newWorldGlyphs);
			press(HOST_PAD, PadButton.DPAD_DOWN);
			newWorldGlyphs = chipGlyphs();
			check(allFamily(newWorldGlyphs, "xbox"), "then driven by the Xbox pad: Xbox chips " + newWorldGlyphs);
		} finally {
			backToLobby();
			onRender(() -> {
				if (Party.current() != null && Party.current().hostHasController()) {
					Party.current().releaseController();
				}
				return null;
			});
			PADS.unplug(HOST_PAD);
			Thread.sleep(300);
		}
		check(onRender(() -> minecraft.gui.screen() instanceof LobbyScreen && card(1) == null
				&& Party.current().seatView(1).status().equals("Ready")), "back in the lobby with Player2 ready");
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

	private String madeName, shownName;

	private CoopWorldsStore coopStore() {
		return new CoopWorldsStore(minecraft.gameDirectory.toPath().resolve("hearthside/coop-worlds.json"));
	}

	private void lobbyToPicker() throws Exception {
		// Seat 1 is ready as the profile quick setup made (L3). A second controller takes seat 2 and is still on its
		// card when the world opens: it goes in with what its card shows (the first profile nobody holds).
		String made = onRender(() -> Party.current().seatView(1).name());
		madeName = made;
		// The co-op record says the E2E world was last played with Ember and Birch (W1 checks the picker shows it).
		Files.deleteIfExists(minecraft.gameDirectory.toPath().resolve("hearthside/coop-worlds.json"));
		coopStore().played(WORLD, System.currentTimeMillis(), List.of("Ember", "Birch"));
		PADS.plug(SECOND_LOBBY_PAD, "fake-lobby-pad-2", PadStyle.XBOX);
		Thread.sleep(300);
		check(onRender(() -> Party.current().joinStyle()) == PadStyle.XBOX,
				"the Join hints show the free Xbox controller's buttons, not seat 1's PlayStation ones");
		press(SECOND_LOBBY_PAD, PadButton.A);
		waitFor("seat 2 choosing on its card", Duration.ofSeconds(10), () -> onRender(() -> card(2) != null));
		screenshot("lobby-three-players");
		String shown = onRender(() -> cardShows(2));
		shownName = shown;
		press(LOBBY_PAD, PadButton.START);
		check(onRender(() -> minecraft.gui.screen() instanceof WorldsScreen), "Menu opens the world picker");
		waitFor("the picker has listed the worlds", Duration.ofSeconds(15),
				() -> onRender(() -> minecraft.gui.screen() instanceof WorldsScreen worlds && worlds.loaded()));
	}

	private void pickerFocusesLastCoopWorld() throws Exception {
		WorldsScreen picker = onRender(() -> (WorldsScreen) minecraft.gui.screen());
		check(onRender(() -> minecraft.getLevelSource().levelExists(WORLD)), "the E2E world exists (created by earlier runs)");
		check(WORLD.equals(onRender(picker::focusedLevelId)), "the last co-op world is first and focused");
		check("Last played with Ember and Birch".equals(onRender(() -> picker.playedWithText(WORLD))),
				"its card says who played it");
		screenshot("worlds");
	}

	/** A sound the host played: when, and which event. */
	private record Heard(long nanos, String id) {}

	private final List<Heard> heard = new java.util.concurrent.CopyOnWriteArrayList<>();

	private java.util.Set<String> heardSince(long since) {
		return heard.stream().filter(h -> h.nanos() >= since).map(Heard::id)
				.collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
	}

	private long timesHeard(long since, String id) {
		return heard.stream().filter(h -> h.nanos() >= since && h.id().equals(id)).count();
	}

	/**
	 * S1: since the lobby cards (L2 on): the carousel's page turns, the keyboard's taps, the knock of Hearthside's buttons
	 * and of confirming a card, a seat's crackle and snuff, and never vanilla's click. Then on the picker: a D-pad move
	 * ticks, and six moves inside 100 ms tick at most three times.
	 */
	private void screensMakeSounds(long since) throws Exception {
		java.util.Set<String> lobby = heardSince(since);
		log("   heard since the cards: " + lobby);
		for (String id : List.of("hearthside:ui.page", "hearthside:ui.knock", "hearthside:ui.key", "hearthside:ui.key_delete",
				"hearthside:ui.tick", "hearthside:ui.join", "hearthside:ui.leave")) {
			check(lobby.contains(id), id + " was heard");
		}
		check(!lobby.contains("minecraft:ui.button.click"), "no vanilla click on Hearthside's screens");
		long move = System.nanoTime();
		press(LOBBY_PAD, PadButton.DPAD_DOWN);
		press(LOBBY_PAD, PadButton.DPAD_UP);
		check(timesHeard(move, "hearthside:ui.tick") >= 1, "a D-pad move on the picker ticks");
		long burst = System.nanoTime();
		onRender(() -> {
			for (int i = 0; i < 6; i++) {
				dev.hearthside.fabric.client.menu.MenuNavigator.apply(minecraft, minecraft.gui.screen(),
						i % 2 == 0 ? dev.hearthside.core.input.MenuNav.Action.DOWN : dev.hearthside.core.input.MenuNav.Action.UP,
						0x3F7FD0);
				Thread.sleep(15);
			}
			return null;
		});
		long ticks = timesHeard(burst, "hearthside:ui.tick");
		log("   six moves in 90 ms ticked " + ticks + " times");
		check(ticks >= 1 && ticks <= 3, "six quick moves tick at most three times");
		check(WORLD.equals(onRender(() -> ((WorldsScreen) minecraft.gui.screen()).focusedLevelId())),
				"focus is back on the E2E world for W2");
	}

	/** S2: since B2: the join prompt slides in and out, a drop-in confirm crackles, and a leave snuffs. */
	private void dropInMakesSounds(long since) throws Exception {
		java.util.Set<String> world = heardSince(since);
		log("   heard since B2: " + world);
		for (String id : List.of("hearthside:ui.prompt_in", "hearthside:ui.prompt_out", "hearthside:ui.join",
				"hearthside:ui.leave")) {
			check(world.contains(id), id + " was heard");
		}
	}

	private void pickerOpensWorld() throws Exception {
		if (onRender(() -> minecraft.gui.screen() instanceof WorldsScreen)) {
			press(LOBBY_PAD, PadButton.A);
		}
		Thread.sleep(500);
		if (onRender(() -> minecraft.level == null) && !onRender(() -> minecraft.getLevelSource().levelExists(WORLD))) {
			openWorld();
		}
		String made = madeName, shown = shownName;
		waitFor("both lobby guests in the world", Duration.ofSeconds(240), () -> playerCount() == 3);
		check(guest(made) != null, made + " is in the world under that name (the game launched as Seat1)");
		check(guest(shown) != null, "seat 2 went in as what its card showed (" + shown + ")");
		waitFor("the co-op record has both guests", Duration.ofSeconds(10), () -> coopStore().load().stream()
				.anyMatch(w -> w.levelId().equals(WORLD) && w.players().contains(made) && w.players().contains(shown)));
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

	private void toastShowsTheControllersButton() throws Exception {
		var toast = onRender(() -> Party.current().joinToast());
		check(onRender(toast::visible), "the join toast is up");
		check(hostLogSince(0).contains("hearthside toast glyph playstation"), "it shows the PlayStation controller's own button");
		check(onRender(() -> minecraft.gui.toastManager().getToast(net.minecraft.client.gui.components.toasts.SystemToast.class,
				net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION)) == null,
				"Minecraft's own toast is gone");
		long since = onRender(toast::shownSince);
		float mid = onRender(() -> toast.offset(since + 90_000_000L));
		float done = onRender(() -> toast.offset(since + 180_000_000L));
		check(mid < 0 && done == 0, "it slides down into place within 180 ms (" + mid + ", " + done + ")");
		var still = new dev.hearthside.fabric.client.ui.JoinToast();
		still.show("e2e", PadStyle.XBOX, 0, Duration.ofSeconds(5), true);
		check(still.offset(0) == 0, "with reduce motion it is simply there");
		screenshot("toast");
	}

	private void joinPromptExpires() throws Exception {
		Thread.sleep(3500); // past the 5 s window of B2's press
		press(101, PadButton.A); // a fresh prompt, not a confirmation
		Thread.sleep(2500);
		check(guest("Player2") == null && satellites().isEmpty(), "an A after the prompt expired only asks again");
	}

	/** The latest composited colour logged at a guest's view corner ("" if none). */
	private String lastProbeRgb(int player) throws IOException {
		Matcher m = Pattern.compile("hearthside compositor probe player=" + player + " gen=[0-9]+ rgb=([0-9a-f]{6})")
				.matcher(hostLogSince(0));
		String last = "";
		while (m.find()) {
			last = m.group(1);
		}
		return last;
	}

	private void pickInOwnView() throws Exception {
		Thread.sleep(5500); // let B3's prompt expire
		joinMark = hostLogLength();
		press(101, PadButton.A);
		press(101, PadButton.A);
		waitFor("the newcomer's card", Duration.ofSeconds(10), () -> onRender(() -> card(1) != null));
		check(onRender(() -> Party.current().viewPhaseOf(1)) == dev.hearthside.core.session.ViewPhase.PICKING,
				"their view is picking a profile");
		check(onRender(() -> cardShows(1)).equals("Player2"), "the card shows the profile last used on this controller");
		Thread.sleep(800);
		windowShot("drop-in-picker");
		press(101, PadButton.DPAD_RIGHT);
		check(!onRender(() -> cardShows(1)).equals("Player2"), "their controller drives the card in their view");
		pressUntil(101, PadButton.DPAD_LEFT, 8, () -> cardShows(1).equals("Player2"));
		press(101, PadButton.A);
		check(onRender(() -> card(1) == null
				&& Party.current().viewPhaseOf(1) == dev.hearthside.core.session.ViewPhase.JOINING), "A picks it: joining");
	}

	private void joiningShowsTheCard() throws Exception {
		Thread.sleep(1500);
		windowShot("drop-in-joining");
		waitInWorld(1);
		String log = hostLogSince(joinMark);
		String beforeIn = log.substring(0, log.indexOf("hearthside seat 1 in world"));
		check(!Pattern.compile("probe player=2 gen=[0-9]+ rgb=ff00ff").matcher(beforeIn).find(),
				"while joining, the view showed the card, never the guest's own frames (its loading screens)");
		waitFor("the guest's world in their view", Duration.ofSeconds(5), () -> lastProbeRgb(2).equals("ff00ff"));
		check(!satelliteLog().contains("hearthside loading overlay over a live view"),
				"no loading screen after the card went");
	}

	private void startJoins() throws Exception {
		waitFor("Player2 in the world", Duration.ofSeconds(180), () -> guest("Player2") != null);
		waitInWorld(1);
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

	/** The step ids from {@code first} to {@code last} in run order, both included (a section of the run). */
	private static List<String> between(String first, String last) {
		return ORDER.subList(ORDER.indexOf(first), ORDER.indexOf(last) + 1);
	}

	/** Runs a step, or reports it as SKIP when the focused run doesn't need it. Its id is its name up to the first space. */
	private void step(String name, ThrowingRunnable body) {
		String id = name.substring(0, name.indexOf(' '));
		if (!ORDER.contains(id)) {
			throw new IllegalStateException("E2E step " + id + " is missing from NEEDS");
		}
		if (!selection.runs(id)) {
			log("SKIP " + name);
			return;
		}
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
