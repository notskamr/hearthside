package dev.hearthside.fabric.client.host;

import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.dev.FakeGamepadSource;
import dev.hearthside.fabric.client.input.SdlGamepadSource;
import java.io.IOException;
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

	private TitleCoopButton() {}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof TitleScreen title) {
				add(minecraft, title);
			}
		});
	}

	private static void add(Minecraft minecraft, TitleScreen title) {
		List<AbstractWidget> widgets = Screens.getWidgets(title);
		int index = indexOf(widgets, "menu.singleplayer");
		if (index < 0) {
			HearthsideMod.LOGGER.warn("Hearthside could not find Singleplayer on the title screen; no Co-op button");
			return;
		}
		AbstractWidget singleplayer = widgets.get(index);
		for (AbstractWidget widget : widgets) {
			if (widget.getY() > singleplayer.getY()) {
				widget.setY(widget.getY() + ROW);
			}
		}
		Button coop = Button.builder(label(controllers()), b -> openLobby(minecraft, title))
				.bounds(singleplayer.getX(), singleplayer.getY() + ROW, singleplayer.getWidth(), singleplayer.getHeight())
				.build();
		widgets.add(index + 1, coop);
		ScreenEvents.afterTick(title).register(s -> coop.setMessage(label(controllers())));
	}

	private static void openLobby(Minecraft minecraft, TitleScreen title) {
		try {
			minecraft.gui.setScreen(new LobbyScreen(title, Party.openLobby(minecraft)));
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
