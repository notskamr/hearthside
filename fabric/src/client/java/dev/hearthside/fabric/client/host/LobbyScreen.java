package dev.hearthside.fabric.client.host;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.profile.GuestProfile;
import dev.hearthside.core.profile.ProfileCarousel;
import dev.hearthside.core.profile.QuickSetup;
import dev.hearthside.core.profile.SkinRef;
import dev.hearthside.core.ui.Tween;
import dev.hearthside.fabric.client.mixin.DefaultPlayerSkinInvoker;
import dev.hearthside.fabric.client.ui.EmberLayer;
import dev.hearthside.fabric.client.ui.HearthButton;
import dev.hearthside.fabric.client.ui.HearthDraw;
import dev.hearthside.fabric.client.ui.HintChip;
import dev.hearthside.fabric.client.ui.KeyboardWidget;
import java.time.Duration;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * The co-op party lobby in the Cozy-hearth style (Spec A sections 3.1, 4.1; the approved style draft): a dark plank
 * wall lit by firelight from below with drifting embers, a parchment card per player in a frame of their colour, faint
 * glass cards for empty seats, and button hints along the bottom. A controller presses A to take a card, then picks or
 * makes its profile on it; Y on a ready card picks again; Menu (or Start) goes on to choose a world together.
 */
public final class LobbyScreen extends Screen {
	private static final int SEATS = 4, CARD_GAP = 10, HEADER = 30, FOOTER = 30;
	private static final int MUTED = 0xFF7A6A58, FADED = 0xFFB8A48C, ERROR = 0xFFE05A40;
	private static final Duration FLIP = Duration.ofMillis(250);

	private final Screen parent;
	private final Party party;
	private final EmberLayer embers = new EmberLayer(17, 60);
	private final Tween[] flips = new Tween[SEATS];
	private final boolean[] taken = new boolean[SEATS];
	private final PlayerSkinWidget[] previews = new PlayerSkinWidget[SEATS];
	private HearthButton useController;

	public LobbyScreen(Screen parent, Party party) {
		super(Component.literal("Co-op"));
		this.parent = parent;
		this.party = party;
		for (int i = 0; i < SEATS; i++) {
			flips[i] = new Tween(FLIP);
		}
	}

	private record Card(int x, int y, int w, int h) {
		int centre() {
			return x + w / 2;
		}

		int bottom() {
			return y + h;
		}
	}

	/** Portrait cards, as in the draft, centred between the title and the hints. */
	private Card card(int seat) {
		int w = Math.min(100, (width - 40 - 3 * CARD_GAP) / SEATS);
		int h = Math.max(100, Math.min((int) (w * 1.6), height - HEADER - FOOTER - 8));
		int left = (width - (SEATS * w + 3 * CARD_GAP)) / 2;
		int top = HEADER + Math.max(4, (height - HEADER - FOOTER - h) / 2);
		return new Card(left + seat * (w + CARD_GAP), top, w, h);
	}

	@Override
	protected void init() {
		for (int seat = 0; seat < SEATS; seat++) {
			Card c = card(seat);
			int pw = Math.min(c.w - 20, 50);
			int ph = Math.max(40, Math.min(c.h - 78, 80));
			PlayerSkinWidget preview = new PlayerSkinWidget(pw, ph, minecraft.getEntityModels(), skinFor(seat));
			preview.setPosition(c.centre() - pw / 2, c.y + 20);
			preview.visible = false;
			previews[seat] = addRenderableWidget(preview);
		}
		Card host = card(0);
		useController = addRenderableWidget(new HearthButton(host.x + 4, host.bottom() - 19, host.w - 8, 14,
				Component.literal("+ Controller"), b -> party.useController()));
		// The hints along the bottom, as in the draft; Back and Start are also mouse buttons.
		// Join is for controllers without a seat, so it shows theirs; the rest show whoever drives the screen.
		Supplier<PadStyle> style = party::sharedStyle;
		HintChip[] chips = {
				new HintChip(0, 0, Control.A, party::joinStyle, Component.literal("Join"), b -> { }),
				new HintChip(0, 0, Control.Y, style, Component.literal("Profile"), b -> { }),
				new HintChip(0, 0, Control.B, style, Component.literal("Back"), b -> onClose()),
				new HintChip(0, 0, Control.START, style, Component.literal("Start"), b -> continueToWorlds()),
		};
		int x = width - 8;
		for (int i = chips.length - 1; i >= 0; i--) {
			x -= chips[i].getWidth();
			chips[i].setPosition(x, height - FOOTER + 8);
			x -= 4;
			addRenderableWidget(chips[i]);
		}
	}

	/** On to choosing a world, with the party kept. */
	public void continueToWorlds() {
		minecraft.gui.setScreen(new SelectWorldScreen(this));
	}

