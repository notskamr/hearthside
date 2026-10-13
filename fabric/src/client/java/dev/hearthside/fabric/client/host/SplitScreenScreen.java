package dev.hearthside.fabric.client.host;

import dev.hearthside.core.config.Preset;
import dev.hearthside.core.config.SplitScreenConfig;
import dev.hearthside.core.config.TitleStyleSetting;
import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.MenuNav;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.layout.BorderStyle;
import dev.hearthside.core.layout.LayoutSettings;
import dev.hearthside.core.layout.ViewOrder;
import dev.hearthside.fabric.client.input.ButtonGuideRenderer;
import dev.hearthside.fabric.client.menu.MenuNavigator;
import dev.hearthside.fabric.client.menu.PadScreen;
import dev.hearthside.fabric.client.ui.HearthButton;
import dev.hearthside.fabric.client.ui.HearthDraw;
import dev.hearthside.fabric.client.ui.HearthSounds;
import dev.hearthside.fabric.client.ui.HearthSprites;
import dev.hearthside.fabric.client.ui.HintChip;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Player 1's split-screen settings (Spec E section 3.5), from their pause menu or Mod Menu's Configure: Cozy hearth like
 * Controls, driven by player 1's controller (D-pad, A, B), keyboard or mouse. A row cycles its value as it is pressed,
 * and each change is saved and applies at once: on the left what changes live (the views glide to a new layout), with
 * Swap views for the players in the world; on the right what applies when the next player joins. With no party (Mod
 * Menu at the title screen) a change is only saved.
 */
public final class SplitScreenScreen extends Screen implements PadScreen {
	private static final int TOP = 32, ROW_H = 14, ROW_GAP = 2, HEADING_H = 13, SECTION_GAP = 6;
	private static final int VALUE_INK = 0xFFFFC27A, CAPTION_INK = 0xFFD8C4A0;
	/** Player 1's focus outline with no party (their colour isn't known): the ember accent. */
	private static final int EMBER = 0xFFB35A;
	private static final Component NEXT_JOIN = Component.literal("Applies when the next player joins");

	private final Screen parent;
	/** The settings as last applied, when there is no party to hold them. */
	private SplitScreenConfig unpartied;
	/** Fresh each time the screen (re)opens, so the press that opened it presses nothing here. */
	private MenuNav nav = new MenuNav();
	private final List<SettingRow> rows = new ArrayList<>();
	/** Swap views' buttons by seat and step (-1 earlier, 1 later), so focus can follow the player moved. */
	private final Map<Integer, Map<Integer, Button>> swaps = new HashMap<>();
	/** Where the sections' headings and the swap rows' names go, laid out in {@link #init}. */
	private int columnW, leftX, rightX, rowH, rowGap, liveHeadingY, swapHeadingY, nextHeadingY, captionY;
	private final List<int[]> swapNames = new ArrayList<>();

	public SplitScreenScreen(Screen parent) {
		super(Component.literal("Split-screen"));
		this.parent = parent;
	}

	/** One setting and its value; pressing it moves to the next value. */
	private final class SettingRow extends Button {
		private final String label;
		private final Function<SplitScreenConfig, String> value;

		SettingRow(int x, int y, int width, int height, String label, Function<SplitScreenConfig, String> value,
				UnaryOperator<SplitScreenConfig> next) {
			super(x, y, width, height, Component.literal(label), b -> apply(next.apply(config())), DEFAULT_NARRATION);
			this.label = label;
			this.value = value;
		}

