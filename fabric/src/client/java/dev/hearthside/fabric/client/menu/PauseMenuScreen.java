package dev.hearthside.fabric.client.menu;

import dev.hearthside.core.Role;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.MenuNav;
import dev.hearthside.core.input.PadButton;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.HearthsideClient;
import dev.hearthside.fabric.client.host.Party;
import dev.hearthside.fabric.client.host.SplitScreenScreen;
import dev.hearthside.fabric.client.ui.GlassButton;
import dev.hearthside.fabric.client.ui.HearthDraw;
import dev.hearthside.fabric.client.ui.HintChip;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/**
 * Each player's own pause menu during split-screen (Spec E section 3.1), in their own game: Ember glass over their view
 * with "Paused", a status line and a column of glass buttons. Vanilla's pause screen is never replaced: Game menu…
 * opens it as vanilla would, every mod's buttons included, and closing it back to play comes back here instead.
 * Playing alone, the pause key stays vanilla's ({@code PauseGameMixin}).
 *
 * <p>Controllers drive it by focus (D-pad, A, B), the mouse by pointing; it focuses Resume whenever it opens.
 */
public final class PauseMenuScreen extends Screen implements PadScreen {
	private static final Component TITLE = Component.literal("Paused");
	private static final Component RESUME = Component.literal("Resume"), OPTIONS = Component.literal("Options…"),
			GAME_MENU = Component.literal("Game menu…"), LEAVE = Component.literal("Leave game"),
			SPLIT_SCREEN = Component.literal("Split-screen…");
	private static final int BUTTON_W = 150, BUTTON_H = 20, GAP = 4, PAD = 10, MARGIN = 6, HINT_GAP = 4;
	/** The hotbar and experience bar at the bottom of the view, kept clear. */
	private static final int HUD = 30;
	/** A guest's focus outline: the ember accent (their game doesn't know their profile's colour). */
	private static final int EMBER = 0xFFB35A;
	/** What every pause menu in this game says under "Paused" (Task 3's pause states); empty until then. */
	private static volatile String status = "";
	/** Vanilla's pause screen opened by Game menu…, and the menu that closing it to play goes back to. Render thread. */
	private static WeakReference<PauseScreen> gameMenu = new WeakReference<>(null);
	private static WeakReference<PauseMenuScreen> gameMenuFrom = new WeakReference<>(null);

	/** One entry in the column. */
	private record Entry(Component label, Runnable action, boolean active) {}

	private final List<Entry> entries = new ArrayList<>();
	private final List<GlassButton> buttons = new ArrayList<>();
	private final boolean guest;
	/** Fresh each time the menu (re)opens, so the press that opened it or came back to it presses nothing here. */
	private MenuNav nav = new MenuNav();
	private int lastButtons = -1;
	private int panelX, panelY, panelW, panelH, statusY;
	private boolean bigHeading;
	private PadHints hints;
	/** A controller's A is pressing an entry right now (not the mouse or the PC keyboard). */
	private boolean padPressing;

