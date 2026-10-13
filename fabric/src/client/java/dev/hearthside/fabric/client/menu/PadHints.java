package dev.hearthside.fabric.client.menu;

import dev.hearthside.core.Role;
import dev.hearthside.core.input.Control;
import dev.hearthside.fabric.client.HearthsideClient;
import dev.hearthside.fabric.client.host.Party;
import dev.hearthside.fabric.client.input.ButtonGuideRenderer;
import dev.hearthside.fabric.client.ui.HintChip;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * The hint chips under a Hearthside screen over the world (the pause menu, the Leave confirm): A Select and B for its
 * way back, in this game's controller family. They show only while a controller drives the game, checked every frame,
 * so a controller picked up with the screen open brings them. Clicking one does what its button does.
 */
final class PadHints {
	private static final int GAP = 4;
	private final List<HintChip> chips;

	/** A presses {@code screen}'s focused button; B does {@code back}, labelled {@code backLabel}. */
	PadHints(Screen screen, Component backLabel, Runnable back) {
		chips = List.of(
				new HintChip(0, 0, Control.A, ButtonGuideRenderer::style, Component.literal("Select"), b -> pressFocused(screen)),
				new HintChip(0, 0, Control.B, ButtonGuideRenderer::style, backLabel, b -> back.run()));
	}

	/** Lays the row out centred on {@code centreX} with its top at {@code y}, adding each chip with {@code add}. */
	void addTo(Consumer<HintChip> add, int centreX, int y) {
		place(centreX, y);
		chips.forEach(add);
		update();
	}

	/** Moves the row: centred on {@code centreX}, its top at {@code y}. */
	void place(int centreX, int y) {
		int x = centreX - width() / 2;
		for (HintChip chip : chips) {
			chip.setPosition(x, y);
			x += chip.getWidth() + GAP;
		}
	}

	private int width() {
		int total = (chips.size() - 1) * GAP;
		for (HintChip chip : chips) {
			total += chip.getWidth();
		}
		return total;
	}

	/** Shows or hides the row for whether a controller drives this game now. Every frame, before the screen draws. */
	void update() {
		boolean show = controllerDriven();
		for (HintChip chip : chips) {
			chip.visible = show;
		}
	}

	/**
	 * Whether a controller drives this game's screen now: always a guest's; player 1's while they hold one and their
	 * mouse hasn't taken over (as on Minecraft's screens, where the hints go while the mouse drives).
	 */
	static boolean controllerDriven() {
		if (HearthsideClient.role() == Role.SATELLITE) {
			return true;
		}
		Party party = Party.current();
		return party != null && party.hostOnController();
	}

	/** Presses the screen's focused button, as A does (never a chip: clicking one focuses it). */
	static void pressFocused(Screen screen) {
		if (screen.getFocused() instanceof AbstractButton focused && !(focused instanceof HintChip) && focused.active) {
			focused.onPress(new KeyEvent(0, 0, 0));
		}
	}
}