	/** Back or Esc: leaving the lobby ends the party, so ask first when anyone has taken a seat (Spec A section 7.1). */
	@Override
	public void onClose() {
		if (party.seatedCount() == 0) {
			endParty();
			return;
		}
		minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				endParty();
			} else {
				minecraft.gui.setScreen(this);
			}
		}, Component.literal("End the party?"),
				Component.literal("Everyone's game will close, and they'll need to join again."),
				Component.literal("End party"), Component.literal("Keep playing")));
	}

	/** Ends the party (every guest's game stops) and goes back. */
	public void endParty() {
		party.disband();
		minecraft.gui.setScreen(parent);
	}

	// ---- What each card shows ----

	/** The skin a card previews: player 1's own, the profile on a guest's carousel, or quick setup's pick. */
	private Supplier<PlayerSkin> skinFor(int seat) {
		if (seat == 0) {
			return minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false);
		}
		return () -> {
			SeatCard card = party.seatCard(seat);
			SkinRef skin = null;
			if (card != null && card.setup() != null) {
				skin = card.setup().skin();
			} else if (card != null && card.carousel().current() instanceof ProfileCarousel.Entry.Profile(GuestProfile p)) {
				skin = p.skin();
			} else if (card == null && party.profileOf(seat) != null) {
				skin = party.profileOf(seat).skin();
			}
			return skin(skin);
		};
	}

	private static PlayerSkin skin(SkinRef ref) {
		// Imported and borrowed skins arrive with Plan 5d; until then they preview as Steve.
		boolean builtIn = ref != null && ref.kind() == SkinRef.Kind.DEFAULT;
		boolean slim = builtIn && ref.slim();
		String texture = "entity/player/" + (slim ? "slim/" : "wide/") + (builtIn ? ref.value() : "steve");
		return DefaultPlayerSkinInvoker.hearthside$create(texture, slim ? PlayerModelType.SLIM : PlayerModelType.WIDE);
	}

	// ---- Drawing ----

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		boolean reduceMotion = party.reduceMotion();
		HearthDraw.board(g, 0, 0, width, height);
		// Firelight from below, flickering gently (steady with reduce motion).
		double t = System.nanoTime() / 1e9;
		float flicker = reduceMotion ? 0.9f : (float) (0.85 + 0.08 * Math.sin(t * 7.3) + 0.05 * Math.sin(t * 13.1 + 1.7));
		HearthDraw.firelightGlow(g, width / 10, height * 35 / 100, width * 8 / 10, height - height * 35 / 100, flicker);
		embers.draw(g, 0, 0, width, height, reduceMotion);
		long now = System.nanoTime();
		for (int seat = 0; seat < SEATS; seat++) {
			boolean isTaken = seat == 0 || party.seatView(seat).taken();
			if (isTaken && !taken[seat]) {
				flips[seat].start(now);
			}
			taken[seat] = isTaken;
			drawCard(g, seat, flips[seat].progress(now, reduceMotion));
		}
	}

	private void drawCard(GuiGraphicsExtractor g, int seat, double flip) {
		Card c = card(seat);
		boolean isTaken = taken[seat];
		g.pose().pushMatrix();
		// Cards flip in when a player takes them: squeezed horizontally about their centre.
		g.pose().translate(c.centre(), 0);
		g.pose().scale((float) Math.max(0.05, flip), 1);
		g.pose().translate(-c.centre(), 0);
		if (isTaken) {
			HearthDraw.frame(g, c.x - 3, c.y - 3, c.w + 6, c.h + 6, cardColour(seat));
			HearthDraw.parchment(g, c.x, c.y, c.w, c.h);
			drawTaken(g, seat, c);
		} else {
			drawEmpty(g, c);
		}
		g.pose().popMatrix();
		previews[seat].visible = isTaken && flip >= 1 && !typing(seat);
		if (seat == 0) {
			useController.visible = !party.hostHasController();
			useController.setMessage(Component.literal(party.hostClaimArmed() ? "Press A on it" : "+ Controller"));
		}
	}

	private void drawEmpty(GuiGraphicsExtractor g, Card c) {
		HearthDraw.ghost(g, c.x, c.y, c.w, c.h);
		int middle = c.y + c.h / 2;
		if (party.freeControllers() > 0) {
			Component join = Component.literal("to join");
			int glyph = HearthDraw.glyphWidth(Control.A);
			int total = glyph + 4 + font.width(join);
			int x = c.centre() - total / 2;
			HearthDraw.glyph(g, Control.A, party.joinStyle(), x, middle - 6);
			HearthDraw.label(g, font, join, x + glyph + 4, middle - 4, HearthDraw.CREAM);
		} else {
			HearthDraw.labelCentred(g, font, Component.literal("Connect a"), c.centre(), middle - 9, FADED);
			HearthDraw.labelCentred(g, font, Component.literal("controller"), c.centre(), middle + 1, FADED);
		}
	}

	private int cardColour(int seat) {
		SeatCard card = seat == 0 ? null : party.seatCard(seat);
		if (card != null && card.setup() != null) {
			return card.setup().color();
		}
		if (card != null && card.carousel().current() instanceof ProfileCarousel.Entry.Profile(GuestProfile p)) {
			return party.displayColor(seat, p.color());
		}
		return party.colorOf(seat);
	}

	private boolean typing(int seat) {
		SeatCard card = seat == 0 ? null : party.seatCard(seat);
		return card != null && card.setup() != null && card.setup().step() == QuickSetup.Step.NAME;
	}

	private void drawTaken(GuiGraphicsExtractor g, int seat, Card c) {
		PadStyle style = party.padStyleOf(seat);
		int colour = cardColour(seat);
		int pillY = c.bottom() - 18;
		if (seat == 0) {
			HearthDraw.inkCentred(g, font, Component.literal(minecraft.getUser().getName()), c.centre(), c.y + 7,
					HearthDraw.INK);
			// The player pill (where they'll be on the split screen); "+ Controller" sits under it until they take one.
			HearthDraw.colourPill(g, font, Component.literal("P1"), c.centre(), party.hostHasController() ? pillY : pillY - 18,
					colour);
			return;
		}
		SeatCard card = party.seatCard(seat);
		if (card == null) {
			Party.SeatView view = party.seatView(seat);
			HearthDraw.inkCentred(g, font, Component.literal(view.name()), c.centre(), c.y + 7, HearthDraw.INK);
			HearthDraw.inkCentred(g, font, Component.literal(view.status()), c.centre(), pillY - 12, MUTED);
			HearthDraw.colourPill(g, font, Component.literal("P" + (seat + 1)), c.centre(), pillY, colour);
			return;
		}
		if (card.setup() == null) {
			// The carousel: the profile to pick, between arrows.
			String name = card.carousel().current() instanceof ProfileCarousel.Entry.Profile(GuestProfile p)
					? p.name() : "+ New";
			HearthDraw.inkCentred(g, font, Component.literal("‹ " + name + " ›"), c.centre(), c.y + 7, HearthDraw.INK);
			hints(g, c, style, Control.A, "Pick", Control.B, card.changing() ? "Keep" : "Leave");
			return;
		}
		QuickSetup setup = card.setup();
		String step = switch (setup.step()) {
			case NAME -> "Name";
			case SKIN -> "‹ Skin ›";
			case COLOR -> "‹ Colour ›";
			case DONE -> "";
		};
		HearthDraw.inkCentred(g, font, Component.literal(step), c.centre(), c.y + 7, HearthDraw.INK);
		if (setup.step() == QuickSetup.Step.COLOR) {
			HearthDraw.colourPill(g, font, Component.literal("  "), c.centre(), c.bottom() - 46, setup.color());
		}
		if (setup.step() != QuickSetup.Step.NAME) {
			hints(g, c, style, Control.A, "Next", Control.B, "Back");
		}
	}

	private void hints(GuiGraphicsExtractor g, Card c, PadStyle style, Control first, String firstLabel, Control second,
			String secondLabel) {
		int y = c.bottom() - 30;
		int x = c.x + 7;
		x += HearthDraw.glyph(g, first, style, x, y) + 3;
		g.text(font, firstLabel, x, y + 2, HearthDraw.INK, false);
		x = c.x + 7;
		x += HearthDraw.glyph(g, second, style, x, y + 13) + 3;
		g.text(font, secondLabel, x, y + 15, HearthDraw.INK, false);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		// The title pill: "Co-op · 2 of 4".
		Component heading = Component.literal("Co-op · " + (party.seatedCount() + 1) + " of " + SEATS);
		int pw = font.width(heading) + 20;
		HearthDraw.pill(g, width / 2 - pw / 2, 8, pw, 16);
		HearthDraw.labelCentred(g, font, heading, width / 2, 12, HearthDraw.CREAM);
		// On-screen keyboards float over the board, under their player's card.
		for (int seat = 1; seat < SEATS; seat++) {
			if (!typing(seat)) {
				continue;
			}
			SeatCard card = party.seatCard(seat);
			Card c = card(seat);
			int x = Math.clamp(c.centre() - KeyboardWidget.WIDTH / 2, 4, Math.max(4, width - KeyboardWidget.WIDTH - 4));
			int y = Math.clamp(c.y + 22, HEADER, Math.max(HEADER, height - FOOTER - KeyboardWidget.HEIGHT));
			new KeyboardWidget(card.keyboard(), card.setup().color()).render(g, font, x, y, party.padStyleOf(seat));
			if (card.nameProblem() != null) {
				HearthDraw.labelCentred(g, font, Component.literal(card.nameProblem()), x + KeyboardWidget.WIDTH / 2, y - 10,
						ERROR);
			}
		}
	}
}
