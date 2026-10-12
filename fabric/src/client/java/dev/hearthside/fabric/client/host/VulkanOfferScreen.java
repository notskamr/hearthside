package dev.hearthside.fabric.client.host;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.hearthside.core.config.VulkanOffer;
import dev.hearthside.core.launch.HostJvm;
import dev.hearthside.core.launch.LaunchCommand;
import dev.hearthside.core.launch.RelaunchCommandBuilder;
import dev.hearthside.core.launch.UnsupportedLauncherException;
import dev.hearthside.core.launch.VulkanOfferRule;
import dev.hearthside.core.process.JdkProcessLauncher;
import dev.hearthside.core.process.RunningProcess;
import dev.hearthside.core.profile.ProfileStore;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.compositor.backend.SharedImages;
import dev.hearthside.fabric.client.compositor.backend.VulkanProbe;
import dev.hearthside.fabric.client.menu.TitleInput;
import dev.hearthside.fabric.client.ui.EmberLayer;
import dev.hearthside.fabric.client.ui.HearthButton;
import dev.hearthside.fabric.client.ui.HearthDraw;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The offer to switch to Vulkan (Spec D section 3.1), a card before the lobby the first time Co-op opens on a PC whose
 * OpenGL can't share frames between games but whose Vulkan can. Switch and restart sets Minecraft's Graphics API option
 * to Vulkan and starts this game again; Not now goes on to the lobby. Either answer is remembered, so it is asked once;
 * Back leaves it unanswered. Controllers drive it through {@code TitleInput}, as on the title screen.
 */
public final class VulkanOfferScreen extends Screen {
	static final Component TITLE = Component.literal("Split-screen runs smoother on Vulkan on this PC");
	static final Component BODY = Component.literal("Your graphics card can share each player's picture directly on"
			+ " Vulkan, instead of copying it. Minecraft restarts once to switch.");
	/** How long the new game must stay up before this one closes; one that dies sooner asks the player instead. */
	private static final Duration SURVIVES = Duration.ofSeconds(3);
	private static final int PANEL_W = 300, PAD = 12, LINE = 10, BUTTON_H = 20, GAP = 8;
	/** "Not offered" reasons already logged in this game: each once. */
	private static final Set<String> explained = new HashSet<>();

	private final TitleScreen parent;
	/** Opens the lobby, given the controller that answered (null for the mouse or keyboard). */
	private final Consumer<Integer> openLobby;
	private final EmberLayer embers = new EmberLayer(23, 40);
	private final boolean reduceMotion;
	private HearthButton switchButton, notNowButton;
	/** The relaunched game while this one waits to see it stay up, and when it started. */
	private RunningProcess relaunched;
	private long relaunchedAt;
	private boolean restarting;

	private VulkanOfferScreen(TitleScreen parent, Consumer<Integer> openLobby, boolean reduceMotion) {
		super(TITLE);
		this.parent = parent;
		this.openLobby = openLobby;
		this.reduceMotion = reduceMotion;
	}

	/**
	 * Shows the offer instead of opening the lobby when this PC should get it (it then opens the lobby itself, on Not
	 * now). Logs why not when a player might wonder. Never fails: anything unexpected means no offer.
	 *
	 * @return whether the offer is showing
	 */
	static boolean offerBefore(Minecraft minecraft, TitleScreen title, Consumer<Integer> openLobby) {
		VulkanOfferRule.Decision decision;
		try {
			decision = decide(minecraft);
		} catch (RuntimeException e) {
			HearthsideMod.LOGGER.warn("Hearthside couldn't tell whether to offer Vulkan", e);
			return false;
		}
		decision.notOfferedBecause().ifPresent(reason -> {
			if (explained.add(reason)) {
				HearthsideMod.LOGGER.info("Hearthside Vulkan offer: not offered ({})", reason);
			}
		});
		if (!decision.offer()) {
			return false;
		}
		HearthsideMod.LOGGER.info("Hearthside Vulkan offer: shown");
		minecraft.gui.setScreen(new VulkanOfferScreen(title, openLobby, reduceMotion(minecraft)));
		return true;
	}

	private static VulkanOfferRule.Decision decide(Minecraft minecraft) {
		boolean onOpenGl = "OpenGL".equals(RenderSystem.getDevice().getDeviceInfo().backendName());
		// E2E: OpenGL that can't import, as on Intel.
		boolean glCanImport = onOpenGl && !Boolean.getBoolean("hearthside.e2e.fakeGlCantImport")
				&& SharedImages.forThisGame().facts().canImport();
		String fakeIris = System.getProperty("hearthside.e2e.fakeIris");
		boolean iris = fakeIris != null ? Boolean.parseBoolean(fakeIris) : FabricLoader.getInstance().isModLoaded("iris");
		VulkanOffer stored = SplitScreenConfigFile.load(gameDir(minecraft)).vulkanOffer();
		// The probe makes a Vulkan instance: only worth it when its answer decides.
		boolean probe = onOpenGl && !glCanImport && !iris && stored == VulkanOffer.PENDING;
		return VulkanOfferRule.decide(onOpenGl, glCanImport, probe && VulkanProbe.canShareFrames(), iris, stored);
	}

