package dev.hearthside.fabric.client.menu;

import dev.hearthside.core.Role;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.MenuNav;
import dev.hearthside.core.input.PadButton;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.HearthsideClient;
import dev.hearthside.fabric.client.host.Party;
import dev.hearthside.fabric.client.host.SplitScreenScreen;
import dev.hearthside.fabric.client.satellite.SatelliteBoot;
import dev.hearthside.fabric.client.ui.GlassButton;
import dev.hearthside.fabric.client.ui.HearthDraw;
import dev.hearthside.fabric.client.ui.HintChip;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

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
			SPLIT_SCREEN = Component.literal("Split-screen…"), ADD_PLAYER = Component.literal("Add player");
	private static final int BUTTON_W = 150, BUTTON_H = 20, GAP = 4, PAD = 10, MARGIN = 6, HINT_GAP = 4;
	/** The hotbar and experience bar at the bottom of the view, kept clear. */
	private static final int HUD = 30;
	/** A guest's focus outline: the ember accent (their game doesn't know their profile's colour). */
	private static final int EMBER = 0xFFB35A;
	/** The status line's line height: up to three names (16 characters each) wrap onto more lines, never cut. */
	private static final int STATUS_LINE = 10;
	private static final boolean E2E = "1".equals(System.getenv("HEARTHSIDE_E2E_PATTERN"));
	/** What every pause menu in this game says under "Paused": who is still playing, or that the world is frozen. */
	private static volatile String status = "";
	/**
	 * A guest's controller is away (between the host's PAUSE and RESUME): the menu draws only its dim, so the reconnect
	 * card player 1's game draws over this view is the only thing asking anything (Spec E section 3.3). Render thread.
	 */
	private static boolean controllerAway;
	/** Vanilla's pause screen opened by Game menu…, and the menu that closing it to play goes back to. Render thread. */
	private static WeakReference<PauseScreen> gameMenu = new WeakReference<>(null);
	private static WeakReference<PauseMenuScreen> gameMenuFrom = new WeakReference<>(null);

	/** One entry in the column; whether it can be chosen is asked each time the menu is shown. */
	private record Entry(Component label, Runnable action, BooleanSupplier active) {
		Entry(Component label, Runnable action, boolean active) {
			this(label, action, () -> active);
		}
	}

	/**
	 * How compactly the panel is drawn: the heading at twice the font's size or its own, the gap between buttons, the
	 * buttons' height, and whether the hints sit inside the panel's foot rather than under it.
	 */
	private record Fit(boolean bigHeading, int gap, int buttonH, boolean hintsInside) {}

	/**
	 * Roomiest first, tried until the panel fits every line of the status: the small heading and 2 px gaps, then shorter
	 * buttons (their labels still centred), then the hints inside the panel. Only a view too small for the last cuts it.
	 */
	private static final List<Fit> FITS = List.of(new Fit(true, GAP, BUTTON_H, false), new Fit(false, GAP, BUTTON_H, false),
			new Fit(false, 2, BUTTON_H, false), new Fit(false, 2, 18, false), new Fit(false, 2, 16, false),
			new Fit(false, 2, 16, true));
	/** Under hints inside the panel, the panel's own bottom edge. */
	private static final int FOOT = 4;

	private final List<Entry> entries = new ArrayList<>();
	private final List<GlassButton> buttons = new ArrayList<>();
	private final boolean guest;
	/** Fresh each time the menu (re)opens, so the press that opened it or came back to it presses nothing here. */
	private MenuNav nav = new MenuNav();
	private int lastButtons = -1;
	private int panelX, panelY, panelW, panelH, statusY;
	private Fit fit = FITS.getFirst();
	/** The status line as laid out (wrapped to the panel), and the status it was laid out for. Render thread. */
	private List<String> statusLines = List.of();
	private String laidOutFor = "";
	/** The view was too small for every line of the status: its last line is cut short. */
	private boolean statusTruncated;
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
			// Player 1 asks for another player (Spec E section 3.4): greyed out with a full party (plan R3), as it is each
			// time the menu shows (the same menu comes back from Options… and Game menu…).
			addEntry(1, ADD_PLAYER, this::addPlayer, () -> {
				Party party = Party.current();
				return party != null && !party.partyFull();
			});
			// Player 1's split-screen settings, applied live (Spec E section 3.5); guests' menus don't have them.
			addEntry(2, SPLIT_SCREEN, () -> minecraft.gui.setScreen(new SplitScreenScreen(this)), true);
		}
	}

	/** Back to play, with the join prompt up for a new controller's single A. */
	private void addPlayer() {
		resume();
		Party party = Party.current();
		if (party != null) {
			party.openAddPlayer();
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
	 * A guest's controller went away (the host's PAUSE) or came back or was taken over (its RESUME): while away, any
	 * pause menu in this game shows only its dim, and a menu still open when it comes back shows again as it was.
	 * Render thread.
	 */
	public static void setControllerAway(boolean away) {
		if (away && !controllerAway) {
			HearthsideMod.LOGGER.info("Hearthside pause menu hidden under the reconnect card");
		}
		controllerAway = away;
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
		addEntry(index, label, action, () -> active);
	}

	/** As {@link #addEntry(int, Component, Runnable, boolean)}, with {@code active} asked each time the menu is shown. */
	public void addEntry(int index, Component label, Runnable action, BooleanSupplier active) {
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
		// Fresh for this party and world: player 1's from who is paused now, a guest's from the host's last word.
		setStatus(guest ? SatelliteBoot.pauseLine() : Party.pauseLine());
		HearthsideMod.LOGGER.info("Hearthside pause menu opened entries={}",
				entries.stream().map(e -> e.label().getString()).collect(Collectors.joining(",")));
	}

	/** "Paused" and the status line, so a screen reader hears who is still playing. */
	@Override
	public Component getNarrationMessage() {
		String line = status;
		return line.isEmpty() ? title : CommonComponents.joinForNarration(title, Component.literal(line));
	}

	@Override
	protected void init() {
		nav = new MenuNav();
		lastButtons = -1;
		buttons.clear();
		for (Entry entry : entries) {
			GlassButton button = new GlassButton(0, 0, BUTTON_W, BUTTON_H, entry.label(), b -> choose(entry));
			button.active = entry.active().getAsBoolean();
			buttons.add(addRenderableWidget(button));
		}
		// Its own hints: Minecraft's screens get ButtonGuideRenderer's, Hearthside's draw their own.
		hints = new PadHints(this, RESUME, this::resume);
		hints.addTo(this::addRenderableWidget, width / 2, 0);
		layout();
	}

	/**
	 * Lays the panel out for the status line it shows now, wrapped to the panel in as many lines as it needs (one is
	 * always kept for it): the panel with its hints under it, centred above the hotbar (which stays visible through the
	 * dim), drawn as roomily as every line allows ({@link #FITS}). Run again whenever the status line changes, moving the
	 * buttons and hints without touching the focus. Only a view too small for every line at the most compact cuts the
	 * last one short.
	 */
	private void layout() {
		laidOutFor = status;
		panelW = Math.min(BUTTON_W + 2 * PAD + 40, width - 2 * MARGIN);
		int room = height - HUD - 2 * MARGIN;
		List<String> lines = new ArrayList<>(wrap(status, panelW - 2 * PAD));
		int n = Math.max(1, lines.size());
		fit = FITS.stream().filter(f -> blockHeight(f, n) <= room).findFirst().orElse(FITS.getLast());
		int most = Math.max(1, (room - blockHeight(fit, 0)) / STATUS_LINE);
		statusTruncated = lines.size() > most;
		if (statusTruncated) {
			lines = new ArrayList<>(lines.subList(0, most));
			String last = lines.getLast();
			lines.set(most - 1, font.plainSubstrByWidth(last, panelW - 2 * PAD - font.width("…")) + "…");
		}
		statusLines = List.copyOf(lines);
		int shown = Math.max(1, lines.size());
		int heading = fit.bigHeading() ? 18 : 9;
		int column = entries.size() * fit.buttonH() + (entries.size() - 1) * fit.gap();
		panelH = PAD + heading + 4 + statusHeight(shown) + 8 + column
				+ (fit.hintsInside() ? HINT_GAP + HintChip.HEIGHT + FOOT : PAD);
		panelX = (width - panelW) / 2;
		panelY = Math.max(MARGIN, MARGIN + (room - blockHeight(fit, shown)) / 2);
		statusY = panelY + PAD + heading + 4;
		int buttonW = Math.min(BUTTON_W, panelW - 2 * PAD);
		int y = statusY + statusHeight(shown) + 8;
		for (GlassButton button : buttons) {
			button.setRectangle(buttonW, fit.buttonH(), (width - buttonW) / 2, y);
			y += fit.buttonH() + fit.gap();
		}
		hints.place(width / 2, fit.hintsInside() ? panelY + panelH - FOOT - HintChip.HEIGHT : panelY + panelH + HINT_GAP);
		if (E2E) {
			HearthsideMod.LOGGER.info("Hearthside pause status lines={} truncated={} drawn={}", statusLines.size(),
					statusTruncated, String.join("|", statusLines));
		}
	}

	/** The status line's height in {@code lines} lines (one line still takes a line's room when it's empty). */
	private static int statusHeight(int lines) {
		return Math.max(1, lines) * STATUS_LINE - 1;
	}

	/** The panel and its hints, drawn as {@code fit} says, with the status in this many lines. */
	private int blockHeight(Fit fit, int statusLines) {
		int column = entries.size() * fit.buttonH() + (entries.size() - 1) * fit.gap();
		int status = statusLines == 0 ? -1 : statusHeight(statusLines);
		int foot = fit.hintsInside() ? FOOT : PAD;
		return PAD + (fit.bigHeading() ? 18 : 9) + 4 + status + 8 + column + HINT_GAP + HintChip.HEIGHT + foot;
	}

	/**
	 * The status wrapped to {@code width}: broken after its em dash when it needs more than one line ("The world keeps
	 * going —" / "Player2 is still playing"), unless that would take more lines than wrapping it as it comes.
	 */
	private List<String> wrap(String text, int width) {
		if (text.isEmpty()) {
			return List.of();
		}
		List<String> plain = split(text, width);
		int dash = text.indexOf(" — ");
		if (plain.size() < 2 || dash < 0) {
			return plain;
		}
		List<String> broken = new ArrayList<>(split(text.substring(0, dash + 2), width));
		broken.addAll(split(text.substring(dash + 3), width));
		return broken.size() <= plain.size() ? broken : plain;
	}

	private List<String> split(String text, int width) {
		List<String> lines = new ArrayList<>();
		for (FormattedCharSequence line : font.split(Component.literal(text), width)) {
			StringBuilder chars = new StringBuilder();
			line.accept((index, style, codePoint) -> {
				chars.appendCodePoint(codePoint);
				return true;
			});
			lines.add(chars.toString());
		}
		return lines;
	}

	/** E2E: the open pause menu's status line as drawn, line by line, or null when none is open. Render thread. */
	public static List<String> e2eDrawnStatus(Minecraft minecraft) {
		return minecraft.gui.screen() instanceof PauseMenuScreen menu ? menu.statusLines : null;
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
		if (controllerAway) {
			return; // only the reconnect card over this view asks anything now
		}
		if (!status.equals(laidOutFor)) {
			layout(); // the host's word changed while the menu was open: the panel fits the new line
		}
		// Backed, as the on-screen keyboard is, so the buttons stay readable over a bright world (and the crosshair stays
		// out of the panel).
		g.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + panelH - 2, 0xD8201410);
		HearthDraw.glass(g, panelX, panelY, panelW, panelH);
		int headingY = panelY + PAD;
		if (fit.bigHeading()) {
			HearthDraw.heading(g, font, title, width / 2, headingY, HearthDraw.CREAM);
		} else {
			HearthDraw.labelCentred(g, font, title, width / 2, headingY, HearthDraw.CREAM);
		}
		int y = statusY;
		for (String line : statusLines) {
			HearthDraw.labelCentred(g, font, Component.literal(line), width / 2, y, 0xFFD8C4A0);
			y += STATUS_LINE;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (controllerAway) {
			return; // no entries and no hints for a controller that isn't there
		}
		hints.update();
		if (PadHints.controllerDriven()) {
			// The controller's selection is the only one lit: a guest's game has no mouse (the OS arrow is player 1's,
			// wherever it last was), and player 1's mouse lights what it's over again once it moves.
			mouseX = mouseY = -1;
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}
}