	public PauseMenuScreen() {
		super(TITLE);
		guest = HearthsideClient.role() == Role.SATELLITE;
		entries.add(new Entry(RESUME, this::resume, true));
		entries.add(new Entry(OPTIONS, () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options)), true));
		entries.add(new Entry(GAME_MENU, this::openGameMenu, true));
		entries.add(new Entry(LEAVE, this::leave, true));
		if (!guest) {
			// Player 1's split-screen settings, applied live (Spec E section 3.5); guests' menus don't have them.
			addEntry(1, SPLIT_SCREEN, () -> minecraft.gui.setScreen(new SplitScreenScreen(this)), true);
		}
	}

	/**
	 * Whether the pause key opens this menu instead of vanilla's: in split-screen. A guest's game always is while in a
	 * world; player 1's while at least one guest is seated.
	 */
	public static boolean replacesVanilla(Minecraft minecraft) {
		if (minecraft.level == null) {
			return false;
		}
		return HearthsideClient.role() == Role.SATELLITE || Party.splitScreen();
	}

	/** Whether this game shows Hearthside's pause menu right now. Render thread. */
	public static boolean isOpen(Minecraft minecraft) {
		return minecraft.gui.screen() instanceof PauseMenuScreen;
	}

	/** The status line under "Paused" for every pause menu in this game, the open one and later ones ("" for none). */
	public static void setStatus(String line) {
		status = line == null ? "" : line;
	}

	/**
	 * Adds an entry at {@code index} in the column (Tasks 5 and 6: "Add player" and "Split-screen…" after Resume).
	 * Render thread.
	 *
	 * <p>Add entries once per instance, in the constructor by role. The same instance is shown again on the way back from
	 * Options…, Game menu… and the Leave confirm, and {@code init} (and Fabric's {@code ScreenEvents.AFTER_INIT}) runs
	 * each time, so adding from an "on show" hook would add the entry again.
	 */
	public void addEntry(int index, Component label, Runnable action, boolean active) {
		entries.add(Math.clamp(index, 0, entries.size()), new Entry(label, action, active));
		// Shown already: lay the column out again (before then, init does).
		if (minecraft != null && minecraft.gui != null && minecraft.gui.screen() == this) {
			rebuildWidgets();
		}
	}

	/**
	 * Called as {@code Gui.setScreen} is about to close vanilla's pause screen back to play: the menu to show instead,
	 * when that pause screen is the one Game menu… opened. Render thread.
	 */
	public static Screen instead(Minecraft minecraft, Screen closing) {
		PauseMenuScreen from = gameMenuFrom.get();
		if (closing == null || closing != gameMenu.get() || from == null || minecraft.level == null || minecraft.player == null
				|| minecraft.player.isDeadOrDying()) {
			return null;
		}
		gameMenu = new WeakReference<>(null);
		gameMenuFrom = new WeakReference<>(null);
		return from;
	}

	@Override
	public void added() {
		super.added();
		HearthsideMod.LOGGER.info("Hearthside pause menu opened entries={}",
				entries.stream().map(e -> e.label().getString()).collect(Collectors.joining(",")));
	}

	@Override
	protected void init() {
		nav = new MenuNav();
		lastButtons = -1;
		buttons.clear();
		// The panel with its hints under it, centred above the hotbar (which stays visible through the dim).
		int room = height - HUD - 2 * MARGIN;
		bigHeading = blockHeight(true, GAP) <= room;
		int gap = bigHeading || blockHeight(false, GAP) <= room ? GAP : 2;
		int heading = bigHeading ? 18 : 9;
		int column = entries.size() * BUTTON_H + (entries.size() - 1) * gap;
		panelW = Math.min(BUTTON_W + 2 * PAD + 40, width - 2 * MARGIN);
		panelH = PAD + heading + 4 + 9 + 8 + column + PAD;
		panelX = (width - panelW) / 2;
		panelY = Math.max(MARGIN, MARGIN + (room - blockHeight(bigHeading, gap)) / 2);
		statusY = panelY + PAD + heading + 4;
		int buttonW = Math.min(BUTTON_W, panelW - 2 * PAD);
		int y = statusY + 9 + 8;
		for (Entry entry : entries) {
			GlassButton button = new GlassButton((width - buttonW) / 2, y, buttonW, BUTTON_H, entry.label(), b -> choose(entry));
			button.active = entry.active();
			buttons.add(addRenderableWidget(button));
			y += BUTTON_H + gap;
		}
		// Its own hints: Minecraft's screens get ButtonGuideRenderer's, Hearthside's draw their own.
		hints = new PadHints(this, RESUME, this::resume);
		hints.addTo(this::addRenderableWidget, width / 2, panelY + panelH + HINT_GAP);
	}

	/** The panel and its hints, with the heading at twice the font's size or its own, and this gap between buttons. */
	private int blockHeight(boolean big, int gap) {
		int column = entries.size() * BUTTON_H + (entries.size() - 1) * gap;
		return PAD + (big ? 18 : 9) + 4 + 9 + 8 + column + PAD + HINT_GAP + HintChip.HEIGHT;
	}

	/** Resume, whenever the menu opens or comes back (plan P-2): the D-pad always starts from the top. */
	@Override
	protected void setInitialFocus() {
		if (!buttons.isEmpty()) {
			setInitialFocus(buttons.getFirst());
		}
	}

	private void choose(Entry entry) {
		HearthsideMod.LOGGER.info("Hearthside pause menu chose {}", entry.label().getString());
		if (padPressing) {
			// The controller drives the next screen (vanilla's Options or pause screen) with its pointer: no keyboard
			// focus lit there as well, which the D-pad here would otherwise leave behind.
			minecraft.setLastInputType(InputType.NONE);
		}
		entry.action().run();
	}

	/** As vanilla's Back to Game. */
	private void resume() {
		minecraft.gui.setScreen(null);
		minecraft.mouseHandler.grabMouse();
	}

	/** Vanilla's pause screen, unchanged and with every mod's buttons; back from it to play comes back here. */
	private void openGameMenu() {
		PauseScreen vanilla = new PauseScreen(true);
		gameMenu = new WeakReference<>(vanilla);
		gameMenuFrom = new WeakReference<>(this);
		minecraft.gui.setScreen(vanilla);
	}

	/**
	 * A guest leaves as vanilla's Disconnect does (player 1's game hears they left and closes theirs); player 1 is asked
	 * first, since everyone's game closes with theirs.
	 */
	private void leave() {
		if (guest) {
			disconnect(minecraft, this);
		} else {
			minecraft.gui.setScreen(new LeaveConfirmScreen(this));
		}
	}

	/** Vanilla's Disconnect and Save and Quit, as its pause screen does them (a draft report is asked about first). */
	static void disconnect(Minecraft minecraft, Screen from) {
		minecraft.getReportingContext().draftReportHandled(minecraft, from,
				() -> minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE), true);
	}

	@Override
	public void onClose() {
		resume();
	}

	@Override
	public boolean padInput(int player, InputFrame frame, long nanos) {
		int start = InputFrame.buttons(PadButton.START);
		boolean startPressed = lastButtons != -1 && (frame.buttons() & ~lastButtons & start) != 0;
		lastButtons = frame.buttons();
		if (startPressed) {
			// START toggles the menu, as it opened it.
			resume();
			return true;
		}
		boolean used = false;
		for (MenuNav.Action action : nav.step(frame, nanos)) {
			if (minecraft.gui.screen() != this) {
				return true;
			}
			used = true;
			if (action == MenuNav.Action.BACK) {
				resume();
				return true;
			}
			padPressing = action == MenuNav.Action.ACCEPT;
			try {
				MenuNavigator.apply(minecraft, this, action, focusColour());
			} finally {
				padPressing = false;
			}
		}
		return used;
	}

	private int focusColour() {
		Party party = guest ? null : Party.current();
		return party == null ? EMBER : party.colorOf(0);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// The world stays visible through the glass, a little dimmed.
		g.fill(0, 0, width, height, 0x60000000);
		// Backed, as the on-screen keyboard is, so the buttons stay readable over a bright world (and the crosshair stays
		// out of the panel).
		g.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + panelH - 2, 0xD8201410);
		HearthDraw.glass(g, panelX, panelY, panelW, panelH);
		int headingY = panelY + PAD;
		if (bigHeading) {
			HearthDraw.heading(g, font, title, width / 2, headingY, HearthDraw.CREAM);
		} else {
			HearthDraw.labelCentred(g, font, title, width / 2, headingY, HearthDraw.CREAM);
		}
		String line = status;
		if (!line.isEmpty()) {
			String fitted = font.plainSubstrByWidth(line, panelW - 2 * PAD);
			if (fitted.length() < line.length()) {
				fitted = font.plainSubstrByWidth(line, panelW - 2 * PAD - font.width("…")) + "…";
			}
			HearthDraw.labelCentred(g, font, Component.literal(fitted), width / 2, statusY, 0xFFD8C4A0);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		hints.update();
		if (PadHints.controllerDriven()) {
			// The controller's selection is the only one lit: a guest's game has no mouse (the OS arrow is player 1's,
			// wherever it last was), and player 1's mouse lights what it's over again once it moves.
			mouseX = mouseY = -1;
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}
}