	private static boolean reduceMotion(Minecraft minecraft) {
		try {
			return new ProfileStore(gameDir(minecraft).resolve("hearthside").resolve("profiles.json")).loadFile().host()
					.settings().reduceMotion();
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	private static Path gameDir(Minecraft minecraft) {
		return minecraft.gameDirectory.toPath().toAbsolutePath();
	}

	@Override
	protected void init() {
		Layout layout = layout(font, width, height);
		Component switchLabel = Component.literal("Switch and restart"), notNowLabel = Component.literal("Not now");
		int y = layout.buttonsY();
		if (!layout.stacked()) {
			int switchW = switchWidth(font), notNowW = notNowWidth(font);
			int x = (width - (switchW + GAP + notNowW)) / 2;
			switchButton = new HearthButton(x, y, switchW, BUTTON_H, switchLabel, b -> switchAndRestart());
			notNowButton = new HearthButton(x + switchW + GAP, y, notNowW, BUTTON_H, notNowLabel, b -> notNow());
		} else {
			// A narrow window: one above the other, as wide as the card.
			int x = layout.panelX(), w = layout.panelW();
			switchButton = new HearthButton(x, y, w, BUTTON_H, switchLabel, b -> switchAndRestart());
			notNowButton = new HearthButton(x, y + BUTTON_H + 4, w, BUTTON_H, notNowLabel, b -> notNow());
		}
		addRenderableWidget(switchButton);
		addRenderableWidget(notNowButton);
		switchButton.active = notNowButton.active = !restarting;
	}

	private static int switchWidth(Font font) {
		return Math.max(120, font.width("Switch and restart") + 24);
	}

	private static int notNowWidth(Font font) {
		return Math.max(90, font.width("Not now") + 24);
	}

	/** Switch and restart first: it is what the card is for, and Not now is one press away. */
	@Override
	protected void setInitialFocus() {
		setInitialFocus(switchButton);
	}

	/** Not now: remembered, then on to the lobby as if the card had never been. */
	private void notNow() {
		remember(VulkanOffer.DECLINED);
		openLobby.accept(TitleInput.pressingPad());
	}

	/**
	 * Saves Vulkan as Minecraft's Graphics API and starts this game again the way it was started; once the new game has
	 * stayed up a moment, this one closes. If it can't be started, or dies at once, the player is asked to start
	 * Minecraft again themselves (the option is already saved).
	 */
	private void switchAndRestart() {
		restarting = true;
		switchButton.active = notNowButton.active = false;
		minecraft.options.preferredGraphicsBackend().set(PreferredGraphicsApi.VULKAN);
		minecraft.options.save();
		remember(VulkanOffer.ACCEPTED);
		try {
			LaunchCommand command = RelaunchCommandBuilder.build(HostJvm.capture(HostLaunchFacts.read()),
					HostLaunchFacts.workingDirectory());
			HearthsideMod.LOGGER.info("Hearthside relaunch command: {}", RelaunchCommandBuilder.describe(command));
			String dry = System.getProperty("hearthside.e2e.dryRelaunch");
			if ("true".equals(dry)) {
				return; // E2E: the command is what's checked; nothing starts and this game stays.
			}
			if ("dead".equals(dry)) {
				// E2E: a game that exits at once.
				command = new LaunchCommand(List.of("cmd", "/c", "exit", "1"), Map.of(), Set.of(), command.workingDirectory());
			}
			// Its output goes to a file of its own: nothing ties it to this game, which is about to close.
			relaunched = new JdkProcessLauncher().start(command,
					gameDir(minecraft).resolve("logs").resolve("hearthside-relaunch.log"));
			relaunchedAt = System.nanoTime();
		} catch (UnsupportedLauncherException | IOException | RuntimeException e) {
			HearthsideMod.LOGGER.warn("Hearthside couldn't start Minecraft again on Vulkan", e);
			pleaseRestart();
		}
	}

	@Override
	public void tick() {
		if (relaunched == null || System.nanoTime() - relaunchedAt < SURVIVES.toNanos()) {
			return;
		}
		RunningProcess game = relaunched;
		relaunched = null;
		if (game.isAlive()) {
			HearthsideMod.LOGGER.info("Hearthside started Minecraft again on Vulkan (pid {}); closing this game", game.pid());
			minecraft.stop();
		} else {
			HearthsideMod.LOGGER.warn("Hearthside's restarted Minecraft exited at once; see logs/hearthside-relaunch.log");
			pleaseRestart();
		}
	}

	private void pleaseRestart() {
		minecraft.gui.setScreen(new PleaseRestartScreen(parent, reduceMotion));
	}

	private void remember(VulkanOffer answer) {
		Path gameDir = gameDir(minecraft);
		try {
			SplitScreenConfigFile.save(gameDir, SplitScreenConfigFile.load(gameDir).withVulkanOffer(answer));
		} catch (IOException e) {
			HearthsideMod.LOGGER.warn("Hearthside couldn't remember the answer to the Vulkan offer", e);
		}
	}

	/** Not while the new game is starting: this one is about to close. */
	@Override
	public boolean shouldCloseOnEsc() {
		return !restarting;
	}

	/** Back (B or Esc): to the title screen, unanswered. */
	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		drawHearth(g, width, height, embers, reduceMotion);
		Layout layout = layout(font, width, height);
		HearthDraw.parchment(g, layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH());
		int text = layout.panelW() - 2 * PAD;
		int y = drawLines(g, font, TITLE, text, width / 2, layout.panelY() + PAD, HearthDraw.INK);
		drawLines(g, font, BODY, text, width / 2, y + 6, HearthDraw.MUTED);
		if (restarting) {
			HearthDraw.labelCentred(g, font, Component.literal("Restarting…"), width / 2,
					notNowButton.getY() + BUTTON_H + 8, HearthDraw.CREAM);
		}
	}

	/** Where the card goes: a parchment panel holding the title and body, centred with the buttons below it. */
	private record Layout(int panelX, int panelY, int panelW, int panelH, int buttonsY, boolean stacked) {}

	private static Layout layout(Font font, int width, int height) {
		int panelW = Math.min(PANEL_W, width - 32);
		int text = panelW - 2 * PAD;
		int lines = font.split(TITLE, text).size() + font.split(BODY, text).size();
		int panelH = PAD + lines * LINE + 6 + PAD;
		boolean stacked = switchWidth(font) + GAP + notNowWidth(font) > panelW;
		int buttons = stacked ? 2 * BUTTON_H + 4 : BUTTON_H;
		int top = Math.max(8, (height - (panelH + 12 + buttons)) / 2);
		return new Layout((width - panelW) / 2, top, panelW, panelH, top + panelH + 12, stacked);
	}

	// ---- Shared with the restart card ----

	/** The lobby's wall: planks lit by a flickering fire from below, with embers drifting up. */
	static void drawHearth(GuiGraphicsExtractor g, int width, int height, EmberLayer embers, boolean reduceMotion) {
		HearthDraw.board(g, 0, 0, width, height);
		double t = System.nanoTime() / 1e9;
		float flicker = reduceMotion ? 0.9f : (float) (0.85 + 0.08 * Math.sin(t * 7.3) + 0.05 * Math.sin(t * 13.1 + 1.7));
		HearthDraw.firelightGlow(g, width / 10, height * 35 / 100, width * 8 / 10, height - height * 35 / 100, flicker);
		embers.draw(g, 0, 0, width, height, reduceMotion);
	}

	/** Ink on parchment, wrapped to {@code width} and centred on {@code centreX}; returns the y below it. */
	private static int drawLines(GuiGraphicsExtractor g, Font font, Component text, int width, int centreX, int y,
			int argb) {
		for (FormattedCharSequence line : font.split(text, width)) {
			g.text(font, line, centreX - font.width(line) / 2, y, argb, false);
			y += LINE;
		}
		return y;
	}

	/** When the restart couldn't happen: the player starts Minecraft again (Vulkan is already saved). OK goes back. */
	static final class PleaseRestartScreen extends Screen {
		static final Component MESSAGE = Component.literal("Please start Minecraft again to switch to Vulkan.");

		private final TitleScreen parent;
		private final EmberLayer embers = new EmberLayer(29, 40);
		private final boolean reduceMotion;
		private HearthButton ok;

		PleaseRestartScreen(TitleScreen parent, boolean reduceMotion) {
			super(MESSAGE);
			this.parent = parent;
			this.reduceMotion = reduceMotion;
		}

		@Override
		protected void init() {
			int panelW = Math.min(PANEL_W, width - 32);
			int panelH = panelH(panelW);
			int top = Math.max(8, (height - (panelH + 12 + BUTTON_H)) / 2);
			ok = addRenderableWidget(new HearthButton((width - 90) / 2, top + panelH + 12, 90, BUTTON_H,
					Component.literal("OK"), b -> onClose()));
		}

		private int panelH(int panelW) {
			return PAD + font.split(MESSAGE, panelW - 2 * PAD).size() * LINE + PAD;
		}

		@Override
		protected void setInitialFocus() {
			setInitialFocus(ok);
		}

		@Override
		public void onClose() {
			minecraft.gui.setScreen(parent);
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			drawHearth(g, width, height, embers, reduceMotion);
			int panelW = Math.min(PANEL_W, width - 32);
			int panelH = panelH(panelW);
			int top = Math.max(8, (height - (panelH + 12 + BUTTON_H)) / 2);
			HearthDraw.parchment(g, (width - panelW) / 2, top, panelW, panelH);
			drawLines(g, font, MESSAGE, panelW - 2 * PAD, width / 2, top + PAD, HearthDraw.INK);
		}
	}
}
