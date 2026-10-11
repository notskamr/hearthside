package dev.hearthside.fabric.client.host;

import dev.hearthside.core.input.Action;
import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.MenuNav;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.input.RemapModel;
import dev.hearthside.core.profile.GuestProfile;
import dev.hearthside.fabric.client.menu.MenuNavigator;
import dev.hearthside.fabric.client.menu.SeatScreen;
import dev.hearthside.fabric.client.ui.HearthDraw;
import dev.hearthside.fabric.client.ui.HearthSounds;
import dev.hearthside.fabric.client.ui.HearthSprites;
import dev.hearthside.fabric.client.ui.HintChip;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * One profile's controller layout (Spec A section 5.4), driven by that seat's controller only: every in-world action
 * with its button; A on an action waits for the button to bind (swapping with whatever had it); LB/RB try the
 * presets; a strip at the bottom says what each pressed button would do. Menu saves, B discards.
 */
public final class ControlsScreen extends Screen implements SeatScreen {
	private static final int ROWS = 8, ROW_H = 14, ROW_GAP = 2, TOP = 48;
	private static final long LISTEN_NANOS = 5_000_000_000L;
	private static final int ERROR_INK = 0xFFA8321E;

	private final Screen parent;
	private final Party party;
	private final int seat;
	private final GuestProfile profile;
	private final RemapModel model;
	private final MenuNav nav = new MenuNav();
	private final List<ActionRow> rows = new ArrayList<>();
	/** Controls down on the last frame; null until the first (what is held then counts as already pressed). */
	private Set<Control> lastDown;
	private long listenStarted;
	/** The last control pressed, for the test strip. */
	private Control tried;
	private String problem;

	public ControlsScreen(Screen parent, Party party, int seat, GuestProfile profile) {
		super(Component.literal("Controls"));
		this.parent = parent;
		this.party = party;
		this.seat = seat;
		this.profile = profile;
		this.model = new RemapModel(profile.controls());
	}

	/** One action and its button. */
	private final class ActionRow extends Button {
		final Action action;

		ActionRow(int x, int y, int width, Action action) {
			super(x, y, width, ROW_H, Component.literal(RemapModel.label(action)), b -> listen(action), DEFAULT_NARRATION);
			this.action = action;
		}