		@Override
		public void playDownSound(SoundManager soundManager) {
			HearthSounds.play(HearthSounds.Ui.PAGE);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			String shown = value.apply(config());
			setMessage(Component.literal(label + ": " + shown));
			boolean selected = HearthDraw.selected(this);
			var sprite = selected ? HearthSprites.BUTTON_HIGHLIGHTED : HearthSprites.BUTTON;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX(), getY(), getWidth(), getHeight());
			if (isHovered() && !selected) {
				HearthDraw.hoverGlow(g, getX(), getY(), getWidth(), getHeight());
			}
			int textY = getY() + (getHeight() - 8) / 2;
			int valueW = font.width(shown);
			int right = getX() + getWidth() - 5;
			String fitted = font.plainSubstrByWidth(label, Math.max(0, getWidth() - 6 - 5 - valueW - 6));
			HearthDraw.label(g, font, Component.literal(fitted), getX() + 6, textY, HearthDraw.CREAM);
			HearthDraw.label(g, font, Component.literal(shown), right - valueW, textY, VALUE_INK);
		}
	}

	/** The settings now: the party's, or (with none) as last saved here. */
	private SplitScreenConfig config() {
		Party party = Party.current();
		if (party != null) {
			return party.config();
		}
		if (unpartied == null) {
			unpartied = SplitScreenConfigFile.load(gameDir());
		}
		return unpartied;
	}

	private void apply(SplitScreenConfig next) {
		Party party = Party.current();
		if (party != null) {
			party.applySettings(next);
		} else {
			SplitScreenConfigFile.apply(gameDir(), config(), next);
			unpartied = next;
		}
	}

	private Path gameDir() {
		return minecraft.gameDirectory.toPath().toAbsolutePath();
	}

	/** The seats in the order they fill the views, if there are two or more to swap. */
	private List<Integer> viewOrder() {
		Party party = Party.current();
		List<Integer> order = party == null ? List.of() : party.viewOrder();
		return order.size() > 1 ? order : List.of();
	}

	@Override
	protected void init() {
		nav = new MenuNav();
		rows.clear();
		swaps.clear();
		swapNames.clear();
		columnW = Math.min(190, (width - 30) / 2);
		leftX = width / 2 - columnW - 5;
		rightX = width / 2 + 5;
		List<Integer> order = viewOrder();
		// The live column is the taller: shrink the rows a little where the view is short (a stacked split).
		int liveRows = 7 + order.size();
		int room = height - 26 - TOP - 2 * HEADING_H - (order.isEmpty() ? 0 : SECTION_GAP);
		boolean tight = liveRows * (ROW_H + ROW_GAP) > room;
		rowH = tight ? 12 : ROW_H;
		rowGap = tight ? 1 : ROW_GAP;

		liveHeadingY = TOP;
		int y = TOP + HEADING_H;
		y = row(leftX, y, "Two players",
				c -> c.layout().two() == LayoutSettings.TwoPlayer.STACKED ? "Stacked" : "Side by side",
				c -> c.withLayout(c.layout().withTwo(next(LayoutSettings.TwoPlayer.values(), c.layout().two()))));
		y = row(leftX, y, "Three players", c -> switch (c.layout().three()) {
					case GRID_WITH_PANEL -> "Grid";
					case WIDE_TOP -> "Wide top";
					case TALL_LEFT -> "Tall left";
				},
				c -> c.withLayout(c.layout().withThree(next(LayoutSettings.ThreePlayer.values(), c.layout().three()))));
		y = row(leftX, y, "Party map", c -> onOff(c.layout().partyPanel()),
				c -> c.withLayout(c.layout().withPartyPanel(!c.layout().partyPanel())));
		y = row(leftX, y, "Borders", c -> switch (c.layout().borders()) {
					case COLOUR -> "Colour";
					case THIN -> "Thin";
					case OFF -> "Off";
				},
				c -> c.withLayout(c.layout().withBorders(next(BorderStyle.values(), c.layout().borders()))));
		y = row(leftX, y, "Name tags", c -> onOff(c.layout().nameTags()),
				c -> c.withLayout(c.layout().withNameTags(!c.layout().nameTags())));
		y = row(leftX, y, "Layout animation", c -> onOff(c.layout().layoutAnimation()),
				c -> c.withLayout(c.layout().withLayoutAnimation(!c.layout().layoutAnimation())));
		y = row(leftX, y, "Title screen", c -> c.titleStyle() == TitleStyleSetting.VANILLA ? "Vanilla" : "Hearthside",
				c -> c.withTitleStyle(next(TitleStyleSetting.values(), c.titleStyle())));
		swapHeadingY = y + SECTION_GAP;
		y = swapHeadingY + HEADING_H;
		int stepW = Math.min(52, (columnW - 40) / 2);
		for (int i = 0; i < order.size(); i++) {
			int seat = order.get(i);
			swapNames.add(new int[] {seat, y});
			Map<Integer, Button> buttons = new HashMap<>();
			buttons.put(-1, swapButton(leftX + columnW - 2 * stepW - 3, y, stepW, "Earlier", seat, -1, i > 0));
			buttons.put(1, swapButton(leftX + columnW - stepW, y, stepW, "Later", seat, 1, i < order.size() - 1));
			swaps.put(seat, buttons);
			y += rowH + rowGap;
		}

		nextHeadingY = TOP;
		captionY = TOP + HEADING_H - 2;
		List<FormattedCharSequence> caption = font.split(NEXT_JOIN, columnW);
		y = captionY + caption.size() * 10 + 3;
		y = row(rightX, y, "Preset", c -> title(c.preset().name()),
				c -> c.withPreset(next(Preset.values(), c.preset())));
		y = row(rightX, y, "Preset for player 1", c -> onOff(c.presetForPlayer1()),
				c -> c.withPresetForPlayer1(!c.presetForPlayer1()));
		y = row(rightX, y, "Guests' render distance", c -> renderDistance(c.guestRenderDistance()),
				c -> c.withGuestRenderDistance(next(RENDER_DISTANCES, c.guestRenderDistance())));
		row(rightX, y, "Guests' memory", c -> c.guestMemoryMb() + " MB",
				c -> c.withGuestMemoryMb(next(MEMORY_MB, c.guestMemoryMb())));

		HintChip[] chips = {
				new HintChip(0, 0, Control.A, this::style, Component.literal("Change"), b -> { }),
				new HintChip(0, 0, Control.B, this::style, Component.literal("Back"), b -> onClose()),
		};
		int cx = width - 8;
		for (int i = chips.length - 1; i >= 0; i--) {
			cx -= chips[i].getWidth();
			chips[i].setPosition(cx, height - 22);
			cx -= 4;
			addRenderableWidget(chips[i]);
		}
	}

	private int row(int x, int y, String label, Function<SplitScreenConfig, String> value,
			UnaryOperator<SplitScreenConfig> next) {
		rows.add(addRenderableWidget(new SettingRow(x, y, columnW, rowH, label, value, next)));
		return y + rowH + rowGap;
	}

	private Button swapButton(int x, int y, int w, String label, int seat, int step, boolean active) {
		HearthButton button = new HearthButton(x, y, w, rowH, Component.literal(label), b -> swap(seat, step));
		button.withSound(HearthSounds.Ui.PAGE);
		button.active = active;
		return addRenderableWidget(button);
	}

	/** Moves a player one view earlier or later; the focus stays on the button just pressed, for that player. */
	private void swap(int seat, int step) {
		Party party = Party.current();
		if (party == null) {
			return;
		}
		List<Integer> order = party.viewOrder();
		party.setViewOrder(step < 0 ? ViewOrder.earlier(order, seat) : ViewOrder.later(order, seat));
		rebuildWidgets();
		Map<Integer, Button> buttons = swaps.get(seat);
		if (buttons != null) {
			Button same = buttons.get(step);
			setFocused(same.active ? same : buttons.get(-step));
		}
	}

	/** The first setting (vanilla's own initial focus would tab past it after controller input). */
	@Override
	protected void setInitialFocus() {
		if (!rows.isEmpty()) {
			setInitialFocus(rows.getFirst());
		}
	}

	private PadStyle style() {
		Party party = Party.current();
		return party != null ? party.padStyleOf(0) : ButtonGuideRenderer.style();
	}

	private int focusColour() {
		Party party = Party.current();
		return party == null ? EMBER : party.colorOf(0);
	}

	/** B or Esc: back to where it was opened from. Every change is already saved. */
	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean padInput(int player, InputFrame frame, long nanos) {
		if (player != 0) {
			// Player 1's settings: only their controller drives this screen.
			return false;
		}
		for (MenuNav.Action action : nav.step(frame, nanos)) {
			if (minecraft.gui.screen() != this) {
				return true;
			}
			if (action == MenuNav.Action.BACK) {
				onClose();
				return true;
			}
			MenuNavigator.apply(minecraft, this, action, focusColour());
		}
		return true;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		HearthDraw.board(g, 0, 0, width, height);
		HearthDraw.firelightGlow(g, width / 10, height * 35 / 100, width * 8 / 10, height - height * 35 / 100, 0.85f);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int pw = font.width(title) + 20;
		HearthDraw.pill(g, width / 2 - pw / 2, 8, pw, 16);
		HearthDraw.labelCentred(g, font, title, width / 2, 12, HearthDraw.CREAM);
		HearthDraw.label(g, font, Component.literal("Layout and look"), leftX + 2, liveHeadingY, HearthDraw.CREAM);
		HearthDraw.label(g, font, Component.literal("Performance"), rightX + 2, nextHeadingY, HearthDraw.CREAM);
		int y = captionY;
		for (FormattedCharSequence line : font.split(NEXT_JOIN, columnW)) {
			g.text(font, line, rightX + 2, y, CAPTION_INK, true);
			y += 10;
		}
		if (!swapNames.isEmpty()) {
			HearthDraw.label(g, font, Component.literal("Swap views"), leftX + 2, swapHeadingY, HearthDraw.CREAM);
			Party party = Party.current();
			int stepW = Math.min(52, (columnW - 40) / 2);
			for (int i = 0; i < swapNames.size(); i++) {
				int seat = swapNames.get(i)[0], rowY = swapNames.get(i)[1];
				String name = (i + 1) + ". " + (party == null ? "" : party.nameOf(seat));
				String fitted = font.plainSubstrByWidth(name, columnW - 2 * stepW - 3 - 8);
				int colour = party == null ? HearthDraw.CREAM : 0xFF000000 | party.colorOf(seat);
				g.fill(leftX + 2, rowY + (rowH - 5) / 2, leftX + 7, rowY + (rowH - 5) / 2 + 5, colour);
				HearthDraw.label(g, font, Component.literal(fitted), leftX + 10, rowY + (rowH - 8) / 2, HearthDraw.CREAM);
			}
		}
	}

	// ---- Values ----

	/** Guests' render distance, in chunks: the preset's, a cap, or their own setting (no cap). */
	private static final List<Integer> RENDER_DISTANCES = List.of(SplitScreenConfig.RENDER_DISTANCE_FROM_PRESET, 4, 6, 8,
			10, 12, 16, 24, 32, SplitScreenConfig.NO_RENDER_DISTANCE_CAP);
	private static final List<Integer> MEMORY_MB = List.of(768, 1024, 1536, 2048, 3072, 4096, 6144, 8192);

	private static String renderDistance(int chunks) {
		return switch (chunks) {
			case SplitScreenConfig.RENDER_DISTANCE_FROM_PRESET -> "Preset";
			case SplitScreenConfig.NO_RENDER_DISTANCE_CAP -> "Their own";
			default -> String.valueOf(chunks);
		};
	}

	private static String onOff(boolean on) {
		return on ? "On" : "Off";
	}

	/** {@code BALANCED} as "Balanced". */
	private static String title(String constant) {
		return constant.charAt(0) + constant.substring(1).toLowerCase(java.util.Locale.ROOT);
	}

	private static <E extends Enum<E>> E next(E[] values, E current) {
		return values[(current.ordinal() + 1) % values.length];
	}

	/** The value after {@code current}; one not among {@code values} (a hand-edited file's) goes to the first. */
	private static int next(List<Integer> values, int current) {
		int at = values.indexOf(current);
		return values.get(at < 0 ? 0 : (at + 1) % values.size());
	}
}
