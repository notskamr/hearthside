package dev.hearthside.fabric.client.host;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.MenuNav;
import dev.hearthside.core.input.NavRepeater;
import dev.hearthside.core.input.PadButton;
import dev.hearthside.core.session.WorldNames;
import dev.hearthside.core.ui.KeyboardModel;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.menu.MenuNavigator;
import dev.hearthside.fabric.client.menu.PadScreen;
import dev.hearthside.fabric.client.ui.HearthButton;
import dev.hearthside.fabric.client.ui.HearthDraw;
import dev.hearthside.fabric.client.ui.HearthTextField;
import dev.hearthside.fabric.client.ui.HintChip;
import dev.hearthside.fabric.client.ui.KeyboardWidget;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FileUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * A new world from a few big choices, made with a controller (Spec A section 3.1): its name (typed, or on the
 * on-screen keyboard), mode, difficulty and cheats; More options… opens Minecraft's full screen. Focus starts on
 * Create, so the quick path is one press.
 */
public final class NewWorldScreen extends Screen implements PadScreen {
	public enum Mode {
		SURVIVAL("Survival"), CREATIVE("Creative"), HARDCORE("Hardcore");

		final String label;

		Mode(String label) {
			this.label = label;
		}
	}

	private static final int ROW_W = 220, ROW_H = 20, ROW_GAP = 6;

	private final Screen parent;
	private final Party party;
	private final Collection<String> takenNames;
	private final Map<Integer, MenuNav> navs = new HashMap<>();
	private String name;
	private Mode mode = Mode.SURVIVAL;
	private Difficulty difficulty = Difficulty.NORMAL;
	private boolean cheats;
	private EditBox nameBox;
	private HearthButton modeRow, difficultyRow, cheatsRow, createButton;
	/** The on-screen keyboard while a controller player names the world, and whose controller it is. */
	private KeyboardModel keyboard;
	private int keyboardPlayer;
	private int keyboardButtons = -1;
	private final NavRepeater keysX = new NavRepeater(Duration.ofMillis(300), Duration.ofMillis(80));
	private final NavRepeater keysY = new NavRepeater(Duration.ofMillis(300), Duration.ofMillis(80));

	/** @param takenNames the names of existing worlds (the default name avoids them) */
	public NewWorldScreen(Screen parent, Party party, Collection<String> takenNames) {
		super(Component.literal("New world"));
		this.parent = parent;
		this.party = party;
		this.takenNames = List.copyOf(takenNames);
		this.name = WorldNames.unique(WorldNames.DEFAULT, takenNames);
	}

	@Override
	protected void init() {
		int x = (width - ROW_W) / 2;
		int y = 44;
		nameBox = addRenderableWidget(new HearthTextField(font, x, y, ROW_W, ROW_H, Component.literal("World name")));
		nameBox.setMaxLength(32);
		nameBox.setValue(name);
		nameBox.setResponder(value -> name = value);
		y += ROW_H + ROW_GAP;
		modeRow = addRenderableWidget(new HearthButton(x, y, ROW_W, ROW_H, Component.empty(), b -> cycleMode(1)));
		y += ROW_H + ROW_GAP;
		difficultyRow = addRenderableWidget(new HearthButton(x, y, ROW_W, ROW_H, Component.empty(), b -> cycleDifficulty(1)));
		y += ROW_H + ROW_GAP;
		cheatsRow = addRenderableWidget(new HearthButton(x, y, ROW_W, ROW_H, Component.empty(), b -> cheats = !cheats));
		y += ROW_H + ROW_GAP + 6;
		createButton = addRenderableWidget(new HearthButton(x, y, ROW_W, ROW_H + 4, Component.literal("Create"),
				b -> create()));
		y += ROW_H + 4 + ROW_GAP;
		addRenderableWidget(new HearthButton(x, y, ROW_W, ROW_H, Component.literal("More options…"),
				b -> CreateWorldScreen.openFresh(minecraft, () -> minecraft.gui.setScreen(this))));
		HintChip[] chips = {
				new HintChip(0, 0, Control.A, party::sharedStyle, Component.literal("Select"), b -> { }),
				new HintChip(0, 0, Control.B, party::sharedStyle, Component.literal("Back"), b -> onClose()),
		};
		int cx = width - 8;
		for (int i = chips.length - 1; i >= 0; i--) {
			cx -= chips[i].getWidth();
			chips[i].setPosition(cx, height - 22);
			cx -= 4;
			addRenderableWidget(chips[i]);
		}
		refreshLabels();
	}

	/** Create, so the quick path is one press (vanilla's would tab to the name box after controller input). */
	@Override
	protected void setInitialFocus() {
		setInitialFocus(createButton);
	}

	private void refreshLabels() {
		modeRow.setMessage(Component.literal("Mode:  ‹ " + mode.label + " ›"));
		Difficulty shown = mode == Mode.HARDCORE ? Difficulty.HARD : difficulty;
		difficultyRow.setMessage(Component.literal("Difficulty:  ‹ " + shown.getDisplayName().getString() + " ›"));
		difficultyRow.active = mode != Mode.HARDCORE;
		cheatsRow.setMessage(Component.literal("Cheats:  " + (cheats && mode != Mode.HARDCORE ? "On" : "Off")));
		cheatsRow.active = mode != Mode.HARDCORE;
	}

	private void cycleMode(int step) {
		mode = Mode.values()[Math.floorMod(mode.ordinal() + step, Mode.values().length)];
		refreshLabels();
	}

	private void cycleDifficulty(int step) {
		difficulty = Difficulty.values()[Math.floorMod(difficulty.ordinal() + step, Difficulty.values().length)];
		refreshLabels();
	}