		@Override
		public void playDownSound(SoundManager soundManager) {
			HearthSounds.play(HearthSounds.Ui.KNOCK);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			var sprite = isHoveredOrFocused() ? HearthSprites.BUTTON_HIGHLIGHTED : HearthSprites.BUTTON;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX(), getY(), getWidth(), getHeight());
			int textY = getY() + (getHeight() - 8) / 2;
			HearthDraw.label(g, font, getMessage(), getX() + 6, textY, HearthDraw.CREAM);
			int right = getX() + getWidth() - 5;
			if (model.listening() == action) {
				Component waiting = Component.literal("press…");
				HearthDraw.label(g, font, waiting, right - font.width(waiting), textY, 0xFFFFC27A);
				return;
			}
			var control = model.scheme().controlFor(action);
			if (control.isPresent()) {
				int w = HearthDraw.glyphWidth(control.get());
				HearthDraw.glyph(g, control.get(), style(), right - w, getY() + (getHeight() - HearthDraw.GLYPH_HEIGHT) / 2);
			} else {
				Component none = Component.literal("—");
				HearthDraw.label(g, font, none, right - font.width(none), textY, 0xFFB8A48C);
			}
		}
	}

	private PadStyle style() {
		return party.padStyleOf(seat);
	}

	private int columnWidth() {
		return Math.min(160, (width - 30) / 2);
	}

	@Override
	protected void init() {
		rows.clear();
		int w = columnWidth();
		Action[] actions = Action.values();
		for (int i = 0; i < actions.length; i++) {
			int column = i / ROWS;
			int x = column == 0 ? width / 2 - w - 4 : width / 2 + 4;
			int y = TOP + (i % ROWS) * (ROW_H + ROW_GAP);
			rows.add(addRenderableWidget(new ActionRow(x, y, w, actions[i])));
		}
		HintChip[] chips = {
				new HintChip(0, 0, Control.A, this::style, Component.literal("Change"), b -> { }),
				new HintChip(0, 0, Control.START, this::style, Component.literal("Save"), b -> save()),
				new HintChip(0, 0, Control.B, this::style, Component.literal("Cancel"), b -> onClose()),
		};
		int cx = width - 8;
		for (int i = chips.length - 1; i >= 0; i--) {
			cx -= chips[i].getWidth();
			chips[i].setPosition(cx, height - 22);
			cx -= 4;
			addRenderableWidget(chips[i]);
		}
	}

	/** The first action (vanilla's own initial focus would tab past it after controller input). */
	@Override
	protected void setInitialFocus() {
		if (!rows.isEmpty()) {
			setInitialFocus(rows.getFirst());
		}
	}

	private void listen(Action action) {
		model.listen(action);
		listenStarted = System.nanoTime();
		problem = null;
	}

	/** Menu: keep these controls. */
	public void save() {
		if (party.saveControls(profile.id(), model.scheme())) {
			onClose();
		} else {
			problem = "Couldn't save the controls";
		}
	}

	/** B or Esc: back to the lobby, the changes dropped. */
	@Override
	public void onClose() {
		party.backToCard(seat);
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean padInput(int player, InputFrame frame, long nanos) {
		if (player != seat) {
			// Only the seat whose controls these are drives this screen.
			return false;
		}
		Set<Control> down = EnumSet.noneOf(Control.class);
		for (Control control : Control.values()) {
			if (control.isDown(frame)) {
				down.add(control);
			}
		}
		Set<Control> pressed = EnumSet.copyOf(down);
		if (lastDown == null) {
			pressed.clear();
		} else {
			pressed.removeAll(lastDown);
		}
		lastDown = down;
		pressed.remove(Control.GUIDE);
		if (model.listening() != null) {
			// Waiting for a button: the first one pressed is bound (B and Menu too); nothing else happens this frame.
			if (!pressed.isEmpty()) {
				Control control = pressed.iterator().next();
				model.press(control);
				HearthSounds.play(HearthSounds.Ui.KNOCK);
				tried = control;
			} else if (nanos - listenStarted > LISTEN_NANOS) {
				model.cancel();
			}
			nav.step(frame, nanos);
			return true;
		}
		if (!pressed.isEmpty()) {
			tried = pressed.iterator().next();
		}
		if (pressed.contains(Control.LEFT_BUMPER)) {
			model.previousPreset();
			HearthSounds.play(HearthSounds.Ui.PAGE);
		}
		if (pressed.contains(Control.RIGHT_BUMPER)) {
			model.nextPreset();
			HearthSounds.play(HearthSounds.Ui.PAGE);
		}
		if (pressed.contains(Control.START)) {
			HearthSounds.play(HearthSounds.Ui.KNOCK);
			save();
			return true;
		}
		for (MenuNav.Action action : nav.step(frame, nanos)) {
			if (minecraft.gui.screen() != this) {
				return true;
			}
			if (action == MenuNav.Action.BACK) {
				onClose();
				return true;
			}
			MenuNavigator.apply(minecraft, this, action, party.colorOf(seat));
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
		Component heading = Component.literal("Controls · " + profile.name());
		int pw = font.width(heading) + 20;
		HearthDraw.pill(g, width / 2 - pw / 2, 8, pw, 16);
		HearthDraw.labelCentred(g, font, heading, width / 2, 12, HearthDraw.CREAM);
		// The preset strip: LB ‹ name › RB.
		Component preset = Component.literal("‹ " + model.name() + " ›");
		int lb = HearthDraw.glyphWidth(Control.LEFT_BUMPER);
		int rb = HearthDraw.glyphWidth(Control.RIGHT_BUMPER);
		int stripW = lb + rb + font.width(preset) + 24;
		int sx = width / 2 - stripW / 2;
		HearthDraw.pill(g, sx, 28, stripW, 16);
		HearthDraw.glyph(g, Control.LEFT_BUMPER, style(), sx + 4, 30);
		HearthDraw.labelCentred(g, font, preset, width / 2, 32, HearthDraw.CREAM);
		HearthDraw.glyph(g, Control.RIGHT_BUMPER, style(), sx + stripW - 4 - rb, 30);
		// The test strip: what the last button pressed does, or what is waiting for a button.
		int w = columnWidth() * 2 + 8;
		int ty = TOP + ROWS * (ROW_H + ROW_GAP) + 4;
		HearthDraw.parchment(g, width / 2 - w / 2, ty, w, 16);
		int textY = ty + 4;
		if (problem != null) {
			HearthDraw.inkCentred(g, font, Component.literal(problem), width / 2, textY, ERROR_INK);
		} else if (model.listening() != null) {
			HearthDraw.inkCentred(g, font,
					Component.literal("Press the button for " + RemapModel.label(model.listening())), width / 2, textY,
					HearthDraw.INK);
		} else if (tried != null) {
			Component does = Component.literal("does: " + model.actionOf(tried).map(RemapModel::label).orElse("nothing"));
			int gw = HearthDraw.glyphWidth(tried);
			int total = gw + 4 + font.width(does);
			int x = width / 2 - total / 2;
			HearthDraw.glyph(g, tried, style(), x, ty + 2);
			g.text(font, does, x + gw + 4, textY, HearthDraw.INK, false);
		} else {
			HearthDraw.inkCentred(g, font, Component.literal("Press any button to see what it does"), width / 2, textY,
					0xFF7A6A58);
		}
	}

	// ---- For the E2E ----

	@Override
	public int seat() {
		return seat;
	}

	public RemapModel model() {
		return model;
	}

	/** The action on the focused row, or null. */
	public Action focusedAction() {
		return getFocused() instanceof ActionRow row ? row.action : null;
	}
}
