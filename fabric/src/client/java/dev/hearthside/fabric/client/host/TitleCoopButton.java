package dev.hearthside.fabric.client.host;

import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.dev.FakeGamepadSource;
import dev.hearthside.fabric.client.input.SdlGamepadSource;
import dev.hearthside.fabric.client.menu.TitleInput;
import dev.hearthside.fabric.client.ui.HearthSprites;
import java.io.IOException;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * "Co-op" on the title screen, beside Singleplayer (Spec A section 3.1): Singleplayer's row is shared, so the menu
 * keeps vanilla's layout. It shows how many controllers are connected and opens the party lobby. Host only.
 */
public final class TitleCoopButton {
	private static final int GAP = 4;

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
		int full = singleplayer.getWidth();
		int half = (full - GAP) / 2;
		singleplayer.setWidth(half);
		CoopButton coop = new CoopButton(singleplayer.getX() + full - half, singleplayer.getY(), half,
				singleplayer.getHeight(), b -> openLobby(minecraft, title));
		widgets.add(index + 1, coop);
		ScreenEvents.afterTick(title).register(s -> coop.setControllers(controllers()));
	}

	private static void openLobby(Minecraft minecraft, TitleScreen title) {
		try {
			// The controller that pressed Co-op (if one did) becomes player 1's.
			minecraft.gui.setScreen(new LobbyScreen(title, Party.openLobby(minecraft, TitleInput.pressingPad())));
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Hearthside could not start a co-op party", e);
		}
	}

	/** The button's full text (narration and tests): "Co-op", with the controller count once there is one. */
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

	/** Half a row wide, so it draws "Co-op" and a small controller badge with the count rather than the full text. */
	private static final class CoopButton extends Button {
		private int controllers;

		CoopButton(int x, int y, int width, int height, OnPress onPress) {
			super(x, y, width, height, label(0), onPress, DEFAULT_NARRATION);
		}

		void setControllers(int count) {
			controllers = count;
			setMessage(label(count));
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			extractDefaultSprite(g);
			var font = Minecraft.getInstance().font;
			Component word = Component.literal("Co-op");
			String count = String.valueOf(controllers);
			int badge = controllers > 0 ? 4 + 13 + 2 + font.width(count) : 0;
			int x = getX() + (getWidth() - font.width(word) - badge) / 2;
			int textY = getY() + (getHeight() - 8) / 2;
			int colour = isActive() ? 0xFFFFFFFF : 0xFFA0A0A0;
			g.text(font, word, x, textY, colour, true);
			if (controllers > 0) {
				int iconX = x + font.width(word) + 4;
				g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.CONTROLLER, iconX, getY() + (getHeight() - 9) / 2,
						13, 9);
				g.text(font, count, iconX + 15, textY, colour, true);
			}
		}
	}
}