	/** Creates and opens the world. */
	public void create() {
		String levelName = WorldNames.unique(name, List.of());
		try {
			String id = FileUtil.findAvailableName(minecraft.getLevelSource().getBaseDir(), levelName, "");
			boolean hardcore = mode == Mode.HARDCORE;
			GameType type = mode == Mode.CREATIVE ? GameType.CREATIVE : GameType.SURVIVAL;
			LevelSettings settings = new LevelSettings(levelName, type,
					new LevelSettings.DifficultySettings(hardcore ? Difficulty.HARD : difficulty, hardcore, false),
					cheats && !hardcore, WorldDataConfiguration.DEFAULT);
			minecraft.createWorldOpenFlows().createFreshLevel(id, settings, WorldOptions.defaultWithRandomSeed(),
					WorldPresets::createNormalWorldDimensions, this);
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not create the world {}", levelName, e);
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean padInput(int player, InputFrame frame, long nanos) {
		if (keyboard != null) {
			if (player == keyboardPlayer) {
				type(frame, nanos);
			}
			return true;
		}
		boolean used = false;
		for (MenuNav.Action action : navs.computeIfAbsent(player, p -> new MenuNav()).step(frame, nanos)) {
			if (minecraft.gui.screen() != this) {
				return true;
			}
			used = true;
			var focused = getFocused();
			if ((action == MenuNav.Action.LEFT || action == MenuNav.Action.RIGHT)
					&& (focused == modeRow || focused == difficultyRow || focused == cheatsRow)) {
				int step = action == MenuNav.Action.RIGHT ? 1 : -1;
				if (focused == modeRow) {
					cycleMode(step);
				} else if (focused == difficultyRow && difficultyRow.active) {
					cycleDifficulty(step);
				} else if (cheatsRow.active) {
					cheats = !cheats;
					refreshLabels();
				}
			} else if (action == MenuNav.Action.ACCEPT && focused == nameBox) {
				keyboard = KeyboardModel.forText(name, 32);
				keyboardPlayer = player;
				keyboardButtons = -1;
			} else {
				MenuNavigator.apply(minecraft, this, action, party.colorOf(player));
			}
		}
		return used;
	}

	/** The on-screen keyboard: A types, X deletes, Y adds a space, the bumpers switch layer, Menu keeps, B cancels. */
	private void type(InputFrame frame, long nanos) {
		int pressed = keyboardButtons == -1 ? 0 : frame.buttons() & ~keyboardButtons;
		keyboardButtons = frame.buttons();
		int dx = direction(frame, PadButton.DPAD_LEFT, PadButton.DPAD_RIGHT, frame.leftX());
		int dy = direction(frame, PadButton.DPAD_UP, PadButton.DPAD_DOWN, frame.leftY());
		int moveX = keysX.step(dx, nanos) > 0 ? dx : 0;
		int moveY = keysY.step(dy, nanos) > 0 ? dy : 0;
		if (moveX != 0 || moveY != 0) {
			keyboard.move(moveX, moveY);
		}
		if (down(pressed, PadButton.A)) {
			keyboard.type();
		}
		if (down(pressed, PadButton.X)) {
			keyboard.delete();
		}
		if (down(pressed, PadButton.Y)) {
			keyboard.space();
		}
		if (down(pressed, PadButton.LEFT_BUMPER)) {
			keyboard.previousLayer();
		}
		if (down(pressed, PadButton.RIGHT_BUMPER)) {
			keyboard.nextLayer();
		}
		if (down(pressed, PadButton.START)) {
			name = keyboard.text();
			nameBox.setValue(name);
			keyboard = null;
		} else if (down(pressed, PadButton.B)) {
			keyboard = null;
		}
	}

	private static int direction(InputFrame frame, PadButton negative, PadButton positive, float axis) {
		if (frame.pressed(negative) || axis < -0.5f) {
			return -1;
		}
		return frame.pressed(positive) || axis > 0.5f ? 1 : 0;
	}

	private static boolean down(int pressed, PadButton button) {
		return (pressed & InputFrame.buttons(button)) != 0;
	}

	/** For the E2E. */
	public String worldName() {
		return name;
	}

	public Mode mode() {
		return mode;
	}

	public boolean typing() {
		return keyboard != null;
	}

	/** Which row has focus: "name", "mode", "difficulty", "cheats", "create" or "". For the E2E. */
	public String focusedRow() {
		var focused = getFocused();
		return focused == nameBox ? "name" : focused == modeRow ? "mode" : focused == difficultyRow ? "difficulty"
				: focused == cheatsRow ? "cheats" : focused == createButton ? "create" : "";
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		HearthDraw.board(g, 0, 0, width, height);
		HearthDraw.firelightGlow(g, width / 10, height * 35 / 100, width * 8 / 10, height - height * 35 / 100, 0.85f);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		Component heading = Component.literal("New world");
		int pw = font.width(heading) + 20;
		HearthDraw.pill(g, width / 2 - pw / 2, 8, pw, 16);
		HearthDraw.labelCentred(g, font, heading, width / 2, 12, HearthDraw.CREAM);
		HearthDraw.labelCentred(g, font, Component.literal("Name"), nameBox.getX() - 20, nameBox.getY() + 6,
				HearthDraw.CREAM);
		if (keyboard != null) {
			int w = Math.min(230, width - 20);
			new KeyboardWidget(keyboard, party.colorOf(keyboardPlayer)).render(g, font, (width - w) / 2,
					nameBox.getY() + ROW_H + 4, w, party.padStyleOf(keyboardPlayer));
		}
	}
}
