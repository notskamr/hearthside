package dev.hearthside.fabric.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hearthside.core.input.MenuNav;
import dev.hearthside.fabric.client.ui.DrawsOwnSelection;
import dev.hearthside.fabric.client.ui.HearthSounds;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/**
 * Drives any screen from a controller through the screen's own keyboard navigation (Spec A section 7.1): the D-pad
 * moves focus as the arrow keys do, A presses the focused widget as Enter does, B goes back as Escape does. The
 * focused widget is outlined in the colour of whoever moved it last.
 */
public final class MenuNavigator {
	private static volatile int focusColour = 0x3F7FD0;
	private static boolean registered;

	private MenuNavigator() {}

	/** @return whether the screen used it */
	public static boolean apply(Minecraft minecraft, Screen screen, MenuNav.Action action, int colour) {
		register();
		int key = switch (action) {
			case UP -> InputConstants.KEY_UP;
			case DOWN -> InputConstants.KEY_DOWN;
			case LEFT -> InputConstants.KEY_LEFT;
			case RIGHT -> InputConstants.KEY_RIGHT;
			case ACCEPT -> InputConstants.KEY_RETURN;
			case BACK -> InputConstants.KEY_ESCAPE;
			case MORE -> -1;
		};
		if (key < 0 || screen == null) {
			return false;
		}
		focusColour = colour & 0xFFFFFF;
		minecraft.setLastInputType(InputType.KEYBOARD_ARROW);
		boolean move = action != MenuNav.Action.ACCEPT && action != MenuNav.Action.BACK;
		GuiEventListener before = move ? deepFocus(screen) : null;
		boolean used = screen.keyPressed(new KeyEvent(key, keycode(key), 0));
		if (move && minecraft.gui.screen() == screen && deepFocus(screen) != before) {
			// A wooden tick as the highlight moves (the mouse hovering stays silent).
			HearthSounds.play(HearthSounds.Ui.TICK);
		}
		return used;
	}

	/** The innermost focused element (a world card inside its list, say). */
	private static GuiEventListener deepFocus(ContainerEventHandler container) {
		GuiEventListener focused = container.getFocused();
		return focused instanceof ContainerEventHandler inner && inner.getFocused() != null ? deepFocus(inner) : focused;
	}

	/**
	 * The SDL keycode for a scancode: a key event carries both, and screens read arrows from the keycode but Enter and
	 * Escape from the scancode. Non-character keys are the scancode with bit 30 set; Enter and Escape are characters.
	 */
	private static int keycode(int scancode) {
		return switch (scancode) {
			case InputConstants.KEY_RETURN -> '\r';
			case InputConstants.KEY_ESCAPE -> 0x1B;
			default -> 0x40000000 | scancode;
		};
	}

	/** Draws the focus outline on every screen from now on (registered at startup, before any screen opens). */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) ->
				ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> outline(minecraft, s, g)));
	}

	/**
	 * The focused widget's outline in the driver's colour, while a controller (or the arrow keys) moved it. The pause
	 * menu's buttons light up as the selection themselves: no second ring around them.
	 */
	private static void outline(Minecraft minecraft, Screen screen, GuiGraphicsExtractor g) {
		if (minecraft.getLastInputType() != InputType.KEYBOARD_ARROW
				|| !(screen.getFocused() instanceof AbstractWidget widget) || !widget.visible
				|| widget instanceof DrawsOwnSelection || screen instanceof DrawsOwnSelection) {
			return;
		}
		g.outline(widget.getX() - 2, widget.getY() - 2, widget.getWidth() + 4, widget.getHeight() + 4,
				0xFF000000 | focusColour);
	}
}
