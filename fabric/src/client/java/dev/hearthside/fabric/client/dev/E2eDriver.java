package dev.hearthside.fabric.client.dev;

import dev.hearthside.core.compositor.GuiScaleRule;
import dev.hearthside.core.e2e.StepSelection;
import dev.hearthside.core.input.Action;
import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.config.Preset;
import dev.hearthside.core.config.SessionTuning;
import dev.hearthside.core.config.SplitScreenConfig;
import dev.hearthside.core.config.TitleStyleSetting;
import dev.hearthside.core.config.VulkanOffer;
import dev.hearthside.core.input.PadButton;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.input.RemapModel;
import dev.hearthside.core.layout.BorderStyle;
import dev.hearthside.core.layout.ConsoleLayout;
import dev.hearthside.core.layout.LayoutSettings;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.TwoPlayerSplit;
import dev.hearthside.core.layout.ViewFrame;
import dev.hearthside.core.layout.ViewOrder;
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
import dev.hearthside.fabric.client.compositor.OverlayLayer;
import dev.hearthside.fabric.client.compositor.OverlayPass;
import dev.hearthside.fabric.client.host.LobbyScreen;
import dev.hearthside.fabric.client.input.ButtonGuideRenderer;
import dev.hearthside.fabric.client.ui.HearthTextField;
import dev.hearthside.fabric.client.menu.TitleInput;
import dev.hearthside.fabric.client.input.VirtualCursor;
import dev.hearthside.fabric.client.ui.HearthSprites;
import dev.hearthside.fabric.client.ui.TitleStyle;
import dev.hearthside.fabric.client.host.Party;
import dev.hearthside.fabric.client.host.SplitScreenConfigFile;
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
import java.lang.reflect.Field;
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
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.TutorialToast;
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
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLMouse;
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
			{"K1"}, {"E2"}, {"N8"}, {"B7"}, {"T3"}, {"T4"}, {"T1"}, {"T2", "T1"}, {"L5"},
			{"W3", "L5"}, {"W4", "L5"}, {"W5", "L5"}, {"W6", "L5"}, {"W7", "L5"}, {"W8", "L5"},
			{"W10", "L5"}, {"W11", "L5"}, {"W9", "L5"}, {"L6", "W9"},
			{"B6"}, {"B4", "B6"}, {"L1", "B4"}, {"L2", "L1"}, {"L9", "L2"}, {"R1", "L9"}, {"R2", "R1"}, {"L11", "R1"},
			{"B8", "L2"}, {"B10", "B4"}, {"B9", "B4"}, {"L4", "B9"}, {"L8", "L4"}, {"L10", "L8"}, {"L3", "L10"}, {"L12", "L3"},
			{"B5", "L3"}, {"W1", "B5"}, {"S1", "L2", "W1"}, {"W2", "B5"},
			// The in-world session: the world opens with the host alone.
			{"1"}, {"2", "1"}, {"C1", "1"}, {"C2", "1"}, {"Q1", "1"}, {"Q1b", "Q1"}, {"V1", "C2"}, {"V6", "1"}, {"O1", "C2"}, {"C3", "1"}, {"C4", "C2"},
			{"V2", "C2"}, {"U1", "C2"}, {"V4", "C2"}, {"V5", "C2"}, {"V7", "C2"}, {"V8", "C2"}, {"C5", "1"}, {"C5b", "C5"},
			{"F1", "1"}, {"F2", "1"}, {"F3", "1"}, {"E1", "1"},
			{"C6", "C2"}, {"C12", "1"}, {"V3", "1"}, {"V9", "1"}, {"Q3", "C2"}, {"Q4", "C2"}, {"Q4b", "C2"},
			{"N7", "C2"}, {"N7b", "C2"},
			{"C7", "C2"}, {"D4", "C7"}, {"C7b", "C7"}, {"N6b", "C7b"},
			{"V10", "C7b"}, {"D5", "C7b"},
			{"D6", "D5"},
			{"C7c", "C7"}, {"G1", "C6", "D6"}, {"C9", "1"}, {"C11", "1"}, {"3", "1"}, {"C8", "3"}, {"4"}, {"4b", "1"},
			{"5", "1"}, {"C10", "5"}, {"D7"}, {"D8"}, {"Q2", "1"}, {"E3", "1"},
			// The in-world pad block (fake pads).
			{"N2"}, {"P0"}, {"B2"}, {"D3", "B2"}, {"B3", "B2"}, {"D2", "P0", "B2"}, {"D1", "D2"}, {"P1", "D2"},
			{"P2", "P1"}, {"P3", "P1"}, {"P4", "P1"}, {"P7", "P1"}, {"P5", "P1"}, {"P6", "P1"},
			{"M1", "P5"}, {"H2", "P1"}, {"M2", "M1"}, {"M3", "P5"}, {"N1", "P5"}, {"N2b", "P5"}, {"N1b", "P5"}, {"N3", "P5"}, {"N5", "P5"}, {"N9", "P5"}, {"N5b", "P5"}, {"N6", "P5"}, {"A1", "P5"}, {"A2", "P5"}, {"A3", "P5"},
			{"P10", "P5"}, {"P8", "P1"}, {"N4", "P5"}, {"P9", "P1"}, {"N8b"}, {"H3"}, {"H4"}, {"H5"}, {"H5b"}, {"H6"}, {"H7"}, {"S2", "B3", "P9"},
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
	/** A run started with every frame copied ({@code -Pe2eForceCopy=true}): the steps about GPU sharing don't apply. */
	private final boolean copyForcedForTheRun = Boolean.getBoolean("hearthside.forceCopy");

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
				step("K1 Vulkan is offered once where it lets frames be shared, never with Iris, and relaunches the game", this::vulkanOffer);
				step("E2 Auto chooses a preset from the machine when a party is created", this::autoChoosesFromTheMachine);
				step("N8 player 1's PC keyboard types into a lobby card's name keyboard", this::pcKeyboardTypesIntoTheLobbyCard);
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
				// In play, not on the world's loading screen: a focused run's first step starts here. Not fatal: a screen
				// a full run left open is the steps' business.
				try {
					waitFor("player 1 in play", Duration.ofSeconds(120),
							() -> onRender(() -> minecraft.player != null && minecraft.gui.screen() == null));
				} catch (AssertionError e) {
					log("   " + e.getMessage());
				}
				hostWindowBefore = onRender(() -> WindowPlacer.bounds(minecraft.getWindow()));
			}
			step("1 guest joins", this::guestJoins);
			step("2 satellite loads Sodium and Iris", this::satelliteHasCompatMods);
			step("C1 guest window is hidden", this::guestWindowHidden);
			step("C2 guest frames reach the host's window", this::guestFramesComposited);
			step("Q1 each guest's heap is the configured size and its memory is logged", this::guestMemoryConfigured);
			step("Q1b a guest saving its options doesn't tell the server its own render distance", this::guestSaveKeepsTheCap);
			step("V1 frames are shared zero-copy on this backend", this::zeroCopyOnThisBackend);
			step("V6 the guest's window is hidden on its backend", this::guestWindowHiddenOnItsBackend);
			step("O1 the overlay pass draws over every view", this::overlayDrawsOverViews);
			step("C3 host window is not moved or resized by split-screen", this::hostWindowUntouched);
			step("C4 host window resize re-lays out the guest", this::resizeRelaysOut);
			step("V2 views are the right way up", this::viewsUpright);
			step("U1 every view's GUI is the same size, whatever player 1's setting, display scale or fullscreen", this::oneGuiScale);
			step("V4 a resource reload while composited keeps every view composited", this::reloadKeepsViews);
			step("V5 composite cost within budget", this::compositeCostWithinBudget);
			step("V7 minimise, restore and fullscreen keep views composited", this::minimiseRestoreFullscreen);
			step("V8 a broken view shader falls back to a plain blit", this::brokenViewShaderFallsBack);
			step("C5 copy transport works", this::copyTransportWorks);
			step("C5b two quick resizes on the copy transport keep the guest composited", this::quickResizesOnCopy);
			step("F1 copied frames are never torn, even when copying is slow", this::copiedFramesAreWhole);
			step("F2 copying keeps a guest's pixels in shared memory, never in a file", this::copyingLeavesNoPixelFile);
			step("F3 no uncleared memory or see-through pixels in a guest's view, on either transport", this::noGarbageOrSeeThrough);
			step("E1 a forced Performance preset applies for the session and leaves the guest's own options alone", this::forcedPerformancePreset);
			step("C6 maximise and restore keep the guest composited", () -> sampleGaps(this::maximiseAndRestore));
			step("C12 a failed GPU import falls back to copying frames", this::failedImportFallsBackToCopy);
			step("V3 a device mismatch copies frames and says why", this::deviceMismatchCopies);
			step("V9 a game without Vulkan copies frames instead of crashing", this::noVulkanCopies);
			step("Q3 a guest's own Sodium settings survive its next launch", this::guestSodiumSettingsSurvive);
			step("Q4 a guest can't switch to fullscreen", this::guestRefusesFullscreen);
			step("Q4b an untested Sodium version leaves Sodium's options alone instead of crashing guests", this::untestedSodiumLeftAlone);
			step("N7 Split-screen options apply live: layout glides, borders and name tags at once, swap views, saved, title style next time",
					() -> sampleGaps(this::splitScreenOptions));
			step("N7b a preset chosen on Split-screen leaves guests already in the world as they were", this::presetWaitsForTheNextJoin);
			step("C7 a third player joins and the layout makes room without GL errors", () -> sampleGaps(this::thirdPlayerJoins));
			step("D4 with three players the free quarter shows the party map", this::partyPanelShows);
			step("C7b a fourth player joins the four-view layout", () -> sampleGaps(this::fourthJoins));
			step("N6b with four players, Add player is greyed out", this::addPlayerGreyedWhenFull);
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
			// Last in the composited session: it opens the world twice more, leaving it as it was with player 1 alone.
			step("Q2 a stacked two-player layout from config/hearthside.json", this::stackedLayoutFromConfig);
			step("E3 presetForPlayer1 gives player 1's own game the preset for the session, and saves keep their own", this::presetForPlayerOne);
			if (Boolean.getBoolean("hearthside.e2e.fakePads")) {
				step("N2 alone, the pause key opens vanilla's pause screen, a mod's button and all", this::alonePauseIsVanilla);
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
				step("N1 a guest's START opens Hearthside's pause menu; Options… and Game menu… open vanilla's and come back", this::guestPauseMenu);
				step("N2b in split-screen player 1 gets Hearthside's menu, and Game menu… keeps every mod's button", this::splitScreenPauseKeepsModButtons);
				step("N1b player 1's controller drives their pause menu by focus; Options… comes back; B is Resume, and Keep playing on the Leave confirm", this::hostPadPauseMenu);
				step("N3 the world freezes only when everyone in it is paused, and every menu says so", this::pauseTogether);
				step("N5 a dropped controller dims its player's view and pauses them; replugging undoes it; after a minute another controller takes over", this::droppedController);
				step("N9 a joining view's fade never dims player 1's border", this::joinFadeKeepsBorders);
				step("N5b a controller dropping while its player picks a profile dims the picker under the reconnect card", this::droppedWhilePicking);
				step("N6 player 1's Add player: one A on a free controller joins; the prompt expires, and Esc cancels it", this::addPlayer);
				step("A1 RT punches a mob", this::triggerPunches);
				step("A2 holding RT mines a block in the unfocused guest window", this::triggerMines);
				step("A3 LT places a block", this::triggerPlaces);
				step("P10 holding Drop throws the whole stack; a tap throws one", this::dropTapAndHold);
				step("P8 satellite got the PlayStation pad style", this::padStyleReached);
				step("N4 player 1's Leave game asks first; a guest's Leave game takes them out and closes their game", this::leaveGame);
				step("P9 Player2 leaves", this::player2Leaves);
				step("N8b player 1's PC keyboard types into a newcomer's keyboard only while player 1 plays on a controller",
						this::pcKeyboardTypesIntoANewcomersKeyboard);
				step("H3 player 1 on a controller gets a pointer on Minecraft's screens and can respawn", this::hostPointerOnScreens);
				step("H4 controller hints on Minecraft's screens never cover anything and offer only what works", this::hintsStayClear);
				step("H5 player 1 can switch between the controller pointer and the mouse at any time", this::pointerOrMouse);
				step("H5b a mouse click goes where the arrow is, never where the controller's pointer rests", this::clickWhereTheArrowIs);
				step("H6 unplugging player 1's controller with a screen open hides its pointer and hints", this::hostPadUnpluggedOnScreen);
				step("H7 toasts never cover hints, no keyboard tutorial on a controller, one hint row, bundle hints", this::hintsAndToasts);
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
		if (copyForcedForTheRun) {
			log("   V1: every frame is copied in this run (forced); nothing is shared to check");
			return;
		}
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
		// The cap is the party's preset's (Spec D section 3.3), or the config's own number.
		Preset preset = partyPreset();
		int cap = SessionTuning.of(preset, SplitScreenConfigFile.load(minecraft.gameDirectory.toPath())).renderDistanceCap();
		log("   party preset " + preset + ", guest render distance cap " + cap);
		check(cap == 0 || renderDistance <= cap, "the guest's render distance " + renderDistance + " is capped at " + cap);
		log("   memory host=" + workingSetMb(ProcessHandle.current().pid()) + " MB guest="
				+ workingSetMb(satellites().getFirst().pid()) + " MB");
	}

	/** The preset the current party resolved, from its log line (Auto's choice, or the config's own preset). */
	private Preset partyPreset() throws IOException {
		Matcher m = Pattern.compile("hearthside preset (?:auto → )?([a-z]+)").matcher(hostLogSince(0));
		String last = null;
		while (m.find()) {
			last = m.group(1);
		}
		check(last != null && !last.equals("auto"), "the party logged its preset");
		return Preset.valueOf(last.toUpperCase(Locale.ROOT));
	}

	/**
	 * Q1b: a guest whose own render distance (12) is capped at 8 saves its options, as closing Video Settings does
	 * (batch review I1). Its options.txt keeps 12, and the host's server never hears more than the cap: it goes on
	 * loading and sending chunks for 8.
	 */
	private void guestSaveKeepsTheCap() throws Exception {
		Path options = minecraft.gameDirectory.toPath().resolve("hearthside/satellite-1/options.txt");
		guestLeaves();
		String before = Files.exists(options) ? Files.readString(options) : null;
		System.setProperty("hearthside.e2e.guestSaveOptions", "true");
		// Balanced's cap of 8, whatever Auto chose for this machine: the step is about a cap holding through a save.
		String presetBefore = System.getProperty("hearthside.e2e.preset");
		System.setProperty("hearthside.e2e.preset", "balanced");
		try {
			writeOption(options, "renderDistance", "12");
			// Custom graphics, so a fresh folder's named preset (fancy: render distance 16) doesn't load over the 12 (as in E1).
			writeOption(options, "graphicsPreset", "\"custom\"");
			guestRejoins();
			check(satelliteLog().contains("hearthside capped the render distance at 8 for this session (own setting 12)"),
					"the guest's own 12 is capped at 8 this session");
			waitFor("the guest saved its options", Duration.ofSeconds(30),
					() -> satelliteLog().contains("hearthside e2e guest saved its options"));
			// The save's own broadcast reaches the server within milliseconds; watch it for 5 s.
			long until = System.nanoTime() + Duration.ofSeconds(5).toNanos();
			int requested = 0;
			while (System.nanoTime() < until) {
				IntegratedServer server = minecraft.getSingleplayerServer();
				requested = server.submit(() -> server.getPlayerList().getPlayerByName(GUEST).requestedViewDistance()).join();
				check(requested <= 8, "the server hears the guest's capped render distance, not " + requested);
				Thread.sleep(100);
			}
			log("   server's requested view distance for " + GUEST + ": " + requested);
			check(Files.readString(options).lines().anyMatch(l -> l.equals("renderDistance:12")),
					"the guest's options.txt keeps its own 12");
		} finally {
			System.clearProperty("hearthside.e2e.guestSaveOptions");
			if (presetBefore == null) {
				System.clearProperty("hearthside.e2e.preset");
			} else {
				System.setProperty("hearthside.e2e.preset", presetBefore);
			}
			if (before != null) {
				Files.writeString(options, before);
			}
		}
	}

	/** Sets {@code key:value} in an options.txt (adding the line when it has none). */
	private static void writeOption(Path options, String key, String value) throws IOException {
		List<String> lines = Files.exists(options) ? new ArrayList<>(Files.readAllLines(options)) : new ArrayList<>();
		lines.removeIf(l -> l.startsWith(key + ":"));
		lines.add(key + ":" + value);
		Files.createDirectories(options.getParent());
		Files.write(options, lines);
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
							Party.current().layout()).players().get(1).width();
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

	/**
	 * U1 (Spec D section 3.4; plan R2, R3): every view's GUI looks the same size. As is; with player 1's own GUI scale
	 * fixed at 4 (every view follows within a few seconds); under a faked display scale; and after fullscreen and back.
	 */
	private void oneGuiScale() throws Exception {
		int settingBefore = onRender(() -> minecraft.options.guiScale().get());
		Path guestOptions = minecraft.gameDirectory.toPath().resolve("hearthside/satellite-1/options.txt");
		String guestOptionsBefore = null;
		long handle = onRender(() -> minecraft.getWindow().handle());
		try {
			// A big window, so the settings below give different scales (at the E2E's 854×480 every one caps to 1).
			onRender(() -> SDLVideo.SDL_SetWindowSize(handle, 1920, 1080));
			Thread.sleep(2000);
			guiScalesMatch("as is, 1920×1080");

			onRender(() -> {
				minecraft.options.guiScale().set(4);
				minecraft.options.save();
				return null;
			});
			guiScalesMatch("player 1's GUI scale fixed at 4");
			onRender(() -> {
				minecraft.options.guiScale().set(1);
				minecraft.options.save();
				return null;
			});
			guiScalesMatch("player 1's GUI scale fixed at 1, smaller than Auto");
			onRender(() -> {
				minecraft.options.guiScale().set(settingBefore);
				minecraft.options.save();
				return null;
			});
			guiScalesMatch("player 1's own GUI scale back");

			// The laptop's cause (Task 4's finding): a guest whose own options say GUI scale 1.
			guestLeaves();
			guestOptionsBefore = Files.exists(guestOptions) ? Files.readString(guestOptions) : null;
			writeOption(guestOptions, "guiScale", "1");
			guestRejoins();
			guiScalesMatch("the guest's own options say GUI scale 1");

			withGuestHooks(Map.of("hearthside.e2e.fakeScreenPerFb", "1.5"), () -> {
				rejoinGuest();
				guiScalesMatch("a display scale of 150%");
			});

			onRender(() -> {
				toggleFullscreen();
				return null;
			});
			Thread.sleep(2000);
			guiScalesMatch("fullscreen");
			onRender(() -> {
				toggleFullscreen();
				return null;
			});
			Thread.sleep(2000);
			onRender(() -> SDLVideo.SDL_SetWindowSize(handle, hostWindowBefore.width(), hostWindowBefore.height()));
			guiScalesMatch("back from fullscreen");
		} finally {
			onRender(() -> {
				if (minecraft.options.guiScale().get() != settingBefore) {
					minecraft.options.guiScale().set(settingBefore);
					minecraft.options.save();
				}
				if (minecraft.options.fullscreen().get()) {
					toggleFullscreen();
				}
				return null;
			});
			onRender(() -> SDLVideo.SDL_SetWindowSize(handle, hostWindowBefore.width(), hostWindowBefore.height()));
			if (guestOptionsBefore != null) {
				guestLeaves();
				Files.writeString(guestOptions, guestOptionsBefore);
				guestRejoins();
			}
		}
	}

	private static final Pattern GUI_SCALE_LINE = Pattern.compile(
			"hearthside gui scale player=([0-9]+) scale=([0-9]+) frame=([0-9]+)x([0-9]+) view=([0-9]+)x([0-9]+)");

	/** One player's latest logged GUI scale, frame size and view size. */
	private record GuiScaleLine(int scale, int frameWidth, int frameHeight, int viewWidth, int viewHeight) {
		/** The GUI scale as it looks on screen: the frame is stretched over the view. */
		double effective() {
			return scale * (double) viewWidth / frameWidth;
		}
	}

	private static GuiScaleLine lastGuiScale(String log, int player) {
		Matcher m = GUI_SCALE_LINE.matcher(log);
		GuiScaleLine last = null;
		while (m.find()) {
			if (Integer.parseInt(m.group(1)) == player) {
				last = new GuiScaleLine(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
						Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)));
			}
		}
		return last;
	}

	/**
	 * Within 5 s, player 1's and Guest2's latest logged GUI scales both look like the party's: Minecraft's rule at player
	 * 1's setting, the smallest over both views (GuiScaleRule.partyScale).
	 */
	private void guiScalesMatch(String when) throws Exception {
		String[] seen = {""};
		waitFor("the same GUI size for every view (" + when + ")", Duration.ofSeconds(5), () -> {
			GuiScaleLine host = lastGuiScale(hostLogSince(0), 1);
			GuiScaleLine guest = lastGuiScale(satelliteLog(), 2);
			if (host == null || guest == null) {
				seen[0] = "host " + host + ", guest " + guest;
				return false;
			}
			int setting = onRender(() -> minecraft.options.guiScale().get());
			int party = GuiScaleRule.partyScale(setting, List.of(new Rect(0, 0, host.viewWidth(), host.viewHeight()),
					new Rect(0, 0, guest.viewWidth(), guest.viewHeight())));
			seen[0] = "party " + party + ", host " + host + ", guest " + guest;
			return Math.abs(host.effective() - party) < 1e-6 && Math.abs(guest.effective() - party) < 1e-6;
		});
		log("   gui scale (" + when + "): " + seen[0]);
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
			// On Windows copied pixels live in shared memory (Spec D section 3.2): no file at all. Elsewhere, until phase 2's
			// shm_open, the old files must be cleaned up.
			boolean windows = System.getProperty("os.name", "").startsWith("Windows");
			check(windows ? pixelFiles == 0 : pixelFiles <= 2,
					(windows ? "no copy-transport pixel file on Windows" : "old copy-transport files are cleaned up") + " (found "
							+ pixelFiles + ")");
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
		// A generation the guest had already moved on from is gone, not an import error (Spec D final review F4).
		check(!hostLogSince(mark).contains("could not import player"), "no superseded generation logged as an import error");
		check(!satelliteLog().substring(satMark).contains("exception"), "no exception in the guest's log");
	}

	/**
	 * F1 (Spec D section 3.2): the guest paints whole frames in two alternating colours while copying is slowed on both
	 * sides, standing in for a slow integrated GPU. The host judges every steady frame at 13 points: one frame showing
	 * both colours is torn, any other colour is garbage.
	 */
	private void copiedFramesAreWhole() throws Exception {
		withGuestHooks(Map.of("hearthside.forceCopy", "true", "hearthside.e2e.solidFrames", "255",
				"hearthside.e2e.slowCopyMs", "4", "hearthside.e2e.tearCheck", "true"), () -> {
			long mark = rejoinGuest();
			waitFor("satellite on the copy transport", Duration.ofSeconds(30),
					() -> satelliteLog().contains("hearthside frame slots ready transport=copy"));
			String[] counts = waitForTearCheck(2, mark, 600, Duration.ofSeconds(120));
			log("   tear check (copy, slowed 4 ms): frames=" + counts[0] + " mixed=" + counts[1] + " bad=" + counts[2]
					+ " first=" + counts[3]);
			check(counts[1].equals("0"), "no torn frame (mixed=" + counts[1] + ")");
			check(counts[2].equals("0"), "only the guest's colours (bad=" + counts[2] + ", first=" + counts[3] + ")");
		});
	}

	/**
	 * F2 (Spec D section 3.2): copying keeps a guest's pixels in page-file-backed shared memory, never in a file in its
	 * game folder (Windows writes such a file's pages back to disk). The slot table stays a small file.
	 */
	private void copyingLeavesNoPixelFile() throws Exception {
		withGuestHooks(Map.of("hearthside.forceCopy", "true"), () -> {
			long mark = rejoinGuest();
			waitFor("satellite on the copy transport", Duration.ofSeconds(30),
					() -> satelliteLog().contains("hearthside frame slots ready transport=copy"));
			waitForProbe(2, mark, Duration.ofSeconds(30));
			List<String> files;
			try (var list = Files.list(minecraft.gameDirectory.toPath().resolve("hearthside/satellite-1"))) {
				files = list.map(f -> f.getFileName().toString()).filter(n -> n.startsWith("frames-")).sorted().toList();
			}
			log("   frame files in the guest's folder: " + files);
			check(files.stream().noneMatch(n -> n.endsWith(".pixels")), "no pixel file while copying (found " + files + ")");
			check(files.stream().anyMatch(n -> n.endsWith(".slots")), "the slot table is still there");
		});
	}

	/**
	 * F3 (Spec D sections 3.2 and R10): every texture Hearthside creates starts full of garbage, as Intel's drivers leave
	 * it, and the guest's frames are half transparent. The host must still show exactly the guest's colours, edge to
	 * edge, on the copy transport and on the shared one.
	 */
	private void noGarbageOrSeeThrough() throws Exception {
		for (boolean copy : new boolean[] {true, false}) {
			Map<String, String> hooks = new java.util.HashMap<>(Map.of("hearthside.e2e.poisonTextures", "true",
					"hearthside.e2e.solidFrames", "128", "hearthside.e2e.tearCheck", "true"));
			if (copy) {
				hooks.put("hearthside.forceCopy", "true");
			}
			withGuestHooks(hooks, () -> {
				long mark = rejoinGuest();
				waitFor("the guest's frame slots", Duration.ofSeconds(30),
						() -> satelliteLog().contains("hearthside frame slots ready transport="));
				if (!copy && !satelliteLog().contains("transport=gpu_shared")) {
					log("   F3: this backend copies frames; the copy run covers it");
					return;
				}
				String[] counts = waitForTearCheck(2, mark, 300, Duration.ofSeconds(90));
				log("   tear check (" + (copy ? "copy" : "shared") + ", poisoned, half alpha): frames=" + counts[0]
						+ " mixed=" + counts[1] + " bad=" + counts[2] + " first=" + counts[3]);
				check(counts[1].equals("0"), "no torn frame (mixed=" + counts[1] + ")");
				check(counts[2].equals("0"), "no garbage or see-through pixel (bad=" + counts[2] + ", first=" + counts[3] + ")");
			});
		}
	}

	/**
	 * Runs {@code body} with host properties set (Party passes its {@code hearthside.e2e.*} hooks on to the guest games it
	 * launches), then puts back what each property was before (a run forced to copy stays forced) and brings Guest2 back.
	 */
	private void withGuestHooks(Map<String, String> hooks, ThrowingRunnable body) throws Exception {
		Map<String, String> before = new java.util.HashMap<>();
		hooks.keySet().forEach(key -> before.put(key, System.getProperty(key)));
		hooks.forEach(System::setProperty);
		try {
			body.run();
		} finally {
			before.forEach((key, value) -> {
				if (value == null) {
					System.clearProperty(key);
				} else {
					System.setProperty(key, value);
				}
			});
			rejoinGuest();
		}
	}

	/** Guest2 leaves and joins again, so its game starts with the properties now set. Returns the host log's length before the join. */
	private long rejoinGuest() throws Exception {
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
		return mark;
	}

	/**
	 * The tear check's counts for {@code player} (frames, mixed, bad, first bad colour) once the host has judged at least
	 * {@code frames} steady frames since {@code mark}.
	 */
	private String[] waitForTearCheck(int player, long mark, int frames, Duration timeout) throws Exception {
		Pattern line = Pattern.compile(
				"hearthside tear check player=" + player + " frames=([0-9]+) mixed=([0-9]+) bad=([0-9]+) first=(\\S+)");
		String[][] last = {null};
		waitFor(frames + " judged frames of player " + player, timeout, () -> {
			Matcher m = line.matcher(hostLogSince(mark));
			while (m.find()) {
				last[0] = new String[] {m.group(1), m.group(2), m.group(3), m.group(4)};
			}
			return last[0] != null && Integer.parseInt(last[0][0]) >= frames;
		});
		return last[0];
	}

	/** The guest options a preset sets for the session; options.txt must keep the guest's own values for each. */
	private static final List<String> TUNED_OPTIONS = List.of("renderDistance", "maxFps", "cutoutLeaves", "biomeBlendRadius",
			"entityDistanceScaling", "renderClouds", "particles", "graphicsPreset");

	/**
	 * E1 (Spec D section 3.3; plan R3): a forced Performance preset. Guest2's game renders 6 chunks with light graphics and
	 * a 45 fps cap for the session, its frame snaps to the party's GUI scale, the server simulates at most 6 chunks, frames
	 * still arrive, and when the guest saves its options its own values are what's written.
	 */
	private void forcedPerformancePreset() throws Exception {
		Path options = minecraft.gameDirectory.toPath().resolve("hearthside/satellite-1/options.txt");
		long stepMark = hostLogLength();
		guestLeaves();
		String before = Files.exists(options) ? Files.readString(options) : null;
		// The guest's own settings, each unlike what Performance sets for the session.
		writeOption(options, "renderDistance", "12");
		writeOption(options, "maxFps", "120");
		writeOption(options, "cutoutLeaves", "true");
		writeOption(options, "biomeBlendRadius", "2");
		writeOption(options, "entityDistanceScaling", "1.0");
		// Custom graphics, as these are: a named preset (a fresh game folder's "fancy") would set its own values over
		// them as the guest loads its options.
		writeOption(options, "graphicsPreset", "\"custom\"");
		Map<String, String> own = optionValues(options);
		String presetBefore = System.getProperty("hearthside.e2e.preset");
		System.setProperty("hearthside.e2e.preset", "performance");
		System.setProperty("hearthside.e2e.guestSaveOptions", "true");
		long handle = onRender(() -> minecraft.getWindow().handle());
		try {
			// Views big enough for a GUI scale of 3, where Performance still renders the guest at full resolution (reduced
			// only from 4, after Task 8's visual QA); the frame check below expects what GuiScaleRule says either way.
			onRender(() -> SDLVideo.SDL_SetWindowSize(handle, 2560, 1440));
			Thread.sleep(2000);
			long mark = hostLogLength();
			guestRejoins();
			waitFor("the guest's session options", Duration.ofSeconds(30),
					() -> satelliteLog().contains("hearthside session options light=true fpscap=45"));
			Matcher distance = Pattern.compile("hearthside guest heap max=([0-9]+) mb render distance=([0-9]+)").matcher(satelliteLog());
			check(distance.find() && Integer.parseInt(distance.group(2)) == 6, "the guest renders 6 chunks this session");
			waitForProbe(2, mark, Duration.ofSeconds(30));

			// The guest's frame snaps to the party's GUI scale (plan R3): reduced only where its GUI stays a whole scale.
			String[] seen = {""};
			waitFor("the guest's frame at the Performance size", Duration.ofSeconds(10), () -> {
				GuiScaleLine host = lastGuiScale(hostLogSince(0), 1);
				GuiScaleLine guest = lastGuiScale(satelliteLog(), 2);
				if (host == null || guest == null) {
					return false;
				}
				int setting = onRender(() -> minecraft.options.guiScale().get());
				Rect guestView = new Rect(0, 0, guest.viewWidth(), guest.viewHeight());
				int party = GuiScaleRule.partyScale(setting, List.of(new Rect(0, 0, host.viewWidth(), host.viewHeight()), guestView));
				GuiScaleRule.ViewScale expected = GuiScaleRule.forView(guestView, party, true);
				seen[0] = "party " + party + ", expected " + expected + ", guest " + guest;
				return guest.scale() == expected.guiScale() && guest.frameWidth() == expected.frameWidth()
						&& guest.frameHeight() == expected.frameHeight();
			});
			log("   performance frame: " + seen[0]);

			IntegratedServer server = minecraft.getSingleplayerServer();
			int simulation = server.submit(() -> server.getPlayerList().getSimulationDistance()).join();
			check(simulation <= 6, "the server simulates at most 6 chunks (" + simulation + ")");
			check(hostLogSince(mark).contains("hearthside simulation distance 6 (preset)") || simulation < 6,
					"the preset's simulation distance is logged");
			windowShot("preset-performance");

			waitFor("the guest saved its options", Duration.ofSeconds(30),
					() -> satelliteLog().contains("hearthside e2e guest saved its options"));
			Map<String, String> after = optionValues(options);
			for (String key : TUNED_OPTIONS) {
				check(java.util.Objects.equals(own.get(key), after.get(key)),
						key + " is still the guest's own " + own.get(key) + " in options.txt (saved " + after.get(key) + ")");
			}
		} finally {
			if (presetBefore == null) {
				System.clearProperty("hearthside.e2e.preset");
			} else {
				System.setProperty("hearthside.e2e.preset", presetBefore);
			}
			System.clearProperty("hearthside.e2e.guestSaveOptions");
			onRender(() -> SDLVideo.SDL_SetWindowSize(handle, hostWindowBefore.width(), hostWindowBefore.height()));
			guestLeaves();
			if (before != null) {
				Files.writeString(options, before);
			}
			guestRejoins();
		}
		// The last guest left as the window went back to its size (just above): compose never failed over it.
		check(!hostLogSince(stepMark).contains("hearthside could not compose the canvas"),
				"the canvas was composed throughout, the window resizing as the last guest left");
	}

	/** Every {@code key:value} line of an options.txt. */
	private static Map<String, String> optionValues(Path options) throws IOException {
		Map<String, String> values = new java.util.HashMap<>();
		if (Files.exists(options)) {
			for (String line : Files.readAllLines(options)) {
				int colon = line.indexOf(':');
				if (colon > 0) {
					values.put(line.substring(0, colon), line.substring(colon + 1));
				}
			}
		}
		return values;
	}

	/**
	 * E2 (Spec D section 3.3): Auto chooses from the machine when a party is created, with the GPU type and memory faked:
	 * an integrated GPU or 8 GB is Performance; a discrete GPU with 32 GB is Quality, or Balanced for 3–4 players or a
	 * very big window, with the reasons logged.
	 */
	private void autoChoosesFromTheMachine() throws Exception {
		Pattern line = Pattern.compile("hearthside preset auto → ([a-z]+) \\(([^)]*)\\)");
		try {
			for (String[] machine : new String[][] {{"INTEGRATED", "32", "performance"}, {"DISCRETE", "8", "performance"},
					{"DISCRETE", "32", null}}) {
				System.setProperty("hearthside.e2e.fakeGpuType", machine[0]);
				System.setProperty("hearthside.e2e.fakeRamGb", machine[1]);
				long mark = hostLogLength();
				check(pressCoop() instanceof LobbyScreen, "Co-op opens the lobby");
				Matcher m = line.matcher(hostLogSince(mark));
				check(m.find(), "the lobby's party logged Auto's choice for " + machine[0] + " " + machine[1] + " GB");
				log("   auto with " + machine[0] + " GPU, " + machine[1] + " GB: " + m.group(1) + " (" + m.group(2) + ")");
				if (machine[2] != null) {
					check(m.group(1).equals(machine[2]), machine[0] + " " + machine[1] + " GB is " + machine[2] + " (got " + m.group(1) + ")");
					check(m.group(2).contains(machine[0].equals("INTEGRATED") ? "integrated gpu" : "8 gb ram"), "the reason is logged");
				} else {
					check(m.group(1).equals("quality") ? m.group(2).equals("discrete gpu, 32 gb ram")
							: m.group(1).equals("balanced") && (m.group(2).contains("players") || m.group(2).contains("window")),
							"a discrete GPU with 32 GB is Quality, or Balanced with its reason (got " + m.group(1) + " (" + m.group(2) + "))");
				}
				leaveLobby();
			}
		} finally {
			System.clearProperty("hearthside.e2e.fakeGpuType");
			System.clearProperty("hearthside.e2e.fakeRamGb");
		}
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
		if (copyForcedForTheRun) {
			log("   C12: every frame is copied in this run (forced); there is no GPU import to fail");
			return;
		}
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
		if (copyForcedForTheRun) {
			log("   V3: every frame is copied in this run (forced), so the copy's reason is FORCED, not a mismatch");
			return;
		}
		long stepMark = hostLogLength();
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
		// The guest left as C12's window went back to its size: compose never failed over it.
		check(!hostLogSince(stepMark).contains("hearthside could not compose the canvas"),
				"the canvas was composed throughout, the last guest leaving as the window resized");
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

	// ---- Config and guests (batch B) ----

	/**
	 * Q2: with {@code "twoPlayerLayout": "stacked"} in config/hearthside.json, player 1's view is the top half and
	 * player 2's the bottom half, which the guest renders at. A session reads the file when it opens, so the world is
	 * closed and opened again, and once more afterwards with the file as it was.
	 */
	private void stackedLayoutFromConfig() throws Exception {
		Path file = minecraft.gameDirectory.toPath().resolve("config").resolve("hearthside.json");
		byte[] before = Files.exists(file) ? Files.readAllBytes(file) : null;
		try {
			dev.hearthside.core.config.SplitScreenConfig config = dev.hearthside.core.config.SplitScreenConfig.DEFAULTS;
			if (before != null) {
				config = dev.hearthside.core.config.SplitScreenConfig.read(
						new java.io.StringReader(new String(before, StandardCharsets.UTF_8)));
			}
			try (java.io.Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				config.withLayout(config.layout().withTwo(LayoutSettings.TwoPlayer.STACKED)).write(out);
			}
			reopenWorld();
			check(onRender(() -> Party.current().layout().two()) == LayoutSettings.TwoPlayer.STACKED,
					"the new session read the stacked layout");
			joinMark = hostLogLength();
			onRender(() -> {
				Party.current().join(GUEST);
				return null;
			});
			waitFor("2 players", Duration.ofSeconds(180), () -> playerCount() == 2);
			waitInWorld(1);
			List<Rect> plan = onRender(() -> {
				var size = minecraft.getWindow().queryFramebufferSize();
				return dev.hearthside.core.layout.SplitLayout.plan(2, new Rect(0, 0, size.width(), size.height()),
						Party.current().layout()).players();
			});
			Rect top = plan.get(0), bottom = plan.get(1);
			log("   stacked views " + top + " over " + bottom);
			check(top.y() == 0 && bottom.y() >= top.height() && top.width() == bottom.width(), "the plan stacks the views");
			waitFor("the views stacked in the window", Duration.ofSeconds(10), () -> onRender(() -> {
				List<dev.hearthside.core.layout.ViewFrame> frames = Compositor.active().overlayViews();
				return frames.size() == 2 && frames.stream().anyMatch(f -> f.seat() == 0 && f.rect().equals(top))
						&& frames.stream().anyMatch(f -> f.seat() == 1 && f.rect().equals(bottom));
			}));
			waitFor("the guest rendering at the bottom half's size", Duration.ofSeconds(30),
					() -> satelliteLog().contains("hearthside virtual size " + bottom.width() + "x" + bottom.height()));
			waitForProbe(2, hostLogLength(), Duration.ofSeconds(30));
			windowShot("stacked-two-players");
		} finally {
			if (before != null) {
				Files.write(file, before);
			} else {
				Files.deleteIfExists(file);
			}
			reopenWorld();
		}
	}

	/**
	 * E3 (Spec D section 3.3; plan R4): with {@code presetForPlayer1}, player 1's own game takes the Performance preset's
	 * render distance, lighter graphics and frame-rate cap while split-screen is on (not a lower resolution). A save
	 * writes player 1's own values, and leaving split-screen puts them back.
	 */
	private void presetForPlayerOne() throws Exception {
		Path file = minecraft.gameDirectory.toPath().resolve("config").resolve("hearthside.json");
		byte[] before = Files.exists(file) ? Files.readAllBytes(file) : null;
		Path hostOptions = minecraft.gameDirectory.toPath().resolve("options.txt");
		int[] original = onRender(() -> new int[] {minecraft.options.renderDistance().get(), minecraft.options.framerateLimit().get(),
				minecraft.options.cutoutLeaves().get() ? 1 : 0});
		try {
			// Player 1's own settings, each unlike what Performance sets.
			onRender(() -> {
				minecraft.options.renderDistance().set(12);
				minecraft.options.framerateLimit().set(120);
				minecraft.options.cutoutLeaves().set(true);
				minecraft.options.save();
				return null;
			});
			Map<String, String> own = optionValues(hostOptions);
			SplitScreenConfig config = before == null ? SplitScreenConfig.DEFAULTS
					: SplitScreenConfig.read(new java.io.StringReader(new String(before, StandardCharsets.UTF_8)));
			try (java.io.Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				new SplitScreenConfig(config.guestMemoryMb(), config.guestRenderDistance(), config.layout(), Preset.PERFORMANCE,
						true, config.vulkanOffer(), config.titleStyle()).write(out);
			}
			reopenWorld();
			check(hostLogSince(0).contains("hearthside preset performance (config)"), "the party took Performance from the config");
			onRender(() -> {
				Party.current().join(GUEST);
				return null;
			});
			waitFor("2 players", Duration.ofSeconds(180), () -> playerCount() == 2);
			waitInWorld(1);
			waitFor("player 1's session options", Duration.ofSeconds(10), () -> onRender(() -> minecraft.options.renderDistance().get() <= 6));
			String session = onRender(() -> "renderDistance " + minecraft.options.renderDistance().get() + ", maxFps "
					+ minecraft.options.framerateLimit().get() + ", cutoutLeaves " + minecraft.options.cutoutLeaves().get() + ", clouds "
					+ minecraft.options.cloudStatus().get() + ", particles " + minecraft.options.particles().get());
			log("   player 1 in split-screen: " + session);
			check(onRender(() -> minecraft.options.framerateLimit().get() <= 45 && !minecraft.options.cutoutLeaves().get()
					&& minecraft.options.cloudStatus().get().name().equals("OFF")
					&& minecraft.options.particles().get().name().equals("DECREASED")), "player 1 has Performance's lighter graphics and 45 fps");

			onRender(() -> {
				minecraft.options.save();
				return null;
			});
			Map<String, String> saved = optionValues(hostOptions);
			for (String key : TUNED_OPTIONS) {
				check(java.util.Objects.equals(own.get(key), saved.get(key)),
						key + " is still player 1's own " + own.get(key) + " in options.txt (saved " + saved.get(key) + ")");
			}
			check(onRender(() -> minecraft.options.renderDistance().get() <= 6), "the session values hold after a save");

			onRender(() -> {
				Party.current().leave(GUEST);
				return null;
			});
			waitFor("the guest gone", Duration.ofSeconds(30), () -> playerCount() == 1 && satellites().isEmpty());
			waitFor("player 1's own settings back", Duration.ofSeconds(10), () -> onRender(
					() -> minecraft.options.renderDistance().get() == 12 && minecraft.options.framerateLimit().get() == 120
							&& minecraft.options.cutoutLeaves().get()));
		} finally {
			if (before != null) {
				Files.write(file, before);
			} else {
				Files.deleteIfExists(file);
			}
			reopenWorld();
			onRender(() -> {
				minecraft.options.renderDistance().set(original[0]);
				minecraft.options.framerateLimit().set(original[1]);
				minecraft.options.cutoutLeaves().set(original[2] == 1);
				minecraft.options.save();
				return null;
			});
		}
	}

	/** Closes the world (its guests' games with it) and opens it again: a new party session, with player 1 alone. */
	private void reopenWorld() throws Exception {
		onRender(() -> {
			minecraft.disconnectFromWorld(Component.literal("E2E"));
			return null;
		});
		waitFor("guests' games closed", Duration.ofSeconds(30), () -> satellites().isEmpty());
		waitFor("back on a menu", Duration.ofSeconds(60),
				() -> onRender(() -> minecraft.level == null && minecraft.getSingleplayerServer() == null));
		openWorld();
		waitFor("host session", Duration.ofSeconds(180),
				() -> Party.current() != null && onRender(() -> minecraft.level != null) && playerCount() == 1);
	}

	/**
	 * Q3: a guest's own Sodium settings survive its next launch (user session, 2026-10-11): the host's config is
	 * mirrored in before each launch, but not over a file the guest has changed. The host's copy is untouched.
	 */
	private void guestSodiumSettingsSurvive() throws Exception {
		Path host = minecraft.gameDirectory.toPath().resolve("config").resolve("sodium-options.json");
		Path guest = minecraft.gameDirectory.toPath().resolve("hearthside/satellite-1/config/sodium-options.json");
		guestLeaves();
		check(Files.exists(host) && Files.exists(guest), "Sodium's settings are in the host's and the guest's config");
		String hostBefore = Files.readString(host);
		String guestBefore = Files.readString(guest);
		String setting = "use_closest_point_entity_sort"; // a quality setting that changes nothing a check looks at
		com.google.gson.JsonObject edited = com.google.gson.JsonParser.parseString(guestBefore).getAsJsonObject();
		boolean mine = !edited.getAsJsonObject("quality").get(setting).getAsBoolean();
		edited.getAsJsonObject("quality").addProperty(setting, mine);
		Files.writeString(guest, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(edited));
		try {
			guestRejoins();
			boolean after = com.google.gson.JsonParser.parseString(Files.readString(guest)).getAsJsonObject()
					.getAsJsonObject("quality").get(setting).getAsBoolean();
			log("   guest's " + setting + " " + mine + " before its launch, " + after + " after");
			check(after == mine, "the guest's own Sodium setting survived its next launch");
			check(Files.readString(host).equals(hostBefore), "the host's sodium-options.json is unchanged");
		} finally {
			// As it was: the guest's copy follows the host's again from its next launch.
			Files.writeString(guest, guestBefore);
		}
	}

	/**
	 * Q4: a guest's game can't go fullscreen. Its game tries the option, the window and the toggle key once in the
	 * world: each is refused and logged, its window stays hidden and windowed, player 1's window doesn't move, and the
	 * guest's view still reaches the host. Its Video Settings (vanilla's, and Sodium's) show Fullscreen greyed out.
	 */
	private void guestRefusesFullscreen() throws Exception {
		System.setProperty("hearthside.e2e.guestTryFullscreen", "true");
		try {
			guestLeaves();
			guestRejoins();
			waitFor("the guest tried fullscreen", Duration.ofSeconds(30),
					() -> satelliteLog().contains("hearthside e2e guest fullscreen option="));
			String log = satelliteLog();
			String tried = log.substring(log.indexOf("hearthside e2e guest tries fullscreen"));
			int refused = tried.split(java.util.regex.Pattern.quote("hearthside guest fullscreen refused"), -1).length - 1;
			log("   " + tried.lines().filter(l -> l.contains("fullscreen")).map(String::strip).toList());
			check(refused == 3, "each of the 3 attempts was refused and logged once (got " + refused + ")");
			check(tried.contains("fullscreen option=false window fullscreen=false hidden=true"),
					"the guest's option stays off and its window windowed and hidden");
			Rect now = onRender(() -> WindowPlacer.bounds(minecraft.getWindow()));
			check(now.equals(hostWindowBefore), "player 1's window is where it was (" + now + ", was " + hostWindowBefore + ")");
			waitForProbe(2, hostLogLength(), Duration.ofSeconds(30));
			waitFor("the guest's Video Settings", Duration.ofSeconds(20),
					() -> satelliteLog().contains("hearthside guest video settings: fullscreen active=false"));
			Thread.sleep(1500); // its frames reach the host
			windowShot("guest-video-settings");
			if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
				waitFor("the guest's Sodium Video Settings", Duration.ofSeconds(20),
						() -> satelliteLog().contains("hearthside e2e guest shows sodium's video settings"));
				check(satelliteLog().contains("hearthside guest video settings: sodium's fullscreen greyed out"),
						"Sodium's Fullscreen is greyed out on the guest");
				Thread.sleep(1500);
				// Sodium draws its own widgets, which the hints can't see to keep clear of (they covered its Done).
				check(satelliteLog().contains("hearthside hints skipped on a mod's screen"),
						"controller hints stay off Sodium's screen");
				windowShot("guest-sodium-video-settings");
			}
			waitFor("the guest back in its game", Duration.ofSeconds(20),
					() -> satelliteLog().contains("hearthside e2e guest closed video settings"));
		} finally {
			System.clearProperty("hearthside.e2e.guestTryFullscreen");
		}
	}

	/**
	 * Q4b: on a Sodium version Hearthside wasn't tested with, a guest's game leaves Sodium's video options alone (greying
	 * Fullscreen there relies on Sodium's internals, and Sodium turns a failed overlay into a crash). It still reaches the
	 * world and still refuses fullscreen (batch review I3).
	 */
	private void untestedSodiumLeftAlone() throws Exception {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
			log("   no Sodium in this run");
			return;
		}
		System.setProperty("hearthside.e2e.pretendSodiumVersion", "0.10.0");
		System.setProperty("hearthside.e2e.guestTryFullscreen", "true");
		try {
			guestLeaves();
			guestRejoins();
			waitForProbe(2, hostLogLength(), Duration.ofSeconds(30));
			check(satelliteLog().contains("hearthside left sodium's video options alone: sodium 0.10.0 is untested"),
					"the guest says it left Sodium's options alone on Sodium 0.10.0");
			check(!satelliteLog().contains("hearthside guest video settings: sodium's fullscreen greyed out"),
					"and didn't grey Sodium's Fullscreen");
			waitFor("the guest tried fullscreen", Duration.ofSeconds(30),
					() -> satelliteLog().contains("hearthside e2e guest fullscreen option="));
			String log = satelliteLog();
			String tried = log.substring(log.indexOf("hearthside e2e guest tries fullscreen"));
			int refused = tried.split(Pattern.quote("hearthside guest fullscreen refused"), -1).length - 1;
			check(refused == 3, "each of the 3 attempts was still refused (got " + refused + ")");
			check(tried.contains("fullscreen option=false window fullscreen=false hidden=true"),
					"the guest's option stays off and its window windowed and hidden");
			waitFor("the guest back in its game", Duration.ofSeconds(30),
					() -> satelliteLog().contains("hearthside e2e guest closed video settings"));
		} finally {
			System.clearProperty("hearthside.e2e.pretendSodiumVersion");
			System.clearProperty("hearthside.e2e.guestTryFullscreen");
		}
	}

	/** Guest2 leaves and their game exits. */
	private void guestLeaves() throws Exception {
		onRender(() -> {
			Party.current().leave(GUEST);
			return null;
		});
		waitFor("guest gone", Duration.ofSeconds(30), () -> playerCount() == 1 && satellites().isEmpty());
	}

	/** Guest2 joins again (their game is launched afresh) and is in the world. */
	private void guestRejoins() throws Exception {
		joinMark = hostLogLength();
		onRender(() -> {
			Party.current().join(GUEST);
			return null;
		});
		waitFor("guest back", Duration.ofSeconds(180), () -> playerCount() == 2);
		waitInWorld(1);
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

	/** N7's overlay recorder: the scale and the views (GUI units) the overlay last drew, and seat 1's rects while recording. */
	private volatile int overlayScale;
	private volatile List<ViewFrame> overlayViews = List.of();
	private volatile java.util.Set<Rect> recording;

	private ViewFrame drawnView(int seat) {
		return overlayViews.stream().filter(v -> v.seat() == seat).findFirst().orElseThrow(
				() -> new AssertionError("missing: seat " + seat + "'s view in the overlay"));
	}

	/** The composited colour at a GUI pixel (the middle of it), after the overlay. */
	private int probeAt(int guiX, int guiY) throws Exception {
		int s = overlayScale;
		return probePixel(guiX * s + s / 2, guiY * s + s / 2);
	}

	/** The composited colour at a framebuffer pixel, after the overlay. */
	private int probePixel(int x, int y) throws Exception {
		long mark = hostLogLength();
		Compositor.e2eProbePoint = new int[] {x, y};
		try {
			Pattern line = Pattern.compile("hearthside overlay probe rgb=([0-9a-f]{6})");
			String[] rgb = {null};
			// The second line after the mark: the first may come from a read started before the point moved.
			waitFor("an overlay probe at " + x + "," + y, Duration.ofSeconds(5), () -> {
				Matcher m = line.matcher(hostLogSince(mark));
				int n = 0;
				while (m.find()) {
					if (++n == 2) {
						rgb[0] = m.group(1);
					}
				}
				return rgb[0] != null;
			});
			return Integer.parseInt(rgb[0], 16);
		} finally {
			Compositor.e2eProbePoint = null;
		}
	}

	/** Applies a change to player 1's Split-screen settings, as the Split-screen screen does. */
	private void applySplitScreen(java.util.function.UnaryOperator<SplitScreenConfig> change) throws Exception {
		onRender(() -> {
			Party.current().applySettings(change.apply(Party.current().config()));
			return null;
		});
	}

	/** Seat 1's distinct rects drawn while {@code body} runs and for {@code millis} after. */
	private java.util.Set<Rect> recordSeatOne(ThrowingRunnable body, long millis) throws Exception {
		recording = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
		try {
			body.run();
			Thread.sleep(millis);
			synchronized (recording) {
				return new java.util.LinkedHashSet<>(recording);
			}
		} finally {
			recording = null;
		}
	}

	/**
	 * N7 (Spec E section 3.5): player 1's pause menu opens Split-screen, whose changes apply live. Moving player 1 later
	 * puts them on the right. The stacked layout glides there; borders off and name tags off disappear at once; with
	 * layout animation off the views snap. The file keeps the new values; the title style applies the next time the
	 * title screen opens. Everything is put back afterwards.
	 */
	private void splitScreenOptions() throws Exception {
		Path file = minecraft.gameDirectory.toPath().resolve("config").resolve("hearthside.json");
		byte[] before = Files.exists(file) ? Files.readAllBytes(file) : null;
		SplitScreenConfig start = onRender(() -> Party.current().config());
		List<Integer> order = onRender(() -> Party.current().viewOrder());
		OverlayLayer recorder = (g, context) -> {
			overlayScale = context.guiScale();
			overlayViews = context.views();
			java.util.Set<Rect> into = recording;
			if (into != null) {
				context.views().stream().filter(v -> v.seat() == 1).forEach(v -> into.add(v.rect()));
			}
		};
		OverlayPass.register(recorder);
		long mark = hostLogLength();
		try {
			waitFor("the overlay drawn", Duration.ofSeconds(5), () -> overlayScale > 0 && overlayViews.size() == 2);
			check(start.layout().two() == LayoutSettings.TwoPlayer.SIDE_BY_SIDE && start.layout().borders() == BorderStyle.THIN
					&& start.layout().nameTags() && start.layout().layoutAnimation(), "starts from the defaults");

			// The way in: player 1's pause menu.
			onRender(() -> {
				minecraft.pauseGame(false);
				pressLabelled("Split-screen…");
				return null;
			});
			check(onRender(() -> minecraft.gui.screen() != null
					&& minecraft.gui.screen().getClass().getSimpleName().equals("SplitScreenScreen")),
					"player 1's pause menu opens the Split-screen screen");
			onRender(() -> {
				minecraft.gui.setScreen(null);
				return null;
			});

			// Swap views: player 1 later goes right, and back.
			onRender(() -> {
				Party.current().setViewOrder(ViewOrder.later(Party.current().viewOrder(), 0));
				return null;
			});
			waitFor("player 1's view on the right", Duration.ofSeconds(5),
					() -> drawnView(0).rect().x() > drawnView(1).rect().x());
			onRender(() -> {
				Party.current().setViewOrder(order);
				return null;
			});
			waitFor("player 1's view back on the left", Duration.ofSeconds(5),
					() -> drawnView(0).rect().x() < drawnView(1).rect().x());
			Thread.sleep(800);

			// The stacked layout, gliding.
			Rect sideBySide = drawnView(1).rect();
			java.util.Set<Rect> glide = recordSeatOne(() -> applySplitScreen(
					c -> c.withLayout(c.layout().withTwo(LayoutSettings.TwoPlayer.STACKED))), 1200);
			Rect stacked = drawnView(1).rect();
			log("   seat 1 drew " + glide.size() + " rects from " + sideBySide + " to " + stacked);
			check(stacked.y() > 0 && stacked.width() > sideBySide.width(), "the guest's view moved below player 1's");
			check(glide.size() >= 3, "the views glided there (" + glide.size() + " distinct rects)");
			check(hostLogSince(mark).contains("hearthside split-screen applied twoplayerlayout=stacked"), "and logged it");

			// Within the name tags' 4 s after the layout change: a tag and a border on the guest's view.
			int colour = onRender(() -> Party.current().colorOf(1)) & 0xFFFFFF;
			int tagX = stacked.x() + 14, tagY = stacked.y() + 13; // the colour square in the name tag
			int borderX = stacked.x() + 1, borderY = stacked.y(); // the top border, over the guest's magenta corner
			check(probeAt(tagX, tagY) == colour, "the guest's name tag shows after the layout change");
			check(probeAt(borderX, borderY) != 0xFF00FF, "the guest's view has its border");
			applySplitScreen(c -> c.withLayout(c.layout().withBorders(BorderStyle.OFF).withNameTags(false)));
			Thread.sleep(200);
			check(probeAt(tagX, tagY) != colour, "name tags off: the tag went at once");
			check(probeAt(borderX, borderY) == 0xFF00FF, "borders off: the border went at once");
			String applied = hostLogSince(mark);
			check(applied.contains("hearthside split-screen applied borders=off")
					&& applied.contains("hearthside split-screen applied nametags=false"), "and logged both");

			// Saved.
			SplitScreenConfig saved = SplitScreenConfig.read(new java.io.StringReader(Files.readString(file)));
			check(saved.layout().two() == LayoutSettings.TwoPlayer.STACKED && saved.layout().borders() == BorderStyle.OFF
					&& !saved.layout().nameTags(), "config/hearthside.json has the new values");

			// Back on, long after the layout change's tags would have faded: they show at once, as after a layout change.
			Thread.sleep(4600);
			applySplitScreen(c -> c.withLayout(c.layout().withBorders(BorderStyle.THIN).withNameTags(true)));
			Thread.sleep(200);
			check(probeAt(tagX, tagY) == colour, "name tags back on: the tag shows at once");
			check(probeAt(borderX, borderY) != 0xFF00FF, "borders back on: the border shows at once");

			// Layout animation off: back to side by side in one step.
			java.util.Set<Rect> snap = recordSeatOne(() -> applySplitScreen(c -> c.withLayout(
					c.layout().withLayoutAnimation(false).withTwo(LayoutSettings.TwoPlayer.SIDE_BY_SIDE))), 1200);
			log("   seat 1 drew " + snap + " with layout animation off");
			check(snap.size() <= 2, "with layout animation off the views snap (" + snap.size() + " distinct rects)");
			check(drawnView(1).rect().equals(sideBySide), "back side by side");

			// The title style applies the next time the title screen opens.
			applySplitScreen(c -> c.withTitleStyle(TitleStyleSetting.VANILLA));
			TitleStyle.drewBackground = false;
			TitleStyle.drewButtons = false;
			onRender(() -> {
				minecraft.gui.setScreen(new TitleScreen());
				return null;
			});
			Thread.sleep(600);
			check(!TitleStyle.drewBackground && !TitleStyle.drewButtons, "the vanilla title style: Minecraft's own title screen");
			onRender(() -> {
				minecraft.gui.setScreen(null);
				return null;
			});
			applySplitScreen(c -> c.withTitleStyle(TitleStyleSetting.HEARTHSIDE));
			onRender(() -> {
				minecraft.gui.setScreen(new TitleScreen());
				return null;
			});
			Thread.sleep(600);
			check(TitleStyle.drewBackground && TitleStyle.drewButtons, "and Hearthside's again when switched back");

			// Shown again, the same title screen takes the new style: Mod Menu's Configure goes back to the one it came from.
			TitleScreen same = onRender(() -> (TitleScreen) minecraft.gui.screen());
			onRender(() -> {
				minecraft.gui.setScreen(new dev.hearthside.fabric.client.host.SplitScreenScreen(same));
				return null;
			});
			applySplitScreen(c -> c.withTitleStyle(TitleStyleSetting.VANILLA));
			TitleStyle.drewBackground = false;
			TitleStyle.drewButtons = false;
			onRender(() -> {
				minecraft.gui.screen().onClose();
				return null;
			});
			check(onRender(() -> minecraft.gui.screen() == same), "Split-screen goes back to the title screen it came from");
			Thread.sleep(600);
			check(!TitleStyle.drewBackground && !TitleStyle.drewButtons, "that same title screen is now Minecraft's own");
		} finally {
			onRender(() -> {
				minecraft.gui.setScreen(null);
				Party.current().setViewOrder(order);
				Party.current().applySettings(start);
				return null;
			});
			OverlayPass.unregister(recorder);
			Thread.sleep(1000); // the views settle back before the next step
			if (before != null) {
				Files.write(file, before);
			} else {
				Files.deleteIfExists(file);
			}
		}
	}

	/**
	 * N7b (Spec E sections 3.5 and 5): a preset chosen on the Split-screen screen waits for the next player to join. In a
	 * window big enough for Performance to reduce a guest's resolution, Guest2 (already in the world) keeps their frame
	 * size through a relayout after player 1 picks a preset whose resolution differs from theirs.
	 */
	private void presetWaitsForTheNextJoin() throws Exception {
		Path file = minecraft.gameDirectory.toPath().resolve("config").resolve("hearthside.json");
		byte[] before = Files.exists(file) ? Files.readAllBytes(file) : null;
		SplitScreenConfig start = onRender(() -> Party.current().config());
		List<Integer> order = onRender(() -> Party.current().viewOrder());
		Rect window = onRender(() -> WindowPlacer.bounds(minecraft.getWindow()));
		long handle = onRender(() -> minecraft.getWindow().handle());
		try {
			// Views big enough for a GUI scale of 4 (over 1280 wide), where Performance renders guests at a reduced resolution.
			onRender(() -> SDLVideo.SDL_SetWindowSize(handle, 2720, 1440));
			String[] seen = {""};
			waitFor("Guest2's frame at the bigger window", Duration.ofSeconds(10), () -> {
				GuiScaleLine guest = lastGuiScale(satelliteLog(), 2);
				seen[0] = String.valueOf(guest);
				return guest != null && guest.viewWidth() >= 1300;
			});
			Thread.sleep(1500);
			GuiScaleLine launched = lastGuiScale(satelliteLog(), 2);
			boolean reduced = launched.frameWidth() < launched.viewWidth();
			Preset other = reduced ? Preset.QUALITY : Preset.PERFORMANCE;
			log("   Guest2 at launch: " + launched + (reduced ? " (reduced)" : " (full)") + "; player 1 picks " + other);
			GuiScaleRule.ViewScale wouldBe = GuiScaleRule.forView(new Rect(0, 0, launched.viewWidth(), launched.viewHeight()),
					reduced ? launched.scale() + 1 : launched.scale(), !reduced);
			check(wouldBe.frameWidth() != launched.frameWidth(), "that preset would size Guest2's frame differently (" + wouldBe + ")");
			applySplitScreen(c -> c.withPreset(other));
			// A relayout: player 1 swaps views and back.
			onRender(() -> {
				Party.current().setViewOrder(ViewOrder.later(Party.current().viewOrder(), 0));
				return null;
			});
			Thread.sleep(800);
			onRender(() -> {
				Party.current().setViewOrder(order);
				return null;
			});
			Thread.sleep(1500);
			GuiScaleLine now = lastGuiScale(satelliteLog(), 2);
			log("   Guest2 after the relayout: " + now);
			check(now.frameWidth() == launched.frameWidth() && now.frameHeight() == launched.frameHeight(),
					"Guest2, already in the world, keeps their frame size (" + launched.frameWidth() + "x"
							+ launched.frameHeight() + ", now " + now.frameWidth() + "x" + now.frameHeight() + ")");
		} finally {
			onRender(() -> {
				Party.current().setViewOrder(order);
				Party.current().applySettings(start);
				SDLVideo.SDL_SetWindowSize(handle, window.width(), window.height());
				return null;
			});
			Thread.sleep(2000); // the views settle back before the next step
			if (before != null) {
				Files.write(file, before);
			} else {
				Files.deleteIfExists(file);
			}
		}
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

	private static final int TILED_PAD = 130, TILED_TAKEOVER_PAD = 131;

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
			waitFor("their game in world mode", Duration.ofSeconds(60),
					() -> satelliteLog().contains("hearthside controller: world mode"));

			// Their controller drops (review Important 1): with tiled windows nothing draws a reconnect card, so their
			// pause menu shows, and the chat offers the take-over and says when it happened.
			int paused = count("hearthside satellite paused");
			int menus = count("hearthside pause menu opened");
			int hidden = count("hearthside pause menu hidden");
			long mark = hostLogLength();
			PADS.unplug(TILED_PAD);
			waitFor("their game paused", Duration.ofSeconds(10), () -> count("hearthside satellite paused") > paused);
			waitFor("their pause menu opened", Duration.ofSeconds(10), () -> count("hearthside pause menu opened") > menus);
			Thread.sleep(500);
			check(count("hearthside pause menu hidden") == hidden, "their pause menu shows: no card would stand in for it");
			jumpJoinClock(61);
			waitFor("the take-over offered in chat", Duration.ofSeconds(5),
					() -> hostLogSince(mark).contains("[chat] player 2's controller is still disconnected"));
			PADS.plug(TILED_TAKEOVER_PAD, "fake-tiled-takeover-pad", PadStyle.XBOX);
			Thread.sleep(300);
			press(TILED_TAKEOVER_PAD, PadButton.A);
			waitFor("another controller took over", Duration.ofSeconds(5),
					() -> hostLogSince(mark).contains("hearthside take-over player=2 controller=fake-tiled-takeover-pad"));
			waitFor("and the chat says so", Duration.ofSeconds(5),
					() -> hostLogSince(mark).contains("[chat] player 2 is playing on another controller now"));
		} finally {
			onRender(() -> {
				Party.current().leaveAll();
				return null;
			});
			PADS.unplug(TILED_PAD);
			PADS.unplug(TILED_TAKEOVER_PAD);
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

	private static final int OFFER_PAD = 108;
	private static final String OFFER_GUID = "fake-offer-pad";

	/**
	 * K1 (Spec D section 3.1), with the facts faked: OpenGL that can't share frames, Vulkan that can. Not with Iris; Not
	 * now is remembered; player 1's remembered controller answering it clicks nothing in the lobby with the A it still
	 * holds; Switch saves Vulkan and relaunches (the command is logged, not run); a relaunch that dies asks the player to
	 * start Minecraft again; a probe without a Vulkan loader offers nothing and breaks nothing.
	 */
	private void vulkanOffer() throws Exception {
		Path gameDir = minecraft.gameDirectory.toPath();
		SplitScreenConfig before = SplitScreenConfigFile.load(gameDir);
		PreferredGraphicsApi backendBefore = onRender(() -> minecraft.options.preferredGraphicsBackend().get());
		String rememberedBefore = profiles().loadFile().host().preferredControllerGuid();
		List<String> hooks = List.of("hearthside.e2e.fakeGlCantImport", "hearthside.e2e.fakeVulkanShares",
				"hearthside.e2e.fakeIris", "hearthside.e2e.dryRelaunch", "hearthside.e2e.noVulkanLoader");
		PADS.plug(OFFER_PAD, OFFER_GUID, PadStyle.XBOX);
		try {
			System.setProperty("hearthside.e2e.fakeGlCantImport", "true");
			System.setProperty("hearthside.e2e.fakeVulkanShares", "true");
			if (!onRender(() -> RenderSystem.getDevice().getDeviceInfo().backendName()).equals("OpenGL")) {
				SplitScreenConfigFile.save(gameDir, before.withVulkanOffer(VulkanOffer.PENDING));
				check(pressCoop() instanceof LobbyScreen, "on Vulkan, Co-op opens the lobby (the offer is for OpenGL)");
				leaveLobby();
				log("   K1: this game runs Vulkan; the offer doesn't apply");
				return;
			}

			// (a) With Iris installed: no card, and the log says why.
			System.setProperty("hearthside.e2e.fakeIris", "true");
			SplitScreenConfigFile.save(gameDir, before.withVulkanOffer(VulkanOffer.PENDING));
			long mark = hostLogLength();
			check(pressCoop() instanceof LobbyScreen, "with Iris, Co-op opens the lobby directly");
			check(hostLogSince(mark).contains("hearthside vulkan offer: not offered (iris is installed)"), "the log says Iris is why");
			leaveLobby();

			// (b) The card; Not now, chosen on a controller, opens the lobby and is remembered.
			System.setProperty("hearthside.e2e.fakeIris", "false");
			mark = hostLogLength();
			check(onOfferCard(pressCoop()), "the offer card shows before the lobby");
			check(hostLogSince(mark).contains("hearthside vulkan offer: shown"), "the log says the offer was shown");
			check(onRender(this::focusedLabel).equals("Switch and restart"), "Switch and restart is focused first");
			screenshot("vulkan-offer");
			focusWithPad(OFFER_PAD, "Not now");
			press(OFFER_PAD, PadButton.A);
			waitFor("the lobby after Not now", Duration.ofSeconds(5), () -> onRender(() -> minecraft.gui.screen() instanceof LobbyScreen));
			check(SplitScreenConfigFile.load(gameDir).vulkanOffer() == VulkanOffer.DECLINED, "Not now is remembered");
			leaveLobby();
			check(pressCoop() instanceof LobbyScreen, "once answered, Co-op opens the lobby directly");
			leaveLobby();

			// (g) Co-op pressed with the mouse; player 1's remembered controller answers Not now with A. The lobby hands it
			// to player 1 while that A is still held: it clicks nothing there (final review F3).
			ProfileFile profilesNow = profiles().loadFile();
			profiles().saveFile(new ProfileFile(profilesNow.profiles(), profilesNow.host().withController(OFFER_GUID)));
			SplitScreenConfigFile.save(gameDir, before.withVulkanOffer(VulkanOffer.PENDING));
			countLobbyClicks();
			check(onOfferCard(pressCoop()), "the card, Co-op pressed with the mouse");
			focusWithPad(OFFER_PAD, "Not now");
			lobbyClicks.set(0);
			PADS.set(OFFER_PAD, new InputFrame(0, InputFrame.buttons(PadButton.A), 0, 0, 0, 0, 0, 0));
			try {
				waitFor("player 1 has their remembered controller in the lobby", Duration.ofSeconds(5), () -> onRender(
						() -> minecraft.gui.screen() instanceof LobbyScreen && Party.current() != null && Party.current().hostHasController()));
				Thread.sleep(300);
			} finally {
				PADS.set(OFFER_PAD, InputFrame.NEUTRAL);
			}
			Thread.sleep(200);
			check(lobbyClicks.get() == 0, "the A still held from Not now clicks nothing in the lobby (" + lobbyClicks.get() + " clicks)");
			onRender(() -> {
				if (Party.current() != null) {
					Party.current().releaseController();
				}
				return null;
			});
			leaveLobby();

			// (c) Switch and restart: Vulkan is saved and the relaunch command logged (a dry run starts nothing).
			SplitScreenConfigFile.save(gameDir, before.withVulkanOffer(VulkanOffer.PENDING));
			System.setProperty("hearthside.e2e.dryRelaunch", "true");
			mark = hostLogLength();
			check(onOfferCard(pressCoop()), "the card again, once the answer is reset");
			press(OFFER_PAD, PadButton.A);
			long from = mark;
			waitFor("the relaunch command in the log", Duration.ofSeconds(10),
					() -> hostLogSince(from).contains("hearthside relaunch command: "));
			String command = hostLogSince(mark).lines().filter(l -> l.contains("hearthside relaunch command: ")).findFirst().orElseThrow();
			check(!command.contains("--graphicsbackend opengl"), "the relaunch doesn't force OpenGL");
			Matcher token = Pattern.compile("--accesstoken (\\S+)").matcher(command);
			check(!token.find() || token.group(1).equals("<redacted>"), "the access token is redacted in the log");
			check(onRender(() -> minecraft.options.preferredGraphicsBackend().get()) == PreferredGraphicsApi.VULKAN,
					"Switch set the Graphics API to Vulkan");
			check(SplitScreenConfigFile.load(gameDir).vulkanOffer() == VulkanOffer.ACCEPTED, "Switch is remembered");
			restoreBackend(backendBefore);

			// (d) A relaunch whose game dies at once: the player is asked to start Minecraft again.
			SplitScreenConfigFile.save(gameDir, before.withVulkanOffer(VulkanOffer.PENDING));
			System.setProperty("hearthside.e2e.dryRelaunch", "dead");
			check(onOfferCard(pressCoop()), "the card for the dead relaunch");
			press(OFFER_PAD, PadButton.A);
			waitFor("the restart card", Duration.ofSeconds(10), () -> onRender(
					() -> minecraft.gui.screen() != null && minecraft.gui.screen().getTitle().getString().contains("Please start Minecraft again")));
			screenshot("vulkan-offer-restart");
			restoreBackend(backendBefore);
			System.clearProperty("hearthside.e2e.dryRelaunch");

			// (e) The real probe without a Vulkan loader: no card, no crash, and the log says why.
			System.clearProperty("hearthside.e2e.fakeVulkanShares");
			System.setProperty("hearthside.e2e.noVulkanLoader", "true");
			SplitScreenConfigFile.save(gameDir, before.withVulkanOffer(VulkanOffer.PENDING));
			mark = hostLogLength();
			check(pressCoop() instanceof LobbyScreen, "without a Vulkan loader, Co-op opens the lobby directly");
			check(hostLogSince(mark).contains("hearthside vulkan offer: not offered (vulkan can't share frames)"),
					"the log says Vulkan can't share");
			leaveLobby();

			// (f) The real probe on this machine's Vulkan (task review: it never ran otherwise before the laptop): the card
			// shows exactly when the probe says this machine's Vulkan can share frames, and nothing breaks either way.
			System.clearProperty("hearthside.e2e.noVulkanLoader");
			SplitScreenConfigFile.save(gameDir, before.withVulkanOffer(VulkanOffer.PENDING));
			mark = hostLogLength();
			Screen opened = pressCoop();
			String probed = hostLogSince(mark);
			log("   K1 (f) real probe: " + probed.lines()
					.filter(l -> l.contains("hearthside vulkan probe") || l.contains("hearthside vulkan offer")).toList());
			boolean card = onOfferCard(opened);
			check(card == probed.contains("hearthside vulkan offer: shown"), "the card shows exactly when the log says so");
			check(card || probed.contains("not offered (vulkan can't share frames)"), "the real probe answered: a card or the reason");
			if (card) {
				onRender(() -> {
					minecraft.gui.screen().onClose();
					return null;
				});
			} else {
				leaveLobby();
			}
		} finally {
			hooks.forEach(System::clearProperty);
			SplitScreenConfigFile.save(gameDir, before);
			restoreBackend(backendBefore);
			PADS.set(OFFER_PAD, InputFrame.NEUTRAL);
			PADS.unplug(OFFER_PAD);
			ProfileFile profilesAfter = profiles().loadFile();
			if (!java.util.Objects.equals(profilesAfter.host().preferredControllerGuid(), rememberedBefore)) {
				profiles().saveFile(new ProfileFile(profilesAfter.profiles(), profilesAfter.host().withController(rememberedBefore)));
			}
			onRender(() -> {
				if (!(minecraft.gui.screen() instanceof TitleScreen)) {
					minecraft.gui.setScreen(new TitleScreen());
				}
				return null;
			});
		}
	}

	/** Mouse clicks (real or a controller's pointer) that reached a lobby screen, once {@link #countLobbyClicks} has run. */
	private static final java.util.concurrent.atomic.AtomicInteger lobbyClicks = new java.util.concurrent.atomic.AtomicInteger();
	private static boolean countingLobbyClicks;

	/** From now on, every lobby screen counts the clicks it gets in {@link #lobbyClicks}. */
	private void countLobbyClicks() throws Exception {
		onRender(() -> {
			if (!countingLobbyClicks) {
				countingLobbyClicks = true;
				ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
					if (screen instanceof LobbyScreen) {
						ScreenMouseEvents.beforeMouseClick(screen).register((s, event) -> lobbyClicks.incrementAndGet());
					}
				});
			}
			return null;
		});
	}

	/** Presses the title screen's Co-op button and returns the screen it opened. */
	private Screen pressCoop() throws Exception {
		return onRender(() -> {
			minecraft.gui.setScreen(new TitleScreen());
			coopButton().onPress(new KeyEvent(0, 0, 0));
			return minecraft.gui.screen();
		});
	}

	/** Leaves an empty lobby back to the title screen, as Back does. */
	private void leaveLobby() throws Exception {
		onRender(() -> {
			minecraft.gui.screen().onClose();
			return null;
		});
		check(onRender(() -> minecraft.gui.screen() instanceof TitleScreen) && Party.current() == null, "back on the title screen");
	}

	private static boolean onOfferCard(Screen screen) {
		return screen != null && screen.getClass().getSimpleName().equals("VulkanOfferScreen");
	}

	/** The label of the open screen's focused widget, or "". Render thread. */
	private String focusedLabel() {
		return minecraft.gui.screen() != null && minecraft.gui.screen().getFocused() instanceof AbstractWidget widget
				? widget.getMessage().getString() : "";
	}

	/** Moves focus with {@code pad}'s D-pad (down, then right) until the widget labelled {@code label} has it. */
	private void focusWithPad(int pad, String label) throws Exception {
		PadButton[] path = {PadButton.DPAD_DOWN, PadButton.DPAD_DOWN, PadButton.DPAD_RIGHT, PadButton.DPAD_RIGHT};
		for (int i = 0; i < path.length && !onRender(this::focusedLabel).equals(label); i++) {
			press(pad, path[i]);
			Thread.sleep(150);
		}
		check(onRender(this::focusedLabel).equals(label), "the D-pad moved focus to " + label);
	}

	/** Puts player 1's Graphics API option back and saves it. */
	private void restoreBackend(PreferredGraphicsApi backend) throws Exception {
		onRender(() -> {
			if (minecraft.options.preferredGraphicsBackend().get() != backend) {
				minecraft.options.preferredGraphicsBackend().set(backend);
				minecraft.options.save();
			}
			return null;
		});
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
			// Player 1's PC keyboard on it (Spec E section 3.6): a held Backspace deletes three, Enter keeps the name.
			String before = onRender(screen::worldName);
			pcHoldKey(InputConstants.KEY_BACKSPACE, 2);
			pcKey(InputConstants.KEY_RETURN);
			String kept = onRender(screen::worldName);
			check(kept.equals(before.substring(0, before.length() - 3)) && !onRender(screen::typing),
					"a held Backspace deleted three characters and Enter kept the name (" + before + " → " + kept + ")");
			press(HOST_PAD, PadButton.A);
			check(onRender(screen::typing), "A on the name opens the keyboard again");
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
		check(labels.equals(List.of("Type", "Close", "_", "Del", "Aa", "Done")),
				"the legend says A Type, B Close, Y _ (a name has no spaces), X Del, LB/RB Aa and Menu Done (" + labels + ")");
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
			// A world's name is free text (W3's keyboard): there Y types a space.
			NewWorldScreen newWorld = onRender(() -> (NewWorldScreen) minecraft.gui.screen());
			pressUntil(HOST_PAD, PadButton.DPAD_UP, 6, () -> newWorld.focusedRow().equals("name"));
			press(HOST_PAD, PadButton.A);
			check(onRender(newWorld::typing), "A on the world's name opens the keyboard");
			Thread.sleep(300);
			List<String> worldLegend = dev.hearthside.fabric.client.ui.KeyboardWidget.drawnLegend;
			log("   world-name legend: " + worldLegend);
			List<String> worldLabels = worldLegend.stream().map(entry -> entry.substring(entry.indexOf(' ') + 1)).toList();
			check(worldLabels.equals(List.of("Type", "Close", "Space", "Del", "Aa!", "Done")),
					"the world-name legend says A Type, B Close, Y Space, X Del, LB/RB Aa! and Menu Done (" + worldLabels + ")");
			press(HOST_PAD, PadButton.B);
			check(!onRender(newWorld::typing), "B closes the world-name keyboard");
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
		// Vanilla's pause screen before Spec E, Hearthside's pause menu after: either is "(pause…".
		int pausesBefore = count("hearthside controller: menu mode (pause");
		int worldBefore = count("hearthside controller: world mode");
		press(102, PadButton.START);
		waitFor("pause menu on the guest", Duration.ofSeconds(10),
				() -> count("hearthside controller: menu mode (pause") > pausesBefore);
		press(102, PadButton.B);
		waitFor("pause menu closed", Duration.ofSeconds(10), () -> count("hearthside controller: world mode") > worldBefore);
	}

	/**
	 * N1 (Spec E section 3.1, plan R7): a guest's START opens Hearthside's pause menu in its own game, focus on Resume.
	 * The D-pad and A open Options… and Game menu…, which are vanilla's screens and come back to the menu; Resume closes
	 * it. The guest's screens are read from its log.
	 */
	private void guestPauseMenu() throws Exception {
		String entries = "hearthside pause menu opened entries=resume,options…,game menu…,leave game";
		int opened = count(entries);
		press(102, PadButton.START);
		waitFor("Hearthside's pause menu in the guest's game", Duration.ofSeconds(10), () -> count(entries) > opened);

		int options = count("hearthside screen optionsscreen");
		press(102, PadButton.DPAD_DOWN);
		press(102, PadButton.A);
		waitFor("vanilla's Options in the guest's game", Duration.ofSeconds(10),
				() -> count("hearthside screen optionsscreen") > options);
		int back = count("hearthside screen pausemenuscreen");
		press(102, PadButton.B);
		waitFor("B from Options back to Hearthside's menu", Duration.ofSeconds(10),
				() -> count("hearthside screen pausemenuscreen") > back);

		int vanilla = count("hearthside screen pausescreen");
		press(102, PadButton.DPAD_DOWN);
		press(102, PadButton.DPAD_DOWN);
		press(102, PadButton.A);
		waitFor("vanilla's pause screen under Game menu…", Duration.ofSeconds(10),
				() -> count("hearthside screen pausescreen") > vanilla);
		int backAgain = count("hearthside screen pausemenuscreen");
		press(102, PadButton.B);
		waitFor("B from vanilla's pause screen back to Hearthside's menu", Duration.ofSeconds(10),
				() -> count("hearthside screen pausemenuscreen") > backAgain);

		int world = count("hearthside controller: world mode");
		press(102, PadButton.A);
		waitFor("Resume (focused when the menu opens) closes it", Duration.ofSeconds(10),
				() -> count("hearthside controller: world mode") > world);
	}

	/** A stand-in for a mod that adds a button to vanilla's pause screen, as Mod Menu does (Spec E goal 5). */
	private static final String STAND_IN = "E2E mod button";
	private static boolean standInRegistered;

	private static void addStandInModButton() {
		if (standInRegistered) {
			return;
		}
		standInRegistered = true;
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof PauseScreen) {
				Screens.getWidgets(screen).add(Button.builder(Component.literal(STAND_IN), b -> {}).bounds(4, 4, 98, 20).build());
			}
		});
	}

	/** The labels of a screen's buttons. Render thread. */
	private static List<String> buttonLabels(Screen screen) {
		return screen == null ? List.of() : Screens.getWidgets(screen).stream().map(w -> w.getMessage().getString()).toList();
	}

	/** Presses the open screen's button labelled {@code label}. Render thread. */
	private void pressLabelled(String label) {
		Screens.getWidgets(minecraft.gui.screen()).stream().filter(w -> w instanceof AbstractButton && w.getMessage().getString().equals(label)).map(w -> (AbstractButton) w).findFirst()
				.orElseThrow(() -> new AssertionError("missing: a button labelled " + label + " on "
						+ minecraft.gui.screen().getClass().getSimpleName()))
				.onPress(new KeyEvent(0, 0, 0));
	}

	private static boolean onPauseMenu(Screen screen) {
		return screen != null && screen.getClass().getSimpleName().equals("PauseMenuScreen");
	}

	/** N2 (Spec E section 3.1, goal 5): playing alone, the pause key opens vanilla's pause screen, a mod's button and all. */
	private void alonePauseIsVanilla() throws Exception {
		addStandInModButton();
		waitFor("player 1 in play", Duration.ofSeconds(60), () -> onRender(() -> minecraft.player != null && minecraft.gui.screen() == null));
		try {
			Screen opened = onRender(() -> {
				minecraft.pauseGame(false);
				return minecraft.gui.screen();
			});
			check(opened instanceof PauseScreen, "alone, the pause key opens vanilla's pause screen (got "
					+ (opened == null ? "nothing" : opened.getClass().getSimpleName()) + ")");
			check(onRender(() -> buttonLabels(minecraft.gui.screen())).contains(STAND_IN), "with the mod's button on it");
		} finally {
			onRender(() -> {
				minecraft.gui.setScreen(null);
				return null;
			});
		}
	}

	/**
	 * N2b (Spec E section 3.1, goal 5): in split-screen, player 1's pause key opens Hearthside's menu, which other mods
	 * still see as a pause screen; Game menu… opens vanilla's pause screen with the mod's button; closing it returns to
	 * Hearthside's menu.
	 */
	private void splitScreenPauseKeepsModButtons() throws Exception {
		addStandInModButton();
		long mark = hostLogLength();
		try {
			Screen opened = onRender(() -> {
				minecraft.pauseGame(false);
				return minecraft.gui.screen();
			});
			check(onPauseMenu(opened), "in split-screen, player 1's pause key opens Hearthside's menu (got "
					+ (opened == null ? "nothing" : opened.getClass().getSimpleName()) + ")");
			check(opened.isPauseScreen(), "Hearthside's menu still counts as a pause screen for other mods");
			check(hostLogSince(mark).contains("hearthside pause menu opened entries=") && hostLogSince(mark).contains("game menu…"),
					"player 1's menu logged its entries, Game menu… among them");
			onRender(() -> {
				pressLabelled("Game menu…");
				return null;
			});
			check(onRender(() -> minecraft.gui.screen() instanceof PauseScreen), "Game menu… opens vanilla's pause screen");
			check(onRender(() -> buttonLabels(minecraft.gui.screen())).contains(STAND_IN), "with the mod's button on it");
			onRender(() -> {
				minecraft.gui.screen().onClose();
				return null;
			});
			check(onRender(() -> onPauseMenu(minecraft.gui.screen())), "closing vanilla's pause screen returns to Hearthside's menu");
		} finally {
			onRender(() -> {
				minecraft.gui.setScreen(null);
				return null;
			});
		}
	}

	/** A controller that joins during N3 and is still choosing a profile while everyone in the world is paused. */
	private static final int PICKING_PAD = 111;

	private static long gameTime(IntegratedServer server) {
		return server.overworld().getGameTime();
	}

	/**
	 * A silent looping sound in player 1's game. Its channel is paused and resumed with every other world sound, so it
	 * shows whether player 1's game's sounds are paused (N3).
	 */
	private static final class SoundProbe extends net.minecraft.client.resources.sounds.AbstractSoundInstance {
		SoundProbe() {
			super(net.minecraft.sounds.SoundEvents.MINECART_RIDING, net.minecraft.sounds.SoundSource.AMBIENT,
					net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
			looping = true;
			volume = 0.0F;
			relative = true;
			attenuation = Attenuation.NONE;
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}

	/** The probe's channel: "playing", "paused", "stopped", or "none" (not started: no sound device). */
	private String soundState(SoundProbe probe) throws Exception {
		java.util.concurrent.CompletableFuture<String> state = new java.util.concurrent.CompletableFuture<>();
		boolean asked = onRender(() -> {
			var engine = ((dev.hearthside.fabric.client.mixin.SoundManagerAccessor) minecraft.getSoundManager())
					.hearthside$soundEngine();
			var handle = ((dev.hearthside.fabric.client.mixin.SoundEngineAccessor) engine).hearthside$instanceToChannel()
					.get(probe);
			if (handle == null) {
				return false;
			}
			handle.execute(channel -> state.complete(channel.playing() ? "playing" : channel.stopped() ? "stopped" : "paused"));
			return true;
		});
		return asked ? state.completeOnTimeout("none", 2, TimeUnit.SECONDS).get() : "none";
	}

	/** Whether a menu's drawn status lines are {@code expected} whole: every word there, nothing cut short. */
	private static boolean wholeStatus(List<String> drawn, String expected) {
		return drawn != null && String.join(" ", drawn).replace(" ", "").toLowerCase(Locale.ROOT)
				.equals(expected.replace(" ", "").toLowerCase(Locale.ROOT));
	}

	/** Player 1's open pause menu draws {@code expected} as its status, whole (wrapped as it needs, never cut). */
	private void waitForMenuStatusDrawnWhole(String expected) throws Exception {
		List<String>[] drawn = new List[1];
		try {
			waitFor("player 1's menu draws \"" + expected + "\" whole", Duration.ofSeconds(3), () -> {
				drawn[0] = onRender(() -> dev.hearthside.fabric.client.menu.PauseMenuScreen.e2eDrawnStatus(minecraft));
				return wholeStatus(drawn[0], expected);
			});
		} catch (AssertionError e) {
			throw new AssertionError(e.getMessage() + " (drawn " + drawn[0] + ")");
		}
	}

	/**
	 * A guest's pause-menu layouts logged after its {@code skip} first ones, each as its drawn lines: only those not cut
	 * short (the satellite logs them in E2E runs).
	 */
	private static List<List<String>> drawnStatuses(String satelliteLog, int skip) {
		Matcher m = Pattern.compile("hearthside pause status lines=\\d+ truncated=(true|false) drawn=([^\\r\\n]*)")
				.matcher(satelliteLog);
		List<List<String>> layouts = new ArrayList<>();
		for (int n = 0; m.find(); n++) {
			if (n >= skip && m.group(1).equals("false")) {
				layouts.add(List.of(m.group(2).split("\\|")));
			}
		}
		return layouts;
	}

	/** The open screen's narration, which carries the pause menu's status line. Render thread. */
	private String narration() {
		Screen screen = minecraft.gui.screen();
		return screen == null ? "" : screen.getNarrationMessage().getString();
	}

	/**
	 * N3 (Spec E section 3.2, plan R5): the world freezes only when everyone in it is paused. (a) Player 1 paused while
	 * Player2 plays: the world runs and player 1's menu says who is still playing. (b) Player2 pausing too freezes it
	 * within a second, and both menus say so; (d) a newcomer still choosing a profile doesn't count. (c) Player2 resuming
	 * starts it again. Player 1's status is read from their menu's narration, Player2's from its log. First, Player2
	 * pauses alone (START, then B) while player 1 plays: their menu draws "The world keeps going — <player 1> is still
	 * playing" whole, wrapped as it needs and never cut short (from its layout log line; the status-line screenshot), and
	 * player 1's menu draws its own status whole at (a) and (b).
	 */
	private void pauseTogether() throws Exception {
		IntegratedServer server = minecraft.getSingleplayerServer();
		PADS.plug(PICKING_PAD, "fake-picking-pad", PadStyle.XBOX);
		Thread.sleep(300);
		SoundProbe probe = new SoundProbe();
		try {
			// Player 1's game is the couch's only audio (guests are muted): its sounds follow the world, not their menu.
			onRender(() -> {
				minecraft.getSoundManager().play(probe);
				return null;
			});
			long probeUntil = System.nanoTime() + Duration.ofSeconds(5).toNanos();
			while (!soundState(probe).equals("playing") && System.nanoTime() < probeUntil) {
				Thread.sleep(100);
			}
			boolean sound = soundState(probe).equals("playing");
			if (!sound) {
				log("   no sound device: N3's sound checks are skipped");
			}
			// Player2 paused while player 1 plays: their menu's status names player 1 in full, wrapped, never cut short.
			String host = minecraft.getUser().getName();
			int statusLines = count("hearthside pause status lines=");
			int ownPaused = count("hearthside pause state paused");
			press(102, PadButton.START);
			waitFor("Player2's game says it's paused", Duration.ofSeconds(10),
					() -> count("hearthside pause state paused") > ownPaused);
			String guestLine = "The world keeps going — " + host + " is still playing";
			waitFor("Player2's menu laid out for \"" + guestLine + "\"", Duration.ofSeconds(10),
					() -> drawnStatuses(satelliteLog(), statusLines).stream().anyMatch(d -> wholeStatus(d, guestLine)));
			windowShot("status-line");
			int ownPlaying = count("hearthside pause state playing");
			press(102, PadButton.B);
			waitFor("Player2's game says it's playing", Duration.ofSeconds(10),
					() -> count("hearthside pause state playing") > ownPlaying);

			press(PICKING_PAD, PadButton.A);
			press(PICKING_PAD, PadButton.A);
			waitFor("a newcomer choosing a profile", Duration.ofSeconds(10), () -> onRender(() -> card(2) != null));

			long mark = hostLogLength();
			onRender(() -> {
				minecraft.pauseGame(false);
				return null;
			});
			check(onRender(() -> minecraft.gui.isPausing()), "player 1 paused");
			Thread.sleep(500);
			long running = gameTime(server);
			Thread.sleep(1000);
			check(gameTime(server) > running, "(a) player 1 paused while Player2 plays: the world keeps going");
			check(hostLogSince(mark).contains("hearthside world running still playing=player2"),
					"player 1's game logs that Player2 is still playing");
			check(onRender(this::narration).contains("The world keeps going — Player2 is still playing"),
					"player 1's menu says Player2 is still playing (got \"" + onRender(this::narration) + "\")");
			waitForMenuStatusDrawnWhole("The world keeps going — Player2 is still playing");
			if (sound) {
				String state = soundState(probe);
				check(state.equals("playing"), "player 1's game's sounds play on while the world runs (got " + state + ")");
			}

			int pausedLines = count("hearthside pause state paused");
			int frozenLines = count("hearthside world frozen");
			press(102, PadButton.START);
			waitFor("Player2's game says it's paused", Duration.ofSeconds(10),
					() -> count("hearthside pause state paused") > pausedLines);
			Thread.sleep(1000);
			long frozen = gameTime(server);
			Thread.sleep(1000);
			check(gameTime(server) == frozen, "(b) everyone in the world paused: the world froze within a second");
			check(onRender(() -> card(2) != null), "(d) though a newcomer is still choosing a profile");
			check(hostLogSince(mark).contains("hearthside world frozen"), "player 1's game logs the world frozen");
			check(onRender(this::narration).contains("Everyone's paused — the world is frozen"),
					"player 1's menu says the world is frozen (got \"" + onRender(this::narration) + "\")");
			waitForMenuStatusDrawnWhole("Everyone's paused — the world is frozen");
			if (sound) {
				String state = soundState(probe);
				check(state.equals("paused"), "player 1's game's sounds pause with the world (got " + state + ")");
			}
			waitFor("Player2's menu told the world is frozen", Duration.ofSeconds(5),
					() -> count("hearthside world frozen") > frozenLines);

			int playingLines = count("hearthside pause state playing");
			press(102, PadButton.A); // Resume, focused whenever the menu opens
			waitFor("Player2's game says it's playing", Duration.ofSeconds(10),
					() -> count("hearthside pause state playing") > playingLines);
			Thread.sleep(500);
			long resumed = gameTime(server);
			Thread.sleep(1000);
			check(gameTime(server) > resumed, "(c) Player2 resuming starts the world again");
			check(onRender(this::narration).contains("Player2 is still playing"), "and player 1's menu says so");
			if (sound) {
				String state = soundState(probe);
				check(state.equals("playing"),
						"player 1's game's sounds play again with the world, their menu still open (got " + state + ")");
			}
		} finally {
			onRender(() -> {
				minecraft.gui.setScreen(null);
				minecraft.getSoundManager().stop(probe);
				return null;
			});
			if (onRender(() -> card(2) != null)) {
				press(PICKING_PAD, PadButton.B);
			}
			waitFor("the newcomer gone", Duration.ofSeconds(10), () -> onRender(() -> card(2) == null));
			PADS.unplug(PICKING_PAD);
		}
	}

	/** The controller that takes Player2 over in N5. */
	private static final int TAKEOVER_PAD = 112;
	private static final String TAKEOVER_GUID = "fake-takeover-pad";

	/** The guest's view's top-left corner, inside the magenta patch its game paints there in E2E runs (framebuffer). */
	private int[] guestCorner() throws Exception {
		Rect r = onRender(() -> Compositor.active().overlayViews().stream().filter(v -> v.seat() == 1).findFirst()
				.orElseThrow(() -> new AssertionError("missing: seat 1's view")).rect());
		return new int[] {r.x() + 4, r.y() + 4};
	}

	private static boolean dimmedMagenta(int rgb) {
		return rgb != 0xFF00FF && (rgb >> 16 & 0xFF) < 0xC0 && (rgb & 0xFF) < 0xC0 && (rgb >> 8 & 0xFF) < 0x40;
	}

	/** Jumps the party's join clock {@code seconds} ahead. Never back: the flow's deadlines only move forward. */
	private void jumpJoinClock(long seconds) {
		Party.e2eTimeShift = Party.e2eTimeShift.plusSeconds(seconds);
	}

	/** The controller that answers Add player in N6. */
	private static final int ADD_PAD = 113;

	/** Player 1's pause menu, then its Add player entry, as player 1 would choose it. */
	private void chooseAddPlayer() throws Exception {
		onRender(() -> {
			minecraft.pauseGame(false);
			pressLabelled("Add player");
			return null;
		});
		check(onRender(() -> minecraft.gui.screen() == null), "Add player closes the menu");
	}

	/** Backs a newcomer out of their profile picker and waits until their seat is free. */
	private void backOut(int pad) throws Exception {
		if (onRender(() -> card(2) != null)) {
			press(pad, PadButton.B);
		}
		waitFor("the newcomer gone", Duration.ofSeconds(10), () -> onRender(() -> card(2) == null));
	}

	/**
	 * N6 (Spec E section 3.4, Review Focus 5): player 1's Add player closes the menu and shows the prompt for 15 s (the
	 * join clock jumped past it). Then one A on a free controller joins, without the usual second A, even from a controller
	 * whose own double-A prompt was pending. Esc on player 1's keyboard cancels the prompt without opening the menu.
	 */
	private void addPlayer() throws Exception {
		long mark = hostLogLength();
		try {
			// (b) The prompt expires after 15 s.
			chooseAddPlayer();
			waitFor("the add-player prompt", Duration.ofSeconds(5), () -> hostLogSince(mark).contains("hearthside add player prompt shown"));
			Thread.sleep(400); // slid into place
			windowShot("add-player-connect"); // no free controller yet: "Connect a controller, then press A"
			jumpJoinClock(16);
			waitFor("(b) the prompt expired", Duration.ofSeconds(5),
					() -> hostLogSince(mark).contains("hearthside add player prompt expired"));

			// (a) One A on a free controller joins.
			PADS.plug(ADD_PAD, "fake-add-pad", PadStyle.XBOX);
			Thread.sleep(300);
			long joined = hostLogLength();
			chooseAddPlayer();
			Thread.sleep(400);
			windowShot("add-player"); // "Press A on a new controller to join"
			press(ADD_PAD, PadButton.A);
			waitFor("(a) one A joins: a newcomer choosing a profile", Duration.ofSeconds(5),
					() -> onRender(() -> card(2) != null));
			check(hostLogSince(joined).contains("hearthside add player prompt joined"), "and the prompt says so");
			backOut(ADD_PAD);

			// (c) A controller whose double-A prompt is pending joins with its next A once player 1 asks.
			press(ADD_PAD, PadButton.A); // its own join prompt: "press A again"
			long pending = hostLogLength();
			chooseAddPlayer();
			press(ADD_PAD, PadButton.A);
			waitFor("(c) the pending controller joined", Duration.ofSeconds(5), () -> onRender(() -> card(2) != null));
			check(hostLogSince(pending).contains("hearthside add player prompt joined"), "through Add player");
			backOut(ADD_PAD);

			// Esc on player 1's keyboard cancels the prompt, and only that.
			long cancel = hostLogLength();
			chooseAddPlayer();
			pcKey(InputConstants.KEY_ESCAPE);
			waitFor("Esc cancelled the prompt", Duration.ofSeconds(5),
					() -> hostLogSince(cancel).contains("hearthside add player prompt cancelled"));
			check(onRender(() -> minecraft.gui.screen() == null), "and didn't open the pause menu");
		} finally {
			onRender(() -> {
				minecraft.gui.setScreen(null);
				return null;
			});
			backOut(ADD_PAD);
			PADS.unplug(ADD_PAD);
			jumpJoinClock(16); // any prompt still showing has expired
			Thread.sleep(1000); // the views settle back before the next step
		}
	}

	/** N6b (plan R3): with four players, player 1's Add player is greyed out. */
	private void addPlayerGreyedWhenFull() throws Exception {
		check(playerCount() == 4, "four players");
		try {
			boolean active = onRender(() -> {
				minecraft.pauseGame(false);
				return Screens.getWidgets(minecraft.gui.screen()).stream()
						.filter(w -> w instanceof AbstractButton && w.getMessage().getString().equals("Add player"))
						.findFirst().orElseThrow(() -> new AssertionError("missing: Add player in player 1's menu")).active;
			});
			check(!active, "Add player is greyed out with four players");
		} finally {
			onRender(() -> {
				minecraft.gui.setScreen(null);
				return null;
			});
		}
	}

	/** Takes Player2 over with a newly plugged controller, after a dropped one's minute has passed. */
	private void takeOverWith(int pad, String guid, PadStyle style) throws Exception {
		long mark = hostLogLength();
		jumpJoinClock(61);
		waitFor("the take-over offered", Duration.ofSeconds(5),
				() -> hostLogSince(mark).contains("hearthside take-over offered player=2"));
		int resumed = count("hearthside satellite resumed");
		PADS.plug(pad, guid, style);
		Thread.sleep(300);
		if (pad == TAKEOVER_PAD) {
			windowShot("take-over"); // the card's take-over line, with the new controller's A
		}
		press(pad, PadButton.A);
		waitFor("the new controller took Player2 over", Duration.ofSeconds(5),
				() -> hostLogSince(mark).contains("hearthside take-over player=2 controller=" + guid));
		waitFor("Player2's game resumed", Duration.ofSeconds(10), () -> count("hearthside satellite resumed") > resumed);
	}

	/**
	 * N5 (Spec E section 3.3, Review Focus 1): unplugging a guest's controller dims their view behind a card and opens
	 * their pause menu; replugging undoes both, but leaves a menu they had opened themselves. After a minute (the join
	 * clock jumped ahead) a new controller's single A takes the player over where they stand, and becomes their
	 * profile's controller. Pad 102 takes Player2 back the same way, for the steps after.
	 */
	private void droppedController() throws Exception {
		int[] corner = guestCorner();
		check(probePixel(corner[0], corner[1]) == 0xFF00FF, "the guest's view is bright before the drop");

		// The drop, and the same controller coming back.
		int paused = count("hearthside satellite paused");
		int menus = count("hearthside pause menu opened");
		PADS.unplug(102);
		waitFor("Player2's game paused", Duration.ofSeconds(10), () -> count("hearthside satellite paused") > paused);
		waitFor("Player2's pause menu opened", Duration.ofSeconds(10), () -> count("hearthside pause menu opened") > menus);
		int veiled = probePixel(corner[0], corner[1]);
		check(dimmedMagenta(veiled), "the reconnect card dims the guest's view (corner " + Integer.toHexString(veiled) + ")");
		windowShot("reconnect-card");
		int world = count("hearthside controller: world mode");
		PADS.plug(102, PAD_GUID, PadStyle.PLAYSTATION);
		waitFor("(a) replugging closes the menu the drop opened", Duration.ofSeconds(10),
				() -> count("hearthside controller: world mode") > world);
		waitFor("(a) and the card", Duration.ofSeconds(5), () -> probePixel(corner[0], corner[1]) == 0xFF00FF);

		// (b) A menu Player2 opened themselves stays open through a drop and a reconnect.
		int own = count("hearthside pause menu opened");
		press(102, PadButton.START);
		waitFor("Player2's own pause menu", Duration.ofSeconds(10), () -> count("hearthside pause menu opened") > own);
		int resumed = count("hearthside satellite resumed");
		PADS.unplug(102);
		Thread.sleep(500);
		PADS.plug(102, PAD_GUID, PadStyle.PLAYSTATION);
		waitFor("Player2's game resumed", Duration.ofSeconds(10), () -> count("hearthside satellite resumed") > resumed);
		int stillOpen = count("hearthside controller: world mode");
		Thread.sleep(1500);
		check(count("hearthside controller: world mode") == stillOpen, "(b) their own menu is still open after the reconnect");
		press(102, PadButton.B);
		waitFor("B closes it", Duration.ofSeconds(10), () -> count("hearthside controller: world mode") > stillOpen);

		// (c) A minute later, another controller takes over.
		ServerPlayer guest = guest("Player2");
		Vec3 where = guest.position();
		int dropped = count("hearthside satellite paused");
		long takeoverMark = hostLogLength();
		PADS.unplug(102);
		waitFor("Player2's game paused", Duration.ofSeconds(10), () -> count("hearthside satellite paused") > dropped);
		try {
			int aPresses = count("hearthside input a down");
			takeOverWith(TAKEOVER_PAD, TAKEOVER_GUID, PadStyle.XBOX);
			waitFor("(c) Player2's game got the new controller's style", Duration.ofSeconds(5),
					() -> satelliteLog().contains("hearthside setpadstyle xbox"));
			Thread.sleep(500);
			check(count("hearthside input a down") == aPresses,
					"(c) the A that took Player2 over never pressed anything in their game");
			check(guest("Player2").position().distanceTo(where) < 0.5, "(c) Player2 is where they were");
			GuestProfile profile = profiles().load().stream().filter(p -> p.name().equals("Player2")).findFirst().orElseThrow();
			check(TAKEOVER_GUID.equals(profile.preferredControllerGuid()), "(c) and the new controller is now their profile's");
			Vec3 start = guest("Player2").position();
			PADS.set(TAKEOVER_PAD, new InputFrame(0, 0, 0, -1, 0, 0, 0, 0));
			Thread.sleep(2000);
			PADS.set(TAKEOVER_PAD, InputFrame.NEUTRAL);
			check(guest("Player2").position().distanceTo(start) > 1, "(c) the new controller moves Player2");
		} finally {
			if (hostLogSince(takeoverMark).contains("hearthside take-over player=2 controller=" + TAKEOVER_GUID)) {
				// Pad 102 takes Player2 back the same way.
				int away = count("hearthside satellite paused");
				PADS.unplug(TAKEOVER_PAD);
				waitFor("Player2's game paused", Duration.ofSeconds(10), () -> count("hearthside satellite paused") > away);
				takeOverWith(102, PAD_GUID, PadStyle.PLAYSTATION);
			} else {
				// No take-over: pad 102 simply reconnects.
				PADS.unplug(TAKEOVER_PAD);
				PADS.plug(102, PAD_GUID, PadStyle.PLAYSTATION);
			}
			PADS.set(102, InputFrame.NEUTRAL);
		}
	}

	/**
	 * N5b (review Minor 2): a newcomer's controller dropping while they pick a profile in their own view dims their
	 * picker under the same reconnect card, so a take-over of their seat is never unseen; replugging it brings it back.
	 */
	private void droppedWhilePicking() throws Exception {
		PADS.plug(PICKING_PAD, "fake-picking-pad", PadStyle.XBOX);
		Thread.sleep(300);
		try {
			press(PICKING_PAD, PadButton.A);
			press(PICKING_PAD, PadButton.A);
			waitFor("a newcomer choosing a profile", Duration.ofSeconds(10), () -> onRender(() -> card(2) != null));
			Thread.sleep(1500); // their view glides in and fades up
			Rect r = onRender(() -> Compositor.active().overlayViews().stream().filter(v -> v.seat() == 2).findFirst()
					.orElseThrow(() -> new AssertionError("missing: seat 2's view")).rect());
			int x = r.x() + 8, y = r.y() + 8;
			int bright = probePixel(x, y);
			check(luminance(bright) > 15, "the picker's corner shows the plank wall (" + Integer.toHexString(bright) + ")");
			PADS.unplug(PICKING_PAD);
			waitFor("the picker dimmed under the reconnect card", Duration.ofSeconds(5),
					() -> luminance(probePixel(x, y)) < 0.6 * luminance(bright));
			windowShot("reconnect-over-picker");
			PADS.plug(PICKING_PAD, "fake-picking-pad", PadStyle.XBOX);
			waitFor("the picker bright again", Duration.ofSeconds(5),
					() -> luminance(probePixel(x, y)) > 0.85 * luminance(bright));
		} finally {
			PADS.plug(PICKING_PAD, "fake-picking-pad", PadStyle.XBOX); // back, if a failed check left it out
			Thread.sleep(300);
			if (onRender(() -> card(2) != null)) {
				press(PICKING_PAD, PadButton.B);
			}
			waitFor("the newcomer gone", Duration.ofSeconds(10), () -> onRender(() -> card(2) == null));
			PADS.unplug(PICKING_PAD);
			Thread.sleep(1000); // the views settle back before the next step
		}
	}

	private static double luminance(int rgb) {
		return 0.2126 * (rgb >> 16 & 0xFF) + 0.7152 * (rgb >> 8 & 0xFF) + 0.0722 * (rgb & 0xFF);
	}

	/**
	 * N9 (Spec E section 3.7, M4): while a newcomer's view fades in under their card (player 1's view gliding out of its
	 * way), player 1's border keeps its brightness, sampled every frame where it meets the newcomer's view.
	 */
	private void joinFadeKeepsBorders() throws Exception {
		PADS.plug(PICKING_PAD, "fake-picking-pad", PadStyle.XBOX);
		Thread.sleep(300);
		long mark = hostLogLength();
		try {
			Compositor.e2eBorderSampling = true;
			try {
				press(PICKING_PAD, PadButton.A);
				press(PICKING_PAD, PadButton.A);
				waitFor("a newcomer choosing a profile", Duration.ofSeconds(10), () -> onRender(() -> card(2) != null));
				Thread.sleep(1500); // through the glide and the fade
			} finally {
				Compositor.e2eBorderSampling = false;
			}
			// Three views: player 1 top-left, the newcomer below them. Their shared edge is player 1's bottom border.
			Matcher m = Pattern.compile("hearthside border probe seat=0 edge=bottom rgb=([0-9a-f]{6})").matcher(hostLogSince(mark));
			List<Integer> samples = new ArrayList<>();
			while (m.find()) {
				samples.add(Integer.parseInt(m.group(1), 16));
			}
			int mine = onRender(() -> Party.current().colorOf(0)) & 0xFFFFFF;
			int theirs = onRender(() -> Party.current().colorOf(2)) & 0xFFFFFF;
			double floor = 0.6 * 0.85 * Math.min(luminance(mine), luminance(theirs));
			double darkest = samples.stream().mapToDouble(E2eDriver::luminance).min().orElse(-1);
			log("   " + samples.size() + " border samples, darkest " + String.format("%.0f", darkest) + ", floor "
					+ String.format("%.0f", floor));
			check(samples.size() >= 20, "player 1's border sampled through the fade (" + samples.size() + " frames)");
			check(darkest >= floor, "player 1's border kept its brightness through the newcomer's fade");
		} finally {
			if (onRender(() -> card(2) != null)) {
				press(PICKING_PAD, PadButton.B);
			}
			waitFor("the newcomer gone", Duration.ofSeconds(10), () -> onRender(() -> card(2) == null));
			PADS.unplug(PICKING_PAD);
			Thread.sleep(1000); // the views settle back before the next step
		}
	}

	/**
	 * N1b (Spec E section 3.1, task 2 review M7): in split-screen, player 1's controller drives their own pause menu by
	 * focus. START opens it on Resume; D-pad down and A open vanilla's Options, B comes back to the menu (on Resume
	 * again); on the Leave confirm B is Keep playing; B on the menu resumes.
	 */
	private void hostPadPauseMenu() throws Exception {
		waitFor("player 1 in play", Duration.ofSeconds(10), () -> onRender(() -> minecraft.gui.screen() == null));
		claimHostPad();
		try {
			press(HOST_PAD, PadButton.START);
			waitFor("player 1's pause menu", Duration.ofSeconds(5), () -> onRender(() -> onPauseMenu(minecraft.gui.screen())));
			check(onRender(this::focusedLabel).equals("Resume"), "it opens on Resume (got " + onRender(this::focusedLabel) + ")");
			// Player 1's menu has Add player, then Split-screen…, between Resume and Options… (Spec E sections 3.4, 3.5).
			press(HOST_PAD, PadButton.DPAD_DOWN);
			check(onRender(this::focusedLabel).equals("Add player"), "the D-pad moves to Add player (got " + onRender(this::focusedLabel) + ")");
			press(HOST_PAD, PadButton.DPAD_DOWN);
			check(onRender(this::focusedLabel).equals("Split-screen…"), "then Split-screen… (got " + onRender(this::focusedLabel) + ")");
			press(HOST_PAD, PadButton.DPAD_DOWN);
			check(onRender(this::focusedLabel).equals("Options…"), "and on to Options… (got " + onRender(this::focusedLabel) + ")");
			press(HOST_PAD, PadButton.A);
			waitFor("vanilla's Options", Duration.ofSeconds(5), () -> onRender(() -> minecraft.gui.screen() instanceof OptionsScreen));
			press(HOST_PAD, PadButton.B);
			waitFor("B from Options back to the menu", Duration.ofSeconds(5), () -> onRender(() -> onPauseMenu(minecraft.gui.screen())));
			check(onRender(this::focusedLabel).equals("Resume"), "back on Resume (got " + onRender(this::focusedLabel) + ")");

			for (int i = 0; i < 5; i++) {
				press(HOST_PAD, PadButton.DPAD_DOWN);
			}
			check(onRender(this::focusedLabel).equals("Leave game"), "the D-pad reaches Leave game (got " + onRender(this::focusedLabel) + ")");
			press(HOST_PAD, PadButton.A);
			waitFor("the Leave confirm", Duration.ofSeconds(5),
					() -> onRender(() -> buttonLabels(minecraft.gui.screen()).contains("Keep playing")));
			check(onRender(this::focusedLabel).equals("Keep playing"), "the confirm opens on Keep playing");
			press(HOST_PAD, PadButton.B);
			waitFor("B on the confirm is Keep playing", Duration.ofSeconds(5), () -> onRender(() -> onPauseMenu(minecraft.gui.screen())));

			press(HOST_PAD, PadButton.B);
			waitFor("B resumes", Duration.ofSeconds(5), () -> onRender(() -> minecraft.gui.screen() == null));
		} finally {
			releaseHostPad();
		}
	}

	/**
	 * N4 (Spec E section 3.1): player 1's Leave game asks first ("Keep playing" goes back); a guest's Leave game (D-pad
	 * to it, A) takes them out of the world and closes their game.
	 */
	private void leaveGame() throws Exception {
		onRender(() -> {
			minecraft.pauseGame(false);
			return null;
		});
		check(onRender(() -> onPauseMenu(minecraft.gui.screen())), "player 1's pause menu is Hearthside's");
		onRender(() -> {
			pressLabelled("Leave game");
			return null;
		});
		check(onRender(() -> buttonLabels(minecraft.gui.screen())).containsAll(List.of("Leave", "Keep playing")),
				"player 1's Leave game asks first");
		check(onRender(() -> minecraft.gui.screen().getTitle().getString()).contains("Leave the world? Everyone's game will close."),
				"and says everyone's game will close");
		onRender(() -> {
			pressLabelled("Keep playing");
			return null;
		});
		check(onRender(() -> onPauseMenu(minecraft.gui.screen())), "Keep playing goes back to the pause menu");
		onRender(() -> {
			minecraft.gui.setScreen(null);
			return null;
		});

		int menus = count("hearthside pause menu opened");
		press(102, PadButton.START);
		waitFor("the guest's pause menu", Duration.ofSeconds(10), () -> count("hearthside pause menu opened") > menus);
		press(102, PadButton.DPAD_DOWN);
		press(102, PadButton.DPAD_DOWN);
		press(102, PadButton.DPAD_DOWN);
		press(102, PadButton.A);
		waitFor("Player2 out of the world", Duration.ofSeconds(30), () -> playerCount() == 1);
		waitFor("Player2's game closed", Duration.ofSeconds(15), () -> satellites().isEmpty());
	}

	/**
	 * N8, the lobby half (Spec E section 3.6): player 1's PC keyboard types into a lobby card's name keyboard alongside
	 * the controller. Letters replace the suggestion, Backspace deletes, a character a name can't have changes nothing,
	 * Enter accepts the name. SDL text input is on while the card types (else no characters arrive), a space never presses
	 * a focused lobby button, a held Backspace keeps deleting and a held Esc backs out of the keyboard once. The in-world
	 * half is N8b.
	 */
	private void pcKeyboardTypesIntoTheLobbyCard() throws Exception {
		PADS.plug(LOBBY_PAD, LOBBY_GUID, PadStyle.PLAYSTATION);
		Thread.sleep(300);
		openLobby();
		try {
			press(LOBBY_PAD, PadButton.A);
			waitFor("a card for the seat", Duration.ofSeconds(10), () -> onRender(() -> card(1) != null));
			toNew(1, LOBBY_PAD);
			press(LOBBY_PAD, PadButton.A);
			waitFor("the name keyboard", Duration.ofSeconds(5), () -> onRender(() -> card(1) != null && card(1).keyboard() != null));
			// Characters arrive from the OS only while SDL text input is on (vanilla turns it on for a focused text box).
			waitFor("text input on while the card types", Duration.ofSeconds(2), this::textInputActive);
			pcType("Robin");
			// A space is "_" in the name, and nothing else: not a press of the lobby button that has vanilla's focus.
			onRender(() -> {
				minecraft.gui.screen().setFocused(widgetLabelled("+ Controller"));
				return null;
			});
			pcKey(InputConstants.KEY_SPACE);
			pcType(" ");
			String spaced = onRender(() -> card(1).keyboard().text());
			check(spaced.equals("Robin_"), "a space typed _ into the name (got " + spaced + ")");
			check(!onRender(() -> Party.current().hostClaimArmed() || Party.current().hostHasController()),
					"and didn't press + Controller, which had focus");
			// Held Backspace: a press and two repeats delete three characters, as in a text box.
			pcHoldKey(InputConstants.KEY_BACKSPACE, 2);
			String held = onRender(() -> card(1).keyboard().text());
			check(held.equals("Rob"), "a held Backspace (a press, two repeats) deleted three characters (got " + held + ")");
			pcType("i#");
			String typed = onRender(() -> card(1).keyboard().text());
			check(typed.equals("Robi"), "the PC keyboard typed i and refused # (got " + typed + ")");
			pcKey(InputConstants.KEY_RETURN);
			check(onRender(() -> card(1).setup().step()) == QuickSetup.Step.SKIN, "Enter accepts the typed name");
			waitFor("text input off once the keyboard closed", Duration.ofSeconds(2), () -> !textInputActive());
			// Held Esc: one press, two repeats and the release back out of the keyboard once, not the lobby too.
			press(LOBBY_PAD, PadButton.B);
			check(onRender(() -> card(1).keyboard() != null && card(1).setup().step() == QuickSetup.Step.NAME),
					"B on Skin goes back to the name keyboard");
			// The screen after each event (a second repeat would answer "End the party?" itself, hiding the first).
			List<String> screens = onRender(() -> {
				long window = minecraft.getWindow().handle();
				KeyEvent esc = new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0);
				List<String> seen = new ArrayList<>();
				for (int action : new int[] {InputConstants.PRESS, InputConstants.REPEAT, InputConstants.REPEAT,
						InputConstants.RELEASE}) {
					minecraft.keyboardHandler.keyPress(window, action, esc);
					seen.add(minecraft.gui.screen().getClass().getSimpleName());
				}
				return seen;
			});
			Thread.sleep(200);
			check(onRender(() -> card(1) != null && card(1).stage() == SeatCard.Stage.CAROUSEL),
					"a held Esc closes the keyboard");
			check(screens.stream().allMatch("LobbyScreen"::equals),
					"and only that: no \"End the party?\" (screens " + screens + ")");
		} finally {
			for (int i = 0; i < 5 && onRender(() -> Party.current() != null && Party.current().seatedCount() > 0); i++) {
				press(LOBBY_PAD, PadButton.B);
			}
			waitFor("the seat given up", Duration.ofSeconds(30),
					() -> satellites().isEmpty() && onRender(() -> Party.current() == null || Party.current().seatedCount() == 0));
			Files.deleteIfExists(satelliteLogFile());
			if (onRender(() -> minecraft.gui.screen() instanceof LobbyScreen)) {
				leaveLobby();
			}
			PADS.unplug(LOBBY_PAD);
		}
	}

	/**
	 * N8b, the in-world half (Spec E section 3.6, ruling R8): a newcomer's name keyboard in their own view takes player
	 * 1's PC keyboard only while player 1 plays on a controller with no screen open. On keyboard and mouse their keys stay
	 * theirs: W reaches their forward key and nothing is typed. On a controller W never reaches it; characters type,
	 * Backspace deletes, and Esc closes the keyboard as B does (no pause menu); function keys stay player 1's, and SDL text
	 * input is on only while the keyboard takes their keys (ruling P-12).
	 */
	private void pcKeyboardTypesIntoANewcomersKeyboard() throws Exception {
		onRender(() -> {
			if (minecraft.gui.screen() != null) {
				minecraft.gui.setScreen(null);
			}
			return null;
		});
		// Its own controller, so the step works whoever else is in the party (Player2 is still there in a focused run).
		int gamesBefore = satellites().size();
		int cardsBefore = onRender(() -> newCardSeats(0));
		PADS.plug(N8B_PAD, "fake-n8b-pad", PadStyle.PLAYSTATION);
		Thread.sleep(300);
		int seat = 0;
		try {
			press(N8B_PAD, PadButton.A);
			press(N8B_PAD, PadButton.A);
			waitFor("the newcomer's card", Duration.ofSeconds(10), () -> onRender(() -> newCardSeats(cardsBefore)) != 0);
			seat = Integer.numberOfTrailingZeros(onRender(() -> newCardSeats(cardsBefore)));
			int s = seat;
			toNew(s, N8B_PAD);
			press(N8B_PAD, PadButton.A);
			waitFor("the newcomer's name keyboard", Duration.ofSeconds(5),
					() -> onRender(() -> card(s) != null && card(s).keyboard() != null));
			String suggested = onRender(() -> card(s).keyboard().text());

			check(!onRender(() -> Party.current().hostHasController()), "player 1 is on keyboard and mouse");
			check(pcPressReaches(InputConstants.KEY_W, minecraft.options.keyUp),
					"on keyboard and mouse, W reaches player 1's forward key");
			pcType("w");
			String kept = onRender(() -> card(s).keyboard().text());
			check(kept.equals(suggested), "and nothing is typed into the newcomer's keyboard (got " + kept + ")");
			Thread.sleep(200);
			check(!textInputActive(), "and text input stays off");

			claimHostPad();
			check(onRender(() -> minecraft.gui.screen() == null), "player 1 is on a controller with no screen open");
			check(!pcPressReaches(InputConstants.KEY_W, minecraft.options.keyUp),
					"on a controller, W doesn't reach player 1's forward key");
			// Function keys never type: they stay player 1's (F5 changes the camera; put back after).
			boolean f5 = pcPressReaches(InputConstants.KEY_F5, minecraft.options.keyTogglePerspective);
			Thread.sleep(200);
			onRender(() -> {
				minecraft.options.setCameraType(CameraType.FIRST_PERSON);
				return null;
			});
			check(f5, "F5 still reaches player 1's camera key");
			waitFor("text input on while the newcomer's keyboard takes player 1's keys", Duration.ofSeconds(2),
					this::textInputActive);
			pcType("Robin");
			pcKey(InputConstants.KEY_BACKSPACE);
			String typed = onRender(() -> card(s).keyboard().text());
			check(typed.equals("Robi"), "the PC keyboard typed Robin into the newcomer's keyboard and deleted the n (got "
					+ typed + ")");
			pcKey(InputConstants.KEY_ESCAPE);
			check(onRender(() -> card(s) != null && card(s).stage() == SeatCard.Stage.CAROUSEL),
					"Esc closes the newcomer's keyboard, as B does");
			check(onRender(() -> minecraft.gui.screen() == null), "and doesn't pause player 1's game");
			waitFor("text input off once the newcomer's keyboard closed", Duration.ofSeconds(2), () -> !textInputActive());
		} finally {
			if (onRender(() -> Party.current().hostHasController())) {
				releaseHostPad();
			}
			// The newcomer backs out (B closes their keyboard, B again leaves), as before the step.
			int s = seat;
			for (int i = 0; i < 3 && s > 0 && onRender(() -> card(s) != null); i++) {
				press(N8B_PAD, PadButton.B);
			}
			PADS.unplug(N8B_PAD);
			waitFor("the newcomer gone", Duration.ofSeconds(30),
					() -> satellites().size() <= gamesBefore && onRender(() -> s == 0 || card(s) == null));
		}
	}

	private static final int N8B_PAD = 114;

	/** Seats with a card open (newcomers choosing) and not in {@code known}, as bits by seat. Render thread. */
	private int newCardSeats(int known) {
		int seats = 0;
		for (int seat = 1; seat < 4; seat++) {
			if (card(seat) != null && (known & 1 << seat) == 0) {
				seats |= 1 << seat;
			}
		}
		return seats;
	}

	/** Player 1's PC keyboard holding {@code key} down: its press, {@code repeats} of the OS's key repeats, its release. */
	private void pcHoldKey(int key, int repeats) throws Exception {
		onRender(() -> {
			long window = minecraft.getWindow().handle();
			minecraft.keyboardHandler.keyPress(window, InputConstants.PRESS, new KeyEvent(key, 0, 0));
			for (int i = 0; i < repeats; i++) {
				minecraft.keyboardHandler.keyPress(window, InputConstants.REPEAT, new KeyEvent(key, 0, 0));
			}
			minecraft.keyboardHandler.keyPress(window, InputConstants.RELEASE, new KeyEvent(key, 0, 0));
			return null;
		});
		Thread.sleep(100);
	}

	/** Whether SDL delivers typed characters to player 1's window now (text input on). */
	private boolean textInputActive() throws Exception {
		return onRender(() -> SDLKeyboard.SDL_TextInputActive(minecraft.getWindow().handle()));
	}

	/** Player 1's PC keyboard pressing {@code key}: whether {@code mapping} went down. Then the key is let go. */
	private boolean pcPressReaches(int key, KeyMapping mapping) throws Exception {
		return onRender(() -> {
			long window = minecraft.getWindow().handle();
			minecraft.keyboardHandler.keyPress(window, InputConstants.PRESS, new KeyEvent(key, 0, 0));
			boolean down = mapping.isDown();
			minecraft.keyboardHandler.keyPress(window, InputConstants.RELEASE, new KeyEvent(key, 0, 0));
			return down;
		});
	}

	/** Player 1's PC keyboard typing {@code text}, as the window's character events arrive. */
	private void pcType(String text) throws Exception {
		onRender(() -> {
			long window = minecraft.getWindow().handle();
			text.chars().forEach(c -> minecraft.keyboardHandler.charTyped(window, new CharacterEvent(c)));
			return null;
		});
		Thread.sleep(100);
	}

	/** Player 1's PC keyboard pressing and releasing {@code key}. */
	private void pcKey(int key) throws Exception {
		onRender(() -> {
			long window = minecraft.getWindow().handle();
			minecraft.keyboardHandler.keyPress(window, InputConstants.PRESS, new KeyEvent(key, 0, 0));
			minecraft.keyboardHandler.keyPress(window, InputConstants.RELEASE, new KeyEvent(key, 0, 0));
			return null;
		});
		Thread.sleep(100);
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

	private void dropTapAndHold() throws Exception {
		aimAtTarget("kill @e[type=item]", "item replace entity Player2 weapon.mainhand with minecraft:cobblestone 16");
		ServerPlayer guest = guest("Player2");
		Callable<Integer> inHand = () -> minecraft.getSingleplayerServer()
				.submit(() -> guest.getInventory().getSelectedItem().getCount()).join();
		check(inHand.call() == 16, "Player2 holds 16 cobblestone");
		hold(102, PadButton.DPAD_DOWN, 100);
		waitFor("one thrown", Duration.ofSeconds(5), () -> inHand.call() == 15 && cobblestoneOnGround(guest) == 1);
		hold(102, PadButton.DPAD_DOWN, 700);
		waitFor("the rest thrown", Duration.ofSeconds(5), () -> inHand.call() == 0);
		waitFor("16 cobblestone on the ground", Duration.ofSeconds(5), () -> cobblestoneOnGround(guest) == 16);

		// Drop stack on a button of its own: one press throws the lot.
		Party party = onRender(Party::current);
		GuestProfile profile = onRender(() -> party.profileOf(1));
		ControlScheme before = profile.controls();
		check(before.controlFor(Action.DROP_STACK).isEmpty(), "Drop stack starts unbound");
		int told = count("hearthside setcontrols");
		check(onRender(() -> party.saveControls(profile.id(), before.withBinding(Action.DROP_STACK, Control.RIGHT_STICK))),
				"Drop stack bound to the right stick");
		try {
			waitFor("the guest's game told", Duration.ofSeconds(10), () -> count("hearthside setcontrols") > told);
			server("kill @e[type=item]", "item replace entity Player2 weapon.mainhand with minecraft:cobblestone 16");
			Thread.sleep(300);
			check(inHand.call() == 16, "Player2 holds 16 again");
			press(102, PadButton.RIGHT_STICK);
			waitFor("the stack thrown at once", Duration.ofSeconds(5), () -> inHand.call() == 0);
			check(cobblestoneOnGround(guest) == 16, "all 16 on the ground");
		} finally {
			onRender(() -> party.saveControls(profile.id(), before));
			server("kill @e[type=item]", "gamemode creative Player2");
		}

		// The controls screen lists it, beside Drop, with no button until a player gives it one.
		ControlsScreen screen = onRender(() -> {
			GuestProfile fresh = new GuestProfile(UUID.randomUUID(), "E2E", SkinRef.defaultSkin("alex"), null,
					ControlScheme.BEDROCK);
			ControlsScreen controls = new ControlsScreen(null, party, 1, fresh);
			minecraft.gui.setScreen(controls);
			return controls;
		});
		try {
			var rows = onRender(screen::listedRows);
			List<Action> actions = rows.stream().map(Map.Entry::getKey).toList();
			check(actions.contains(Action.DROP_STACK), "the controls screen has a Drop stack row");
			check(actions.indexOf(Action.DROP_STACK) == actions.indexOf(Action.DROP) + 1, "right after Drop");
			check(onRender(() -> screen.model().scheme().controlFor(Action.DROP_STACK)).isEmpty(),
					"unbound (—) by default");
			int width = onRender(() -> screen.width), height = onRender(() -> screen.height);
			for (int i = 0; i < rows.size(); i++) {
				var a = rows.get(i).getValue();
				check(a.left() >= 0 && a.right() <= width && a.bottom() <= height, RemapModel.label(actions.get(i)) + " row is on screen");
				for (int j = i + 1; j < rows.size(); j++) {
					check(!a.overlaps(rows.get(j).getValue()), "rows " + actions.get(i) + " and " + actions.get(j) + " don't overlap");
				}
			}
			screenshot("controls-drop-stack");
		} finally {
			onRender(() -> {
				minecraft.gui.setScreen(null);
				return null;
			});
		}
	}

	/** Cobblestone lying near a player, kept on the ground (nobody picks it up) so it can be counted. */
	private int cobblestoneOnGround(ServerPlayer player) {
		return minecraft.getSingleplayerServer().submit(() -> {
			int total = 0;
			for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(12))) {
				if (item.getItem().is(Items.COBBLESTONE)) {
					item.setNeverPickUp();
					total += item.getItem().getCount();
				}
			}
			return total;
		}).join();
	}

	// ---- Player 1 on a controller, in the world ----

	/** Player 1 takes a controller the way their lobby card does: armed, then A on the free pad. */
	private void claimHostPad() throws Exception {
		PADS.plug(HOST_PAD, HOST_GUID, PadStyle.XBOX);
		Thread.sleep(300);
		onRender(() -> {
			Party.current().useController();
			return null;
		});
		press(HOST_PAD, PadButton.A);
		waitFor("player 1 has the controller", Duration.ofSeconds(5), () -> onRender(() -> Party.current().hostHasController()));
		Thread.sleep(500); // the claiming A's jump lands
	}

	/** Player 1 gives the controller back (and it is forgotten), as before the step. */
	private void releaseHostPad() throws Exception {
		onRender(() -> {
			if (minecraft.gui.screen() != null) {
				minecraft.gui.setScreen(null);
			}
			Party.current().releaseController();
			return null;
		});
		PADS.unplug(HOST_PAD);
		Thread.sleep(300);
	}

	private void hostPointerOnScreens() throws Exception {
		claimHostPad();
		try {
			check(!VirtualCursor.isDrawn(), "no pointer in play");
			String name = onRender(() -> minecraft.player.getName().getString());
			IntegratedServer server = minecraft.getSingleplayerServer();
			server.submit(() -> {
				ServerPlayer host = server.getPlayerList().getPlayerByName(name);
				host.kill(host.level());
			}).join();
			waitFor("the death screen", Duration.ofSeconds(10), () -> onRender(() -> minecraft.gui.screen() instanceof DeathScreen));
			Thread.sleep(300);
			check(VirtualCursor.isDrawn(), "player 1's pointer is drawn on the death screen");
			AbstractWidget respawn = onRender(() -> widgetLabelled("deathScreen.respawn"));
			waitFor("Respawn active", Duration.ofSeconds(5), () -> onRender(() -> respawn.active));
			steerPointerOnto(HOST_PAD, respawn);
			screenshot("death-pointer");
			press(HOST_PAD, PadButton.A);
			waitFor("respawned, in play", Duration.ofSeconds(5),
					() -> onRender(() -> minecraft.player != null && minecraft.player.isAlive() && minecraft.gui.screen() == null));
			Thread.sleep(200);
			check(!VirtualCursor.isDrawn(), "the pointer is hidden again in play");

			press(HOST_PAD, PadButton.START);
			waitFor("the pause menu", Duration.ofSeconds(5), () -> onRender(() -> minecraft.gui.screen() instanceof PauseScreen));
			Thread.sleep(200);
			check(VirtualCursor.isDrawn(), "the pointer is drawn on the pause menu");
			press(HOST_PAD, PadButton.B);
			waitFor("pause menu closed", Duration.ofSeconds(5), () -> onRender(() -> minecraft.gui.screen() == null));

			press(HOST_PAD, PadButton.Y);
			waitFor("the inventory", Duration.ofSeconds(5),
					() -> onRender(() -> minecraft.gui.screen() instanceof AbstractContainerScreen<?>));
			Thread.sleep(200);
			check(VirtualCursor.isDrawn(), "the pointer is drawn on the inventory");
			press(HOST_PAD, PadButton.B);
			waitFor("inventory closed", Duration.ofSeconds(5), () -> onRender(() -> minecraft.gui.screen() == null));
			Thread.sleep(200);
			check(!VirtualCursor.isDrawn(), "and hidden in play");
		} finally {
			releaseHostPad();
		}
	}

	private void pointerOrMouse() throws Exception {
		claimHostPad();
		try {
			openPause();
			check(VirtualCursor.isDrawn(), "the controller's pointer is drawn on the pause menu");
			// Park the pointer on empty space, so a click that went to it would do nothing.
			PADS.set(HOST_PAD, new InputFrame(0, 0, -1, 0, 0, 0, 0, 0));
			Thread.sleep(800);
			PADS.set(HOST_PAD, InputFrame.NEUTRAL);
			Thread.sleep(150);
			// The OS mouse moves over Back to Game (events as they arrive from the window), rests, then clicks.
			double[] resume = onRender(() -> windowCentre(widgetLabelled("menu.returnToGame")));
			osMove(resume[0] - 6, resume[1] - 2);
			osMove(resume[0], resume[1]);
			Thread.sleep(300);
			check(!VirtualCursor.isDrawn(), "the mouse took over: the controller's pointer is hidden");
			check(onRender(ButtonGuideRenderer::lastLayout).isEmpty(), "and no controller hints while the mouse drives");
			osButtonOnly(true);
			osButtonOnly(false);
			waitFor("the mouse's click closed the menu", Duration.ofSeconds(5), () -> onRender(() -> minecraft.gui.screen() == null));

			openPause();
			double[] mouse = onRender(() -> new double[] {minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos()});
			// A small nudge, held until the pointer shows (a fixed 60 ms could fall between two slow frames under Vulkan
			// validation and never reach the game).
			PADS.set(HOST_PAD, new InputFrame(0, 0, 0, 0.4f, 0, 0, 0, 0));
			try {
				long until = System.nanoTime() + Duration.ofSeconds(1).toNanos();
				Thread.sleep(60);
				while (!VirtualCursor.isDrawn() && System.nanoTime() < until) {
					Thread.sleep(10);
				}
			} finally {
				PADS.set(HOST_PAD, InputFrame.NEUTRAL);
			}
			Thread.sleep(200);
			check(VirtualCursor.isDrawn(), "the stick takes over again: the pointer is drawn");
			double[] pointer = {VirtualCursor.x(), VirtualCursor.y()};
			check(Math.abs(pointer[0] - mouse[0]) < 2 && pointer[1] >= mouse[1] && pointer[1] - mouse[1] < 60,
					"it starts where the mouse left it (mouse " + Arrays.toString(mouse) + ", pointer " + Arrays.toString(pointer) + ")");
			steerPointerOnto(HOST_PAD, onRender(() -> widgetLabelled("menu.returnToGame")));
			press(HOST_PAD, PadButton.A);
			waitFor("A closed the menu", Duration.ofSeconds(5), () -> onRender(() -> minecraft.gui.screen() == null));
		} finally {
			releaseHostPad();
		}
	}

	/**
	 * H5b: a real click with no move before it lands where the OS arrow is, not where the controller's pointer rests
	 * (batch review I2): the pointer parked on Options…, the arrow on empty space, a click opens nothing. The OS arrow is
	 * hidden while the controller drives, and back once the mouse does.
	 */
	private void clickWhereTheArrowIs() throws Exception {
		claimHostPad();
		try {
			openPause();
			double[] empty = onRender(this::emptySpotOnScreen);
			osMove(empty[0] + 8, empty[1]);
			osMove(empty[0], empty[1]);
			check(onRender(() -> minecraft.gui.screen() instanceof PauseScreen), "the mouse's moves clicked nothing");
			steerPointerOnto(HOST_PAD, onRender(() -> widgetLabelled("menu.options")));
			Thread.sleep(150);
			check(VirtualCursor.isDrawn(), "the controller drives: its pointer is drawn on Options…");
			boolean arrowShown = onRender(SDLMouse::SDL_CursorVisible);
			log("   OS arrow shown while the controller drives: " + arrowShown);
			// The real button, pressed and let go where the arrow rests (no move first).
			osButtonOnly(true);
			osButtonOnly(false);
			Thread.sleep(300);
			String open = onRender(() -> minecraft.gui.screen() == null ? "none" : minecraft.gui.screen().getClass().getSimpleName());
			check(open.equals("PauseScreen"), "the click landed on empty space where the arrow is: the pause menu is still open (" + open + ")");
			check(!arrowShown, "the OS arrow was hidden while the controller drove");
			check(!VirtualCursor.isDrawn(), "the mouse drives now: the controller's pointer is hidden");
			check(onRender(SDLMouse::SDL_CursorVisible), "and the OS arrow is shown");
			double[] resume = onRender(() -> windowCentre(widgetLabelled("menu.returnToGame")));
			osMove(resume[0] - 6, resume[1] - 2);
			osMove(resume[0], resume[1]);
			osButtonOnly(true);
			osButtonOnly(false);
			waitFor("the mouse's click on Back to Game closed the menu", Duration.ofSeconds(5),
					() -> onRender(() -> minecraft.gui.screen() == null));
		} finally {
			releaseHostPad();
		}
	}

	/**
	 * H6: player 1's controller unplugged with the pause menu open (batch review M2): within two frames the pointer and
	 * the controller hints are gone and the OS arrow is back, and the mouse drives the menu. Plugged back in, it is
	 * player 1's again.
	 */
	private void hostPadUnpluggedOnScreen() throws Exception {
		claimHostPad();
		try {
			openPause();
			PADS.set(HOST_PAD, new InputFrame(0, 0, -1, 0, 0, 0, 0, 0));
			Thread.sleep(400);
			PADS.set(HOST_PAD, InputFrame.NEUTRAL);
			Thread.sleep(300);
			check(VirtualCursor.isDrawn(), "the controller's pointer is drawn on the pause menu");
			check(!onRender(ButtonGuideRenderer::lastLayout).isEmpty(), "with its hints");
			java.util.concurrent.CompletableFuture<Object[]> afterTwoFrames = new java.util.concurrent.CompletableFuture<>();
			int[] frames = {0};
			Runnable[] counter = new Runnable[1];
			counter[0] = () -> {
				// At the start of the third frame since the unplug: two whole frames have run.
				if (++frames[0] == 3) {
					afterTwoFrames.complete(new Object[] {VirtualCursor.isDrawn(), ButtonGuideRenderer.lastLayout(),
							SDLMouse.SDL_CursorVisible(), minecraft.gui.screen() instanceof PauseScreen});
					dev.hearthside.fabric.client.input.FrameHook.unregister(counter[0]);
				}
			};
			onRender(() -> {
				PADS.unplug(HOST_PAD);
				dev.hearthside.fabric.client.input.FrameHook.register(counter[0]);
				return null;
			});
			Object[] state = afterTwoFrames.get(5, TimeUnit.SECONDS);
			log("   two frames after the unplug: pointer drawn=" + state[0] + " hints=" + state[1] + " OS arrow shown=" + state[2]);
			check((Boolean) state[3], "the pause menu is still open");
			check(!(Boolean) state[0], "the controller's pointer is hidden");
			check(((List<?>) state[1]).isEmpty(), "no controller hints");
			check((Boolean) state[2], "the OS arrow is shown");
			double[] resume = onRender(() -> windowCentre(widgetLabelled("menu.returnToGame")));
			osMove(resume[0] - 6, resume[1] - 2);
			osMove(resume[0], resume[1]);
			osButtonOnly(true);
			osButtonOnly(false);
			waitFor("the mouse's click on Back to Game closed the menu", Duration.ofSeconds(5),
					() -> onRender(() -> minecraft.gui.screen() == null));
			PADS.plug(HOST_PAD, HOST_GUID, PadStyle.XBOX);
			waitFor("player 1's controller back", Duration.ofSeconds(5), () -> onRender(() -> Party.current().hostHasController()));
		} finally {
			releaseHostPad();
		}
	}

	/** A point on the open screen with no widget under it, in window coordinates: the left edge, half way down. */
	private double[] emptySpotOnScreen() {
		Screen screen = minecraft.gui.screen();
		double gx = 6, gy = screen.height / 2.0;
		check(Screens.getWidgets(screen).stream().noneMatch(w -> w.isMouseOver(gx, gy)), "the spot at " + gx + "," + gy + " is empty");
		double toScreen = (double) minecraft.getWindow().getScreenWidth() / minecraft.getWindow().getGuiScaledWidth();
		return new double[] {gx * toScreen, gy * toScreen};
	}

	private void openPause() throws Exception {
		// Minecraft only pauses from play (a world that just opened may still show its loading screen).
		waitFor("in play", Duration.ofSeconds(10), () -> onRender(() -> minecraft.gui.screen() == null));
		String before = onRender(() -> {
			Screen open = minecraft.gui.screen();
			minecraft.pauseGame(false);
			return open == null ? "none" : open.getClass().getSimpleName();
		});
		waitFor("the pause menu (over " + before + ")", Duration.ofSeconds(5),
				() -> onRender(() -> minecraft.gui.screen() instanceof PauseScreen));
		Thread.sleep(300);
	}

	/** A widget's centre in window coordinates, where OS mouse events are. Render thread. */
	private double[] windowCentre(AbstractWidget widget) {
		var window = minecraft.getWindow();
		double toScreen = (double) window.getScreenWidth() / window.getGuiScaledWidth();
		return new double[] {(widget.getX() + widget.getWidth() / 2.0) * toScreen, (widget.getY() + widget.getHeight() / 2.0) * toScreen};
	}

	/** An OS mouse move, as the window delivers it. */
	private void osMove(double windowX, double windowY) throws Exception {
		onRender(() -> {
			minecraft.mouseHandler.onMove(minecraft.getWindow().handle(), windowX, windowY, 0, 0);
			return null;
		});
		Thread.sleep(50);
	}

	/** An OS left-button press or release where the mouse rests. */
	private void osButtonOnly(boolean press) throws Exception {
		onRender(() -> {
			minecraft.mouseHandler.onButton(minecraft.getWindow().handle(),
					new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), press ? 1 : 0);
			return null;
		});
		Thread.sleep(100);
	}

	/** The open screen's widget with this translated label. Render thread. */
	private AbstractWidget widgetLabelled(String key) {
		String label = Component.translatable(key).getString();
		return Screens.getWidgets(minecraft.gui.screen()).stream().filter(w -> w.getMessage().getString().equals(label))
				.findFirst().orElseThrow();
	}

	/** Steers the pointer onto a widget with a pad's left stick, slowing as it nears. */
	private void steerPointerOnto(int pad, AbstractWidget target) throws Exception {
		steerPointerOnto(pad, new Rect(target.getX(), target.getY(), target.getWidth(), target.getHeight()),
				target.getMessage().getString());
	}

	/** Steers {@code pad}'s pointer into {@code target} (GUI pixels), well inside it. */
	private void steerPointerOnto(int pad, Rect target, String name) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
		try {
			while (true) {
				double[] off = onRender(() -> {
					var window = minecraft.getWindow();
					double toGui = (double) window.getGuiScaledWidth() / window.getScreenWidth();
					return new double[] {target.x() + target.width() / 2.0 - VirtualCursor.x() * toGui,
							target.y() + target.height() / 2.0 - VirtualCursor.y() * toGui};
				});
				if (Math.abs(off[0]) < Math.max(1, target.width() / 2.0 - 3) && Math.abs(off[1]) < Math.max(1, target.height() / 2.0 - 3)) {
					break;
				}
				check(System.nanoTime() < deadline, "the pointer reached " + name);
				PADS.set(pad, new InputFrame(0, 0, stick(off[0]), stick(off[1]), 0, 0, 0, 0));
				Thread.sleep(30);
			}
		} finally {
			PADS.set(pad, InputFrame.NEUTRAL);
		}
		Thread.sleep(150);
	}

	/** Stick deflection toward a GUI-pixel offset: full far away, gentle (still past the dead zone) close in. */
	private static float stick(double offset) {
		if (Math.abs(offset) < 2) {
			return 0;
		}
		return (float) (Math.signum(offset) * Math.clamp(Math.abs(offset) / 40, 0.3, 1));
	}

	private void hintsStayClear() throws Exception {
		claimHostPad();
		try {
			PauseScreen pause = onRender(() -> {
				minecraft.pauseGame(false);
				return (PauseScreen) minecraft.gui.screen();
			});
			// Away from every button: nothing to select.
			PADS.set(HOST_PAD, new InputFrame(0, 0, -1, 0, 0, 0, 0, 0));
			Thread.sleep(800);
			PADS.set(HOST_PAD, InputFrame.NEUTRAL);
			List<String> labels = hintsClear("pause");
			check(labels.contains("Back"), "the pause menu offers Back");
			check(!labels.contains("Select"), "no Select with the pointer on empty space");
			AbstractWidget resume = onRender(() -> widgetLabelled("menu.returnToGame"));
			steerPointerOnto(HOST_PAD, resume);
			check(hintsClear("pause, on Back to Game").contains("Select"), "Select with the pointer on a button");
			screenshot("hints-pause");

			onRender(() -> {
				minecraft.gui.setScreen(new OptionsScreen(pause, minecraft.options));
				return null;
			});
			hintsClear("options");
			screenshot("hints-options");
			onRender(() -> {
				minecraft.gui.setScreen(new VideoSettingsScreen(pause, minecraft, minecraft.options));
				return null;
			});
			hintsClear("video");
			screenshot("hints-video");
			onRender(() -> {
				minecraft.gui.setScreen(new DeathScreen(Component.literal("E2E"), false, minecraft.player));
				return null;
			});
			check(!hintsClear("death").contains("Back"), "no Back on the death screen (B does nothing there)");
			AbstractWidget respawn = onRender(() -> widgetLabelled("deathScreen.respawn"));
			waitFor("Respawn active", Duration.ofSeconds(5), () -> onRender(() -> respawn.active));
			steerPointerOnto(HOST_PAD, respawn);
			check(hintsClear("death, on Respawn").contains("Select"), "Select on the death screen's Respawn");
			screenshot("hints-death");
			onRender(() -> {
				minecraft.gui.setScreen(null);
				return null;
			});
			Thread.sleep(300); // back in play first: a button already down as a screen closes is held from it
			press(HOST_PAD, PadButton.Y);
			waitFor("the inventory", Duration.ofSeconds(5),
					() -> onRender(() -> minecraft.gui.screen() instanceof AbstractContainerScreen<?>));
			check(!hintsClear("inventory").isEmpty(), "the inventory has its hints");
			screenshot("hints-inventory");
			onRender(() -> {
				minecraft.gui.setScreen(new ChatScreen("", false));
				return null;
			});
			check(hintsClear("chat").isEmpty(), "nothing drawn over chat");
		} finally {
			releaseHostPad();
		}
	}

	/**
	 * H7 (Spec D section 3.5, plan R8): player 1 on a controller in the survival inventory. Minecraft's toasts never
	 * cover the hints, which move clear and stay drawn; a keyboard tutorial toast already up goes when player 1 takes the
	 * controller, and none appears after; the screen draws one hint row; and a bundle offers Choose and Take out, with
	 * the right stick choosing.
	 */
	private void hintsAndToasts() throws Exception {
		// Run on its own, the step starts as soon as the session exists: player 1 may not have spawned yet.
		waitFor("player 1 in the world", Duration.ofSeconds(60), () -> onRender(() -> minecraft.player != null));
		String name = onRender(() -> minecraft.player.getName().getString());
		IntegratedServer server = minecraft.getSingleplayerServer();
		server.submit(() -> {
			var source = server.createCommandSourceStack().withSuppressedOutput();
			server.getCommands().performPrefixedCommand(source, "gamemode survival " + name);
			ItemStack bundle = new ItemStack(Items.BUNDLE);
			BundleContents.Mutable contents = new BundleContents.Mutable();
			contents.tryInsert(new ItemStack(Items.DIRT, 4));
			contents.tryInsert(new ItemStack(Items.STONE, 4));
			bundle.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
			server.getPlayerList().getPlayerByName(name).getInventory().setItem(0, bundle);
		}).join();
		// A keyboard tutorial toast already up while player 1 plays on the keyboard (user, 2026-10-12: "hold down the mouse
		// button to mine" stayed stuck once they took a controller): taking the controller clears it.
		onRender(() -> {
			minecraft.gui.toastManager().addToast(new TutorialToast(minecraft.font, TutorialToast.Icons.MOUSE,
					Component.literal("E2E tutorial before the controller"), null, false));
			return null;
		});
		waitFor("the keyboard tutorial toast showing", Duration.ofSeconds(3), () -> onRender(this::hasTutorialToast));
		claimHostPad();
		waitFor("taking the controller clears the keyboard tutorial toast", Duration.ofSeconds(3),
				() -> !onRender(this::hasTutorialToast));
		try {
			// The controller drops (the pause menu opens), a tutorial toast reaches the keyboard, and the same controller
			// comes back: it clears that toast as a claim does (T7b review).
			PADS.unplug(HOST_PAD);
			waitFor("player 1's controller gone", Duration.ofSeconds(5), () -> onRender(() -> !Party.current().hostHasController()));
			onRender(() -> {
				minecraft.gui.toastManager().addToast(new TutorialToast(minecraft.font, TutorialToast.Icons.MOUSE,
						Component.literal("E2E tutorial while the controller is gone"), null, false));
				return null;
			});
			waitFor("the keyboard tutorial toast showing without the controller", Duration.ofSeconds(3),
					() -> onRender(this::hasTutorialToast));
			PADS.plug(HOST_PAD, HOST_GUID, PadStyle.XBOX);
			waitFor("player 1's controller back", Duration.ofSeconds(5), () -> onRender(() -> Party.current().hostHasController()));
			waitFor("the reconnected controller clears the keyboard tutorial toast", Duration.ofSeconds(3),
					() -> !onRender(this::hasTutorialToast));
			onRender(() -> {
				minecraft.gui.setScreen(null);
				return null;
			});
			waitFor("back in play", Duration.ofSeconds(5), () -> onRender(() -> minecraft.gui.screen() == null));
			Thread.sleep(300);

			long mark = hostLogLength();
			press(HOST_PAD, PadButton.Y);
			waitFor("the survival inventory", Duration.ofSeconds(5),
					() -> onRender(() -> minecraft.gui.screen() instanceof InventoryScreen));
			Thread.sleep(300);

			// Two toasts in the top-right column, then three seconds of frames: the hints move clear and stay drawn.
			onRender(() -> {
				var toasts = minecraft.gui.toastManager();
				SystemToast.add(toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal("E2E toast"),
						Component.literal("A toast long enough to reach well into the screen"));
				SystemToast.add(toasts, SystemToast.SystemToastId.LOW_DISK_SPACE, Component.literal("E2E second toast"),
						Component.literal("Stacked under the first"));
				return null;
			});
			int sawToasts = 0;
			long until = System.nanoTime() + Duration.ofSeconds(3).toNanos();
			while (System.nanoTime() < until) {
				int shown = onRender(() -> {
					List<Rect> toasts = toastRects();
					List<ButtonGuideRenderer.Hint> hints = ButtonGuideRenderer.lastLayout();
					check(!hints.isEmpty(), "hints are still drawn while toasts show");
					for (ButtonGuideRenderer.Hint hint : hints) {
						for (Rect toast : toasts) {
							check(!hint.rect().intersects(toast), hint + " is covered by a toast at " + toast);
						}
					}
					return toasts.size();
				});
				sawToasts = Math.max(sawToasts, shown);
				Thread.sleep(50);
			}
			check(sawToasts > 0, "the toasts were on screen");
			screenshot("hints-toasts");

			// A keyboard tutorial toast never reaches a controller player.
			onRender(() -> {
				minecraft.gui.toastManager().addToast(new TutorialToast(minecraft.font, TutorialToast.Icons.MOVEMENT_KEYS,
						Component.literal("E2E tutorial"), null, false));
				return null;
			});
			Thread.sleep(1000);
			check(!onRender(this::hasTutorialToast), "no tutorial toast for player 1 on a controller");

			// Nor while their mouse drives the inventory (T7-4): one let through then would stay once the pad drives again.
			double[] empty = onRender(this::emptySpotOnScreen);
			osMove(empty[0] + 8, empty[1]);
			osMove(empty[0], empty[1]);
			check(onRender(() -> !Party.current().hostOnController()), "the mouse drives the inventory");
			onRender(() -> {
				minecraft.gui.toastManager().addToast(new TutorialToast(minecraft.font, TutorialToast.Icons.MOUSE,
						Component.literal("E2E tutorial while the mouse drives"), null, false));
				return null;
			});
			Thread.sleep(1000);
			check(!onRender(this::hasTutorialToast), "no tutorial toast for player 1 holding a controller while the mouse drives");
			PADS.set(HOST_PAD, new InputFrame(0, 0, -1, 0, 0, 0, 0, 0));
			Thread.sleep(400);
			PADS.set(HOST_PAD, InputFrame.NEUTRAL);
			Thread.sleep(300);
			check(onRender(() -> Party.current().hostOnController()) && VirtualCursor.isDrawn(), "the controller drives the inventory again");

			// The bundle: the pointer over it offers Choose and Take out, and the right stick chooses.
			Rect bundleSlot = onRender(() -> {
				InventoryScreen screen = (InventoryScreen) minecraft.gui.screen();
				ContainerScreenAccessor panel = (ContainerScreenAccessor) screen;
				Slot slot = screen.getMenu().slots.stream()
						.filter(s -> s.container == minecraft.player.getInventory() && s.getContainerSlot() == 0)
						.findFirst().orElseThrow();
				return new Rect(panel.hearthside$leftPos() + slot.x, panel.hearthside$topPos() + slot.y, 16, 16);
			});
			steerPointerOnto(HOST_PAD, bundleSlot, "the bundle");
			Thread.sleep(300);
			List<String> labels = onRender(() -> ButtonGuideRenderer.lastLayout().stream().map(ButtonGuideRenderer.Hint::label).toList());
			log("   hints over the bundle: " + labels);
			check(labels.contains("Choose") && labels.contains("Take out"), "Choose and Take out over a bundle (got " + labels + ")");
			screenshot("hints-bundle");
			int before = onRender(this::selectedBundleItem);
			PADS.set(HOST_PAD, new InputFrame(0, 0, 0, 0, 0, -1, 0, 0));
			Thread.sleep(400);
			PADS.set(HOST_PAD, InputFrame.NEUTRAL);
			Thread.sleep(200);
			int after = onRender(this::selectedBundleItem);
			check(after != before, "the right stick chose an item in the bundle (" + before + " to " + after + ")");

			// One hint row on the inventory, on every frame.
			String since = hostLogSince(mark);
			check(since.contains("hearthside hint rows=1 screen=inventoryscreen"), "the inventory's hint row was counted");
			check(!since.contains("hearthside hint rows=2"), "never two hint rows");
		} finally {
			releaseHostPad();
			server.submit(() -> {
				var source = server.createCommandSourceStack().withSuppressedOutput();
				server.getCommands().performPrefixedCommand(source, "item replace entity " + name + " container.0 with minecraft:air");
				server.getCommands().performPrefixedCommand(source, "gamemode creative " + name);
			}).join();
		}
	}

	/** Where each visible toast sits once fully slid in, in GUI pixels, placed the way the toast manager places it. */
	private List<Rect> toastRects() throws ReflectiveOperationException {
		int guiWidth = minecraft.getWindow().getGuiScaledWidth();
		List<Rect> rects = new ArrayList<>();
		for (Object instance : (List<?>) field(minecraft.gui.toastManager(), "visibleToasts")) {
			Toast toast = (Toast) field(instance, "toast");
			int slot = (int) field(instance, "firstSlotIndex");
			rects.add(new Rect((int) toast.xPos(guiWidth, 1f), (int) toast.yPos(slot), toast.width(), toast.height()));
		}
		return rects;
	}

	/** Whether a tutorial toast is on screen or waiting for room. */
	private boolean hasTutorialToast() throws ReflectiveOperationException {
		ToastManager manager = minecraft.gui.toastManager();
		for (Object instance : (List<?>) field(manager, "visibleToasts")) {
			if (field(instance, "toast") instanceof TutorialToast) {
				return true;
			}
		}
		for (Object toast : (Collection<?>) field(manager, "queued")) {
			if (toast instanceof TutorialToast) {
				return true;
			}
		}
		return false;
	}

	/** The selected item of the bundle in player 1's first hotbar slot, as their game sees it. */
	private int selectedBundleItem() {
		BundleContents contents = minecraft.player.getInventory().getItem(0).get(DataComponents.BUNDLE_CONTENTS);
		return contents == null ? Integer.MIN_VALUE : contents.getSelectedItemIndex();
	}

	/** A private field of one of Minecraft's objects, by its (unobfuscated) name. */
	private static Object field(Object owner, String name) throws ReflectiveOperationException {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

	/**
	 * After two frames on the open screen: no drawn hint covers a widget it shows (inside lists too) or a container's
	 * panel. Logs and returns the hints' labels.
	 */
	private List<String> hintsClear(String name) throws Exception {
		Thread.sleep(300);
		return onRender(() -> {
			Screen screen = minecraft.gui.screen();
			List<ButtonGuideRenderer.Hint> hints = ButtonGuideRenderer.lastLayout();
			List<Rect> shown = new ArrayList<>();
			shownWidgets(screen.children(), new Rect(0, 0, screen.width, screen.height), shown);
			shown.add(new Rect(screen.width / 2 - 91, screen.height - 22, 182, 22)); // the hotbar, under in-world screens
			if (screen instanceof AbstractContainerScreen<?> container) {
				ContainerScreenAccessor panel = (ContainerScreenAccessor) container;
				shown.add(new Rect(panel.hearthside$leftPos(), panel.hearthside$topPos(), panel.hearthside$imageWidth(),
						panel.hearthside$imageHeight()));
			}
			for (ButtonGuideRenderer.Hint hint : hints) {
				check(new Rect(0, 0, screen.width, screen.height).contains(hint.rect()), name + ": " + hint + " is on screen");
				for (Rect widget : shown) {
					check(!hint.rect().intersects(widget), name + ": " + hint + " covers " + widget);
				}
			}
			List<String> labels = hints.stream().map(ButtonGuideRenderer.Hint::label).toList();
			log("   hints " + name + ": " + labels);
			return labels;
		});
	}

	/** The widgets a screen shows, inside lists and their entries too, clipped to what each list shows. */
	private static void shownWidgets(List<? extends GuiEventListener> children, Rect clip, List<Rect> into) {
		for (GuiEventListener child : children) {
			if (!(child instanceof AbstractWidget widget)) {
				if (child instanceof ContainerEventHandler entry) {
					shownWidgets(entry.children(), clip, into);
				}
				continue;
			}
			if (!widget.visible) {
				continue;
			}
			int x0 = Math.max(widget.getX(), clip.x()), y0 = Math.max(widget.getY(), clip.y());
			int x1 = Math.min(widget.getRight(), clip.x() + clip.width());
			int y1 = Math.min(widget.getBottom(), clip.y() + clip.height());
			if (x1 <= x0 || y1 <= y0) {
				continue;
			}
			Rect rect = new Rect(x0, y0, x1 - x0, y1 - y0);
			into.add(rect);
			if (widget instanceof ContainerEventHandler list) {
				shownWidgets(list.children(), rect, into);
			}
		}
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
		// N4 may already have taken Player2 out through their own Leave game.
		if (guest("Player2") != null) {
			onRender(() -> {
				Party.current().leave("Player2");
				return null;
			});
		}
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
