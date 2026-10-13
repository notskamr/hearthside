package dev.hearthside.fabric.client.menu;

import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.MenuNav;
import dev.hearthside.fabric.client.host.Party;
import dev.hearthside.fabric.client.ui.HearthButton;
import dev.hearthside.fabric.client.ui.HearthDraw;
import dev.hearthside.fabric.client.ui.HintChip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Player 1's Leave game asks first (Spec E section 3.1), since everyone's game closes with theirs: a Cozy-hearth card
 * over the world. Leave is vanilla's Save and Quit; Keep playing (or Esc, or B) goes back to the pause menu.
 */
final class LeaveConfirmScreen extends Screen implements PadScreen {
	static final Component TITLE = Component.literal("Leave the world? Everyone's game will close.");
	/** The title as the card shows it: the question, then what it means, each on its own line(s). */
	private static final Component QUESTION = Component.literal("Leave the world?"),
			MEANS = Component.literal("Everyone's game will close.");
	private static final Component LEAVE = Component.literal("Leave"), KEEP_PLAYING = Component.literal("Keep playing");
	private static final int PANEL_W = 230, PAD = 12, LINE = 10, BUTTON_W = 96, BUTTON_H = 20, GAP = 8, MARGIN = 6;
	private static final int HINT_GAP = 4;
	/** The hotbar and experience bar at the bottom of the view, kept clear. */
	private static final int HUD = 30;

	private final PauseMenuScreen parent;
	private MenuNav nav = new MenuNav();
	private HearthButton keepPlaying;
	private PadHints hints;
	private int panelX, panelY, panelW, panelH;

	LeaveConfirmScreen(PauseMenuScreen parent) {
		super(TITLE);
		this.parent = parent;
	}

	@Override
	protected void init() {
		nav = new MenuNav();
		panelW = Math.min(PANEL_W, width - 2 * MARGIN);
		int inner = panelW - 2 * PAD;
		int lines = font.split(QUESTION, inner).size() + font.split(MEANS, inner).size();
		// The buttons sit on the card under the question: side by side where they fit, else one above the other.
		boolean stacked = 2 * BUTTON_W + GAP > inner;
		int buttonsH = stacked ? 2 * BUTTON_H + 4 : BUTTON_H;
		panelH = PAD + lines * LINE + 8 + buttonsH + PAD;
		panelX = (width - panelW) / 2;
		panelY = Math.max(MARGIN, (height - HUD - panelH - HINT_GAP - HintChip.HEIGHT) / 2);
		int y = panelY + PAD + lines * LINE + 8;
		HearthButton leave;
		if (stacked) {
			leave = new HearthButton(panelX + PAD, y, inner, BUTTON_H, LEAVE, b -> PauseMenuScreen.disconnect(minecraft, this));
			keepPlaying = new HearthButton(panelX + PAD, y + BUTTON_H + 4, inner, BUTTON_H, KEEP_PLAYING, b -> onClose());
		} else {
			int x = (width - 2 * BUTTON_W - GAP) / 2;
			leave = new HearthButton(x, y, BUTTON_W, BUTTON_H, LEAVE, b -> PauseMenuScreen.disconnect(minecraft, this));
			keepPlaying = new HearthButton(x + BUTTON_W + GAP, y, BUTTON_W, BUTTON_H, KEEP_PLAYING, b -> onClose());
		}
		addRenderableWidget(leave);
		addRenderableWidget(keepPlaying);
		// Its own hints under the card, as on the pause menu: A presses whatever is focused, B keeps playing.
		hints = new PadHints(this, Component.literal("Back"), this::onClose);
		hints.addTo(this::addRenderableWidget, width / 2, panelY + panelH + HINT_GAP);
	}

	/** Keep playing: leaving is never one stray press away. */
	@Override
	protected void setInitialFocus() {
		setInitialFocus(keepPlaying);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		hints.update();
		if (PadHints.controllerDriven()) {
			// As on the pause menu: the controller's selection is the only one lit until the mouse takes over.
			mouseX = mouseY = -1;
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean padInput(int player, InputFrame frame, long nanos) {
		boolean used = false;
		for (MenuNav.Action action : nav.step(frame, nanos)) {
			if (minecraft.gui.screen() != this) {
				return true;
			}
			used = true;
			if (action == MenuNav.Action.BACK) {
				onClose();
				return true;
			}
			Party party = Party.current();
			MenuNavigator.apply(minecraft, this, action, party == null ? 0xFFB35A : party.colorOf(0));
		}
		return used;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, 0x80000000);
		HearthDraw.parchment(g, panelX, panelY, panelW, panelH);
		int y = inkLines(g, QUESTION, panelY + PAD, HearthDraw.INK);
		inkLines(g, MEANS, y, HearthDraw.MUTED);
	}

	/** Ink on parchment, wrapped to the card and centred; returns the y below it. */
	private int inkLines(GuiGraphicsExtractor g, Component text, int y, int argb) {
		for (FormattedCharSequence line : font.split(text, panelW - 2 * PAD)) {
			g.text(font, line, width / 2 - font.width(line) / 2, y, argb, false);
			y += LINE;
		}
		return y;
	}
}
