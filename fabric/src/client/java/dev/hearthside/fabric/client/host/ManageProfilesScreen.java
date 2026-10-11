package dev.hearthside.fabric.client.host;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.MenuNav;
import dev.hearthside.core.input.PadButton;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.profile.GuestProfile;
import dev.hearthside.core.ui.HoldToConfirm;
import dev.hearthside.fabric.client.menu.SeatScreen;
import dev.hearthside.fabric.client.ui.HearthDraw;
import dev.hearthside.fabric.client.ui.HintChip;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The saved guest profiles (Spec A section 5.5), one parchment card each with its skin, name and colour, driven by
 * the seat that opened it: left and right move along them, holding Y for 1.5 s deletes the focused one (a bar fills on
 * its card; letting go early cancels), and a profile someone is playing as can't be deleted. B goes back.
 */
public final class ManageProfilesScreen extends Screen implements SeatScreen {
	private static final int CARD_W = 80, CARD_H = 118, GAP = 10, TOP = 40;
	private static final Duration HOLD = Duration.ofMillis(1500);
	private static final int MUTED = 0xFF7A6A58, ERROR_INK = 0xFFA8321E, BAR = 0xFFC8452A, BAR_BACK = 0xFFD9C29A;
	/** The focused card is lifted this much and ringed in ember light (frames already use the players' colours). */
	private static final int LIFT = 4, EMBER = 0xFFFFC27A;

	private final Screen parent;
	private final Party party;
	private final int seat;
	private final MenuNav nav = new MenuNav();
	private final List<PlayerSkinWidget> previews = new ArrayList<>();
	private List<GuestProfile> profiles = List.of();
	private int focus;
	private HoldToConfirm hold = new HoldToConfirm(HOLD);
	private double progress;
	/** After a delete, Y must be let go before the next hold counts. */
	private boolean releaseFirst = true;
	private String problem;

	public ManageProfilesScreen(Screen parent, Party party, int seat) {
		super(Component.literal("Manage profiles"));
		this.parent = parent;
		this.party = party;
		this.seat = seat;
	}

