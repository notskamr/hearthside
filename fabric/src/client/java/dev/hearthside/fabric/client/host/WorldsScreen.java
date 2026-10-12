package dev.hearthside.fabric.client.host;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.MenuNav;
import dev.hearthside.core.session.CoopWorld;
import dev.hearthside.core.session.CoopWorlds;
import dev.hearthside.core.session.CoopWorldsStore;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.menu.MenuNavigator;
import dev.hearthside.fabric.client.menu.PadScreen;
import dev.hearthside.fabric.client.ui.EmberLayer;
import dev.hearthside.fabric.client.ui.HearthDraw;
import dev.hearthside.fabric.client.ui.HearthSounds;
import dev.hearthside.fabric.client.ui.HintChip;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * Choosing a world together (Spec A section 3.1; the user's "last co-op world first"): a parchment card per world,
 * the last world played in co-op first and selected, each saying who last played it; Create makes a new one. Anyone
 * in the party can drive it; X (More…) opens Minecraft's own world list for editing and deleting.
 */
public final class WorldsScreen extends Screen implements PadScreen {
	private static final int CARD_H = 34, CREATE_H = 22, GAP = 5, TOP = 34, BOTTOM = 32;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
			.withZone(ZoneId.systemDefault());

	private final Screen parent;
	private final Party party;
	private final EmberLayer embers = new EmberLayer(41, 40);
	private final Map<Integer, MenuNav> navs = new HashMap<>();
	private List<LevelSummary> worlds;
	private List<CoopWorld> coop = List.of();
	private final List<WorldCard> cards = new ArrayList<>();
	private boolean loading;
	/** The selected card's index: focus moves by index, since cards scrolled out of view can't take focus. */
	private int selected;
	/** Set while {@link #init()} runs: a list that finishes loading at once must not rebuild the screen inside it. */
	private boolean initializing;
	private boolean shownBefore;
	/** E2E only: list worlds from here instead of Minecraft's saves folder (an empty one, for "no worlds yet"). */
	public static volatile LevelStorageSource e2eSource;

	public WorldsScreen(Screen parent, Party party) {
		super(Component.literal("Choose a world"));
		this.parent = parent;
		this.party = party;
	}

	/** The co-op worlds record ({@code .minecraft/hearthside/coop-worlds.json}). */
	static CoopWorldsStore coopStore(Path gameDir) {
		return new CoopWorldsStore(gameDir.resolve("hearthside").resolve("coop-worlds.json"));
	}

	/** Shown again (back from More…, New World or a world that didn't open): list the worlds afresh. */
	@Override
	public void added() {
		if (shownBefore) {
			worlds = null;
		}
		shownBefore = true;
		super.added();
	}

	@Override
	protected void init() {
		initializing = true;
		try {
			build();
		} finally {
			initializing = false;
		}
	}

	private void build() {
		if (worlds == null) {
			load();
		}
		cards.clear();
		int w = Math.min(320, width - 60);
		int x = (width - w) / 2;
		List<String> ids = worlds == null ? List.of() : worlds.stream().map(LevelSummary::getLevelId).toList();
		for (String id : CoopWorlds.order(ids, coop)) {
			LevelSummary summary = worlds.stream().filter(s -> s.getLevelId().equals(id)).findFirst().orElseThrow();
			cards.add(addRenderableWidget(new WorldCard(x, w, summary, b -> open(summary))));
		}
		// Create comes last (first, and focused, when there are no worlds yet).
		cards.add(addRenderableWidget(new WorldCard(x, w, null, b -> create())));
		HintChip[] chips = {
				new HintChip(0, 0, Control.A, party::sharedStyle, Component.literal("Open"), b -> pressFocused()),
				new HintChip(0, 0, Control.X, party::sharedStyle, Component.literal("More…"), b -> more()),
				new HintChip(0, 0, Control.B, party::sharedStyle, Component.literal("Back"), b -> onClose()),
		};
		int cx = width - 8;
		for (int i = chips.length - 1; i >= 0; i--) {
			cx -= chips[i].getWidth();
			chips[i].setPosition(cx, height - BOTTOM + 10);
			cx -= 4;
			addRenderableWidget(chips[i]);
		}
	}

	/**
	 * The first card. (Vanilla's own initial focus runs after {@link #init()} and, after keyboard or controller input,
	 * tabs to the first widget after whatever is focused, which would skip the card.)
	 */
	@Override
	protected void setInitialFocus() {
		if (!loading && !cards.isEmpty()) {
			selected = Math.clamp(selected, 0, cards.size() - 1);
			setFocused(cards.get(selected));
		}
	}

	@Override
	public void setFocused(GuiEventListener listener) {
		super.setFocused(listener);
		if (listener instanceof WorldCard card && cards.contains(card)) {
			selected = cards.indexOf(card);
		}
	}

	/** Up and down (arrow keys, and controllers through them) move along the cards by index, scrolling the list. */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isUp() || event.isDown()) {
			move(event.isUp() ? -1 : 1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (scrollY != 0) {
			move(scrollY > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private void move(int step) {
		if (cards.isEmpty()) {
			return;
		}
		selected = Math.clamp(selected + step, 0, cards.size() - 1);
		setFocused(cards.get(selected));
		layOut();
	}

	private void load() {
		loading = true;
		try {
			coop = coopStore(minecraft.gameDirectory.toPath()).load();
		} catch (IOException e) {
			HearthsideMod.LOGGER.warn("Could not read the co-op worlds record", e);
		}
		try {
			var source = e2eSource != null ? e2eSource : minecraft.getLevelSource();
			source.loadLevelSummaries(source.findLevelCandidates()).whenComplete((list, error) -> minecraft.execute(() -> {
				worlds = error == null ? list.stream().filter(s -> !s.isLocked()).toList() : List.of();
				loading = false;
				if (!initializing) {
					rebuildWidgets();
				}
			}));
		} catch (Exception e) {
			HearthsideMod.LOGGER.error("Could not list worlds", e);
			worlds = List.of();
			loading = false;
		}
	}

	private void open(LevelSummary summary) {
		minecraft.createWorldOpenFlows().openWorld(summary.getLevelId(), () -> minecraft.gui.setScreen(this));
	}

	private void create() {
		List<String> names = worlds == null ? List.of() : worlds.stream().map(LevelSummary::getLevelName).toList();
		minecraft.gui.setScreen(new NewWorldScreen(this, party, names));
	}

	private void more() {
		minecraft.gui.setScreen(new SelectWorldScreen(this));
	}

	private void pressFocused() {
		if (!cards.isEmpty()) {
			cards.get(Math.clamp(selected, 0, cards.size() - 1)).onPress(new KeyEvent(0, 0, 0));
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean padInput(int player, InputFrame frame, long nanos) {
		boolean used = false;
		for (MenuNav.Action action : navs.computeIfAbsent(player, p -> new MenuNav()).step(frame, nanos)) {
			if (minecraft.gui.screen() != this) {
				return true;
			}
			used = true;
			if (action == MenuNav.Action.MORE) {
				more();
			} else {
				MenuNavigator.apply(minecraft, this, action, party.colorOf(player));
			}
		}
		return used;
	}

	/** The world on the focused card ("" for Create, null when nothing is focused). For the E2E. */
	public String focusedLevelId() {
		return getFocused() instanceof WorldCard card ? (card.summary == null ? "" : card.summary.getLevelId()) : null;
	}

	/** What a world's card says about who last played it. For the E2E. */
	public String playedWithText(String levelId) {
		return cards.stream().filter(c -> c.summary != null && c.summary.getLevelId().equals(levelId)).findFirst()
				.map(c -> c.playedWith).orElse(null);
	}

	/** The colour a world's card last drew its "Last played with …" line in (0 before it drew one). For the E2E. */
	public int playedWithColour(String levelId) {
		return cards.stream().filter(c -> c.summary != null && c.summary.getLevelId().equals(levelId)).findFirst()
				.map(c -> c.playedWithDrawn).orElse(0);
	}

	/** The cards' worlds top to bottom ("" for Create). For the E2E. */
	public List<String> cardLevelIds() {
		return cards.stream().map(c -> c.summary == null ? "" : c.summary.getLevelId()).toList();
	}

	public boolean loaded() {
		return !loading;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		HearthDraw.board(g, 0, 0, width, height);
		HearthDraw.firelightGlow(g, width / 10, height * 35 / 100, width * 8 / 10, height - height * 35 / 100, 0.85f);
		embers.draw(g, 0, 0, width, height, party.reduceMotion());
		layOut();
	}

	/** Stacks the cards, scrolled so the focused one is on screen. */
	private void layOut() {
		if (cards.isEmpty()) {
			return;
		}
		int focused = Math.clamp(selected, 0, cards.size() - 1);
		int area = height - TOP - BOTTOM;
		int y = TOP;
		int focusTop = TOP;
		for (int i = 0; i < cards.size(); i++) {
			if (i == focused) {
				focusTop = y;
			}
			y += cards.get(i).getHeight() + GAP;
		}
		int scroll = Math.max(0, focusTop + cards.get(focused).getHeight() - (TOP + area));
		y = TOP - scroll;
		for (WorldCard card : cards) {
			card.setY(y);
			card.visible = y >= TOP - 1 && y + card.getHeight() <= TOP + area + 1;
			y += card.getHeight() + GAP;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		Component heading = Component.literal(loading ? "Finding worlds…" : "Choose a world");
		int pw = font.width(heading) + 20;
		HearthDraw.pill(g, width / 2 - pw / 2, 8, pw, 16);
		HearthDraw.labelCentred(g, font, heading, width / 2, 12, HearthDraw.CREAM);
	}

	/** A world on parchment, or "+ Create a new world". */
	private final class WorldCard extends Button {
		final LevelSummary summary;
		final String playedWith;
		int playedWithDrawn;

		WorldCard(int x, int width, LevelSummary summary, OnPress onPress) {
			super(x, 0, width, summary == null ? CREATE_H : CARD_H,
					Component.literal(summary == null ? "Create a new world" : summary.getLevelName()), onPress,
					DEFAULT_NARRATION);
			this.summary = summary;
			String with = "";
			if (summary != null) {
				for (CoopWorld world : coop) {
					if (world.levelId().equals(summary.getLevelId())) {
						with = CoopWorlds.playedWith(world, minecraft.getUser().getName());
					}
				}
			}
			this.playedWith = with;
		}

		/** Hearthside's knock, not vanilla's click. */
		@Override
		public void playDownSound(SoundManager soundManager) {
			HearthSounds.play(HearthSounds.Ui.KNOCK);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			// One selected card: the focused one; a card merely under the pointer gets a warmer frame, not the glow.
			boolean lit = HearthDraw.selected(this);
			HearthDraw.frame(g, getX() - 2, getY() - 2, getWidth() + 4, getHeight() + 4,
					lit ? 0xFF9A4A : isHovered() ? 0xA86434 : 0x6A4020);
			HearthDraw.parchment(g, getX(), getY(), getWidth(), getHeight());
			if (summary == null) {
				HearthDraw.inkCentred(g, font, Component.literal("+ Create a new world"), getX() + getWidth() / 2,
						getY() + (getHeight() - 8) / 2, HearthDraw.INK);
				return;
			}
			int x = getX() + 8;
			g.text(font, getMessage(), x, getY() + 5, HearthDraw.INK, false);
			String mode = summary.isHardcore() ? "Hardcore" : summary.getGameMode().getShortDisplayName().getString();
			String detail = mode + " · " + DATE.format(Instant.ofEpochMilli(summary.getLastPlayed()));
			g.text(font, detail, getX() + getWidth() - 8 - font.width(detail), getY() + 5, HearthDraw.MUTED, false);
			if (!playedWith.isEmpty()) {
				// Secondary, in the warm faded ink (red read as an error).
				playedWithDrawn = HearthDraw.MUTED;
				g.text(font, playedWith, x, getY() + 19, playedWithDrawn, false);
			}
		}
	}
}
