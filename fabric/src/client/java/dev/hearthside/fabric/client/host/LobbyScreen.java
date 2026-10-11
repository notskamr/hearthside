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
 * The co-op party lobby in the Cozy-hearth style (Spec A sections 3.1, 4.1): a wooden board lit by firelight with
 * drifting embers, and a parchment card per player in a frame of their colour. A controller presses A to take a card,
 * then picks or makes its profile on it (the carousel, quick setup and on-screen keyboard); Continue, or Menu on any
 * ready controller, goes on to pick a world together.
 */
public final class LobbyScreen extends Screen {
	private static final int SEATS = 4, CARD_GAP = 8, TOP = 44, BOTTOM_ROOM = 40;
	private static final int GREY = 0x6A5B4D, MUTED = 0xFF7A6A58, ERROR = 0xFFB0302A;
	private static final Duration FLIP = Duration.ofMillis(250);

	private final Screen parent;
	private final Party party;
	private final EmberLayer embers = new EmberLayer(17, 40);
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
	}

	private Card card(int seat) {
		int w = Math.min(120, (width - 24 - 3 * CARD_GAP) / SEATS);
		int left = (width - (SEATS * w + 3 * CARD_GAP)) / 2;
		int h = Math.max(96, height - TOP - BOTTOM_ROOM);
		return new Card(left + seat * (w + CARD_GAP), TOP, w, h);
	}

	@Override
	protected void init() {
		for (int seat = 0; seat < SEATS; seat++) {
			Card c = card(seat);
			int pw = Math.min(c.w - 16, 56);
			int ph = Math.min(c.h - 66, 84);
			PlayerSkinWidget preview = new PlayerSkinWidget(pw, ph, minecraft.getEntityModels(), skinFor(seat));
			preview.setPosition(c.centre() - pw / 2, c.y + 22);
			preview.visible = false;
			previews[seat] = addRenderableWidget(preview);
		}
		Card host = card(0);
		useController = addRenderableWidget(new HearthButton(host.x + 4, host.y + host.h - 22, host.w - 8, 16,
				Component.literal("Use a controller"), b -> party.useController()));
		addRenderableWidget(new HearthButton(width / 2 - 155, height - 30, 150, 20, Component.literal("Continue"),
				b -> continueToWorlds())).withGlyph(Control.START, () -> party.padStyleOf(-1));
		addRenderableWidget(new HearthButton(width / 2 + 5, height - 30, 150, 20, Component.literal("Back"),
				b -> onClose()));
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
		HearthDraw.wood(g, 0, 0, width, height);
		HearthDraw.firelight(g, 0, 0, width, height);
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
		int colour = isTaken ? cardColour(seat) : GREY;
		HearthDraw.frame(g, c.x - 3, c.y - 3, c.w + 6, c.h + 6, colour);
		HearthDraw.parchment(g, c.x, c.y, c.w, c.h);
		if (isTaken) {
			drawTaken(g, seat, c);
		} else {
			int glyph = HearthDraw.glyphWidth(Control.A);
			g.centeredText(font, Component.literal("Player " + (seat + 1)), c.centre(), c.y + 10, MUTED);
			HearthDraw.glyph(g, Control.A, PadStyle.XBOX, c.centre() - glyph / 2, c.y + c.h / 2 - 14);
			g.centeredText(font, Component.literal("to join"), c.centre(), c.y + c.h / 2 + 2, MUTED);
		}
		g.pose().popMatrix();
		previews[seat].visible = isTaken && flip >= 1 && !typing(seat);
		if (seat == 0) {
			useController.visible = !party.hostHasController();
			useController.setMessage(Component.literal(party.hostClaimArmed() ? "Press A on it…" : "Use a controller"));
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
		int statusY = c.y + c.h - 30;
		if (seat == 0) {
			g.centeredText(font, Component.literal(minecraft.getUser().getName()), c.centre(), c.y + 8, HearthDraw.INK);
			if (party.hostHasController()) {
				int w = HearthDraw.glyphWidth(Control.START);
				HearthDraw.glyph(g, Control.START, style, c.centre() - w / 2, statusY - 4);
				g.centeredText(font, Component.literal("Controller"), c.centre(), statusY + 9, MUTED);
			} else {
				String label = font.width("Keyboard & mouse") <= c.w - 8 ? "Keyboard & mouse" : "Keyboard";
				g.centeredText(font, Component.literal(label), c.centre(), statusY - 4, MUTED);
			}
			return;
		}
		SeatCard card = party.seatCard(seat);
		if (card == null) {
			Party.SeatView view = party.seatView(seat);
			g.centeredText(font, Component.literal(view.name()), c.centre(), c.y + 8, HearthDraw.INK);
			g.centeredText(font, Component.literal(view.status()), c.centre(), statusY, MUTED);
			return;
		}
		if (card.setup() == null) {
			// The carousel: the profile to pick, with arrows.
			String name = card.carousel().current() instanceof ProfileCarousel.Entry.Profile(GuestProfile p)
					? p.name() : "+ New";
			g.centeredText(font, Component.literal("< " + name + " >"), c.centre(), c.y + 8, HearthDraw.INK);
			hints(g, c, style, statusY, Control.A, "Pick", Control.B, "Leave");
			return;
		}
		QuickSetup setup = card.setup();
		String step = switch (setup.step()) {
			case NAME -> "Name";
			case SKIN -> "< Skin >";
			case COLOR -> "< Colour >";
			case DONE -> "";
		};
		g.centeredText(font, Component.literal(step), c.centre(), c.y + 8, HearthDraw.INK);
		if (setup.step() == QuickSetup.Step.COLOR) {
			g.fill(c.centre() - 12, statusY - 16, c.centre() + 12, statusY - 4, 0xFF000000 | setup.color());
		}
		if (setup.step() != QuickSetup.Step.NAME) {
			hints(g, c, style, statusY, Control.A, "Next", Control.B, "Back");
		}
	}

	private void hints(GuiGraphicsExtractor g, Card c, PadStyle style, int y, Control first, String firstLabel,
			Control second, String secondLabel) {
		int x = c.x + 6;
		x += HearthDraw.glyph(g, first, style, x, y) + 2;
		g.text(font, firstLabel, x, y + 2, HearthDraw.INK, false);
		x = c.x + 6;
		x += HearthDraw.glyph(g, second, style, x, y + 13) + 2;
		g.text(font, secondLabel, x, y + 15, HearthDraw.INK, false);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		// The sign over the board.
		HearthDraw.wood(g, width / 2 - 60, 6, 120, 32);
		HearthDraw.heading(g, font, title, width / 2, 14, HearthDraw.CREAM);
		// On-screen keyboards float over the bottom of the board, under their player's card.
		for (int seat = 1; seat < SEATS; seat++) {
			if (!typing(seat)) {
				continue;
			}
			SeatCard card = party.seatCard(seat);
			Card c = card(seat);
			int x = Math.clamp(c.centre() - KeyboardWidget.WIDTH / 2, 4, Math.max(4, width - KeyboardWidget.WIDTH - 4));
			int y = Math.max(TOP + 20, height - BOTTOM_ROOM - KeyboardWidget.HEIGHT + 4);
			new KeyboardWidget(card.keyboard(), card.setup().color()).render(g, font, x, y, party.padStyleOf(seat));
			if (card.nameProblem() != null) {
				g.centeredText(font, Component.literal(card.nameProblem()), x + KeyboardWidget.WIDTH / 2, y - 10, ERROR);
			}
		}
	}
}