	@Override
	protected void init() {
		profiles = party.savedProfiles();
		focus = Math.clamp(focus, 0, Math.max(0, profiles.size() - 1));
		previews.clear();
		for (GuestProfile profile : profiles) {
			PlayerSkinWidget preview = new PlayerSkinWidget(44, 60, minecraft.getEntityModels(),
					() -> LobbyScreen.skin(profile.skin()));
			preview.visible = false;
			previews.add(addRenderableWidget(preview));
		}
		HintChip[] chips = {
				new HintChip(0, 0, Control.Y, this::style, Component.literal("Hold to delete"), b -> { }),
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

	/** No widget focus: the cards are moved along with the controller. */
	@Override
	protected void setInitialFocus() {
	}

	private PadStyle style() {
		return party.padStyleOf(seat);
	}

	@Override
	public int seat() {
		return seat;
	}

	@Override
	public void onClose() {
		party.backToCard(seat);
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean padInput(int player, InputFrame frame, long nanos) {
		if (player != seat) {
			return false;
		}
		for (MenuNav.Action action : nav.step(frame, nanos)) {
			switch (action) {
				case LEFT -> move(-1);
				case RIGHT -> move(1);
				case BACK -> {
					onClose();
					return true;
				}
				default -> { }
			}
		}
		boolean y = frame.pressed(PadButton.Y);
		if (!y) {
			releaseFirst = false;
		}
		GuestProfile focused = focused();
		if (focused == null || releaseFirst) {
			progress = hold.step(false, nanos);
			return true;
		}
		if (y && party.profileInUse(focused.id())) {
			problem = focused.name() + " is playing — leave the seat first";
			progress = hold.step(false, nanos);
			releaseFirst = true;
			return true;
		}
		progress = hold.step(y, nanos);
		if (hold.done()) {
			if (party.deleteProfile(focused.id())) {
				problem = null;
				hold = new HoldToConfirm(HOLD);
				progress = 0;
				releaseFirst = true;
				rebuildWidgets();
			} else {
				problem = "Couldn't delete " + focused.name();
				hold = new HoldToConfirm(HOLD);
				releaseFirst = true;
			}
		}
		return true;
	}

	private void move(int step) {
		if (profiles.isEmpty()) {
			return;
		}
		focus = Math.clamp(focus + step, 0, profiles.size() - 1);
		problem = null;
		hold = new HoldToConfirm(HOLD);
		progress = 0;
		// A Y held while moving was meant for the card before: deleting this one takes a fresh press.
		releaseFirst = true;
	}

	private GuestProfile focused() {
		return profiles.isEmpty() ? null : profiles.get(focus);
	}

	/** The first card shown: the row scrolls to keep the focused card in view. */
	private int firstShown() {
		int fits = Math.max(1, (width - 40 + GAP) / (CARD_W + GAP));
		return Math.clamp(focus - fits / 2, 0, Math.max(0, profiles.size() - fits));
	}

	private int shownCount() {
		int fits = Math.max(1, (width - 40 + GAP) / (CARD_W + GAP));
		return Math.min(fits, profiles.size());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		HearthDraw.board(g, 0, 0, width, height);
		HearthDraw.firelightGlow(g, width / 10, height * 35 / 100, width * 8 / 10, height - height * 35 / 100, 0.85f);
		int first = firstShown();
		int count = shownCount();
		int left = width / 2 - (count * CARD_W + (count - 1) * GAP) / 2;
		for (int i = 0; i < previews.size(); i++) {
			boolean shown = i >= first && i < first + count;
			previews.get(i).visible = shown;
			if (shown) {
				int x = left + (i - first) * (CARD_W + GAP);
				int top = i == focus ? TOP - LIFT : TOP;
				drawCard(g, profiles.get(i), x, top, i == focus);
				previews.get(i).setPosition(x + (CARD_W - 44) / 2, top + 20);
			}
		}
	}

	private void drawCard(GuiGraphicsExtractor g, GuestProfile profile, int x, int top, boolean focused) {
		if (focused) {
			g.outline(x - 6, top - 6, CARD_W + 12, CARD_H + 12, EMBER);
			g.outline(x - 5, top - 5, CARD_W + 10, CARD_H + 10, EMBER);
		}
		HearthDraw.frame(g, x - 3, top - 3, CARD_W + 6, CARD_H + 6, profile.color());
		HearthDraw.parchment(g, x, top, CARD_W, CARD_H);
		String name = font.plainSubstrByWidth(profile.name(), CARD_W - 8);
		HearthDraw.inkCentred(g, font, Component.literal(name), x + CARD_W / 2, top + 7, HearthDraw.INK);
		int y = top + CARD_H - 22;
		if (party.profileInUse(profile.id())) {
			HearthDraw.inkCentred(g, font, Component.literal("In use"), x + CARD_W / 2, y, MUTED);
		} else if (focused && progress > 0) {
			// The hold: a bar filling across the card's foot.
			int bw = CARD_W - 16;
			g.fill(x + 8, y + 1, x + 8 + bw, y + 7, BAR_BACK);
			g.fill(x + 8, y + 1, x + 8 + (int) (bw * progress), y + 7, BAR);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		Component heading = Component.literal("Profiles · " + profiles.size());
		int pw = font.width(heading) + 20;
		HearthDraw.pill(g, width / 2 - pw / 2, 8, pw, 16);
		HearthDraw.labelCentred(g, font, heading, width / 2, 12, HearthDraw.CREAM);
		if (profiles.isEmpty()) {
			HearthDraw.labelCentred(g, font, Component.literal("No saved profiles yet"), width / 2, TOP + 40,
					HearthDraw.CREAM);
		}
		int first = firstShown();
		if (first > 0) {
			HearthDraw.labelCentred(g, font, Component.literal("‹"), 12, TOP + CARD_H / 2, HearthDraw.CREAM);
		}
		if (first + shownCount() < profiles.size()) {
			HearthDraw.labelCentred(g, font, Component.literal("›"), width - 12, TOP + CARD_H / 2, HearthDraw.CREAM);
		}
		if (problem != null) {
			int w = font.width(problem) + 16;
			HearthDraw.parchment(g, width / 2 - w / 2, TOP + CARD_H + 10, w, 16);
			HearthDraw.inkCentred(g, font, Component.literal(problem), width / 2, TOP + CARD_H + 14, ERROR_INK);
		}
	}

	// ---- For the E2E ----

	/** The focused profile's name, or null. */
	public String focusedName() {
		GuestProfile focused = focused();
		return focused == null ? null : focused.name();
	}

	public List<String> names() {
		return profiles.stream().map(GuestProfile::name).toList();
	}

	public String problem() {
		return problem;
	}

	public double progress() {
		return progress;
	}
}
