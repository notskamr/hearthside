package dev.hearthside.fabric.client.menu;

import dev.hearthside.core.input.GamepadSource;
import dev.hearthside.core.input.MenuNav;
import dev.hearthside.core.input.PadSnapshot;
import dev.hearthside.core.profile.PlayerColors;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.dev.FakeGamepadSource;
import dev.hearthside.fabric.client.host.Party;
import dev.hearthside.fabric.client.input.FrameHook;
import dev.hearthside.fabric.client.input.SdlGamepadSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Controllers on the title screen and other menus before any party exists (Spec A section 3.1: "any controller can
 * navigate the title screen"). Every connected controller can move focus and press; once a party starts, its own
 * controller loop takes over. Render thread.
 */
public final class TitleInput implements Runnable {
	private final Minecraft minecraft;
	private final Map<Integer, MenuNav> navs = new HashMap<>();
	private GamepadSource source;
	private boolean unavailable;
	/** The controller whose A is being applied right now, for the Co-op button to hand to player 1. */
	private static Integer pressingPad;

	private TitleInput(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	public static void register(Minecraft minecraft) {
		MenuNavigator.register();
		FrameHook.register(new TitleInput(minecraft));
	}

	/** The controller pressing A right now, or null (the mouse or keyboard). Render thread. */
	public static Integer pressingPad() {
		return pressingPad;
	}

	@Override
	public void run() {
		Screen screen = minecraft.gui.screen();
		if (Party.current() != null || minecraft.level != null || screen == null || unavailable) {
			// A fresh start next time, so buttons held now don't count as presses then.
			navs.clear();
			return;
		}
		if (source == null) {
			try {
				source = Boolean.getBoolean("hearthside.e2e.fakePads") ? FakeGamepadSource.INSTANCE : SdlGamepadSource.open();
			} catch (IllegalStateException e) {
				HearthsideMod.LOGGER.warn("Controllers are unavailable on the title screen", e);
				unavailable = true;
				return;
			}
		}
		List<PadSnapshot> pads = source.poll();
		navs.keySet().retainAll(pads.stream().map(PadSnapshot::instanceId).toList());
		long now = System.nanoTime();
		for (PadSnapshot pad : pads) {
			MenuNav nav = navs.computeIfAbsent(pad.instanceId(), id -> new MenuNav());
			for (MenuNav.Action action : nav.step(pad.frame(), now)) {
				Screen current = minecraft.gui.screen();
				if (current == null || Party.current() != null) {
					return;
				}
				pressingPad = action == MenuNav.Action.ACCEPT ? pad.instanceId() : null;
				try {
					MenuNavigator.apply(minecraft, current, action, PlayerColors.DEFAULTS[0]);
				} finally {
					pressingPad = null;
				}
			}
		}
	}
}
