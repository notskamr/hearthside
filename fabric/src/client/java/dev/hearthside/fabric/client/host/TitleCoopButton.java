package dev.hearthside.fabric.client.host;

import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.dev.FakeGamepadSource;
import dev.hearthside.fabric.client.input.SdlGamepadSource;
import java.io.IOException;
import dev.hearthside.fabric.client.menu.TitleInput;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * "Co-op" on the title screen, under Singleplayer (Spec A section 3.1): it shows how many controllers are connected and
 * opens the party lobby. Host only.
 */
public final class TitleCoopButton {
	private static final int ROW = 24;
	/** Widgets this close to the bottom edge are anchored to it (the copyright line), not part of the menu. */
	private static final int BOTTOM_PINNED = 20;

	private TitleCoopButton() {}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof TitleScreen title) {
				add(minecraft, title);
			}
		});
	}

	private static void add(Minecraft minecraft, TitleScreen title) {
		Party party = Party.current();
		if (party != null && !party.inWorld()) {
			// A world failed to open and Minecraft fell back to the title screen: the party is still waiting, so
			// return to its lobby with every seat kept (Spec A section 3.1).
			minecraft.execute(() -> {
				if (minecraft.gui.screen() == title && Party.current() == party) {
					minecraft.gui.setScreen(new LobbyScreen(title, party));
				}
			});
		}
		List<AbstractWidget> widgets = Screens.getWidgets(title);
		int index = indexOf(widgets, "menu.singleplayer");
		if (index < 0) {
			HearthsideMod.LOGGER.warn("Hearthside could not find Singleplayer on the title screen; no Co-op button");
			return;
		}
		AbstractWidget singleplayer = widgets.get(index);
		// Make room for one more row without pushing the menu off small screens: the rows from Singleplayer up move up
		// half a row, the rows below it move down half a row, and text pinned to the bottom edge stays where it is.
		int pinnedBottom = title.height - BOTTOM_PINNED;
		for (AbstractWidget widget : widgets) {
			if (widget.getY() >= pinnedBottom) {
				continue;
			}
			widget.setY(widget.getY() + (widget.getY() > singleplayer.getY() ? ROW / 2 : -ROW / 2));
		}
		Button coop = Button.builder(label(controllers()), b -> openLobby(minecraft, title))
				.bounds(singleplayer.getX(), singleplayer.getY() + ROW, singleplayer.getWidth(), singleplayer.getHeight())
				.build();
		widgets.add(index + 1, coop);
		ScreenEvents.afterTick(title).register(s -> coop.setMessage(label(controllers())));
	}

	private static void openLobby(Minecraft minecraft, TitleScreen title) {
		try {
			// The controller that pressed Co-op (if one did) becomes player 1's.
			minecraft.gui.setScreen(new LobbyScreen(title, Party.openLobby(minecraft, TitleInput.pressingPad())));
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Hearthside could not start a co-op party", e);
		}
	}

	/** The button's text: "Co-op", with the controller count once there is one. */
	public static Component label(int controllers) {
		if (controllers <= 0) {
			return Component.literal("Co-op");
		}
		return Component.literal("Co-op (" + controllers + (controllers == 1 ? " controller)" : " controllers)"));
	}

	private static int controllers() {
		return Boolean.getBoolean("hearthside.e2e.fakePads") ? FakeGamepadSource.INSTANCE.poll().size()
				: SdlGamepadSource.connectedCount();
	}

	private static int indexOf(List<AbstractWidget> widgets, String key) {
		for (int i = 0; i < widgets.size(); i++) {
			if (widgets.get(i).getMessage().getContents() instanceof TranslatableContents contents
					&& contents.getKey().equals(key)) {
				return i;
			}
		}
		return -1;
	}
}
