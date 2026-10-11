package dev.hearthside.fabric.client.host;

import dev.hearthside.core.profile.PlayerColors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/**
 * The co-op party lobby (Spec A section 3.1): each controller presses A to take a seat, its player's game starts
 * warming up, and Continue (or Menu on any seated controller) goes on to pick a world together. This is the plain,
 * functional version; Plan 5b2 brings the Hearthside look.
 */
public final class LobbyScreen extends Screen {
	private final Screen parent;
	private final Party party;

	public LobbyScreen(Screen parent, Party party) {
		super(Component.literal("Co-op"));
		this.parent = parent;
		this.party = party;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("Continue"), b -> continueToWorlds())
				.bounds(width / 2 - 155, height - 32, 150, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
				.bounds(width / 2 + 5, height - 32, 150, 20).build());
	}

	/** On to choosing a world, with the party kept. */
	public void continueToWorlds() {
		minecraft.gui.setScreen(new SelectWorldScreen(this));
	}

	@Override
	public void onClose() {
		// Leaving the lobby ends the party: every guest's game stops.
		party.disband();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
		graphics.centeredText(font, Component.literal(party.seatedCount() + 1 + " of 4 players"), width / 2, 28, 0xFFB0B0B0);
		int cardWidth = Math.min(110, (width - 50) / 4);
		int cardHeight = 90;
		int gap = 10;
		int left = (width - (4 * cardWidth + 3 * gap)) / 2;
		int top = height / 2 - cardHeight / 2 - 10;
		for (int i = 0; i < 4; i++) {
			int x = left + i * (cardWidth + gap);
			Party.SeatView seat = i == 0 ? new Party.SeatView(minecraft.getUser().getName(), "Player 1", PlayerColors.DEFAULTS[0], true)
					: party.seatView(i);
			int border = seat.taken() ? 0xFF000000 | seat.color() : 0xFF555555;
			graphics.fill(x - 2, top - 2, x + cardWidth + 2, top + cardHeight + 2, border);
			graphics.fill(x, top, x + cardWidth, top + cardHeight, 0xC0101010);
			graphics.centeredText(font, Component.literal(seat.name()), x + cardWidth / 2, top + cardHeight / 2 - 10,
					seat.taken() ? 0xFFFFFFFF : 0xFFB0B0B0);
			graphics.centeredText(font, Component.literal(seat.status()), x + cardWidth / 2, top + cardHeight / 2 + 4, 0xFFB0B0B0);
		}
	}
}
