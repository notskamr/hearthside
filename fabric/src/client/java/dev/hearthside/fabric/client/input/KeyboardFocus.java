package dev.hearthside.fabric.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.TextInputManager;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.ui.HearthSounds;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/**
 * Player 1's PC keyboard typing into an on-screen keyboard (Spec E section 3.6): a lobby seat card's, the New World
 * name's, or an in-world newcomer's card. Characters type, Backspace deletes, Enter accepts and Esc backs out, as the
 * controller's buttons do; the controller keeps working alongside. With several keyboards open, typing goes to the one
 * used most recently. Render thread.
 */
public final class KeyboardFocus {
	/** An on-screen keyboard player 1's PC keyboard can type into. */
	public interface Target {
		/** Whether the keyboard is open now. */
		boolean keyboardOpen();

		/** A typed character; false if this keyboard can't type it (nothing changed). */
		boolean typeChar(char c);

		/** Backspace, as X. */
		void backspace();

		/** Enter, as the controller's accept (Menu). */
		void enter();

		/** Esc, as B. */
		void escape();
	}

	private static WeakReference<Target> last = new WeakReference<>(null);
	/** Hearthside's claim on SDL text input, so a text box that takes it over isn't turned off by us. */
	private static final Object TEXT_INPUT_OWNER = new Object();
	private static boolean textInputOn, textInputFailed;
	private static final int NO_KEY = -1;
	/** The key whose press an on-screen keyboard took, until it is let go: its repeats are the keyboard's too. */
	private static int heldKey = NO_KEY;

	private KeyboardFocus() {}

	/**
	 * Keeps SDL text input on while an on-screen keyboard takes player 1's typing ({@code wanted}), as vanilla does for a
	 * focused text box: without it the OS sends no characters. Every client tick, since a screen change turns it off.
	 * Once no keyboard wants it, a focused text box gets it back; otherwise it is turned off. A failure is logged once and
	 * leaves text input as Minecraft has it. Render thread.
	 */
	public static void keepTextInput(Minecraft minecraft, BooleanSupplier wanted) {
		try {
			keepTextInput(minecraft, wanted.getAsBoolean());
		} catch (RuntimeException e) {
			if (!textInputFailed) {
				textInputFailed = true;
				HearthsideMod.LOGGER.error("Hearthside couldn't keep text input on for an on-screen keyboard", e);
			}
		}
	}

	private static void keepTextInput(Minecraft minecraft, boolean wanted) {
		TextInputManager input = minecraft.textInputManager();
		if (wanted) {
			input.startTextInput(TEXT_INPUT_OWNER);
			textInputOn = true;
		} else if (textInputOn) {
			textInputOn = false;
			Screen screen = minecraft.gui.screen();
			if (screen != null && screen.getFocused() instanceof EditBox box && box.isFocused()) {
				input.startTextInput(box);
			} else {
				input.stopTextInput(TEXT_INPUT_OWNER);
			}
		}
	}

	/** A keyboard was opened or used (by a controller or the PC keyboard): typing goes to it from now on. */
	public static void used(Target target) {
		last = new WeakReference<>(target);
	}

	/** Of the open keyboards, the one typing goes to: the most recently used, else the first. */
	public static Optional<Target> among(List<? extends Target> keyboards) {
		Target recent = last.get();
		Target first = null;
		for (Target target : keyboards) {
			if (!target.keyboardOpen()) {
				continue;
			}
			if (target == recent) {
				return Optional.of(target);
			}
			if (first == null) {
				first = target;
			}
		}
		return Optional.ofNullable(first);
	}

	/** A character from player 1's PC keyboard: typed with a key tap, or refused with the soft "no" tick. */
	public static void charTyped(Target target, int codepoint) {
		used(target);
		if (Character.isBmpCodePoint(codepoint) && target.typeChar((char) codepoint)) {
			HearthSounds.play(HearthSounds.Ui.KEY);
		} else {
			HearthSounds.refuse();
		}
	}

	/**
	 * A key pressed (or repeating) on player 1's PC keyboard while {@code keyboard} (if any) takes their typing: true if
	 * the keyboard took it. Backspace deletes, Enter accepts, Esc backs out; every other key is taken too, so nothing
	 * under the keyboard acts on it (its characters arrive through {@link #charTyped}), except the function keys, which
	 * never type. A taken key stays taken until it is let go: holding Backspace keeps deleting, and a held Esc or Enter
	 * that closed the keyboard doesn't go on to act on what was under it.
	 */
	public static boolean keyPressed(Optional<Target> keyboard, int key) {
		if (functionKey(key)) {
			return false;
		}
		if (key == heldKey) {
			if (key == InputConstants.KEY_BACKSPACE && keyboard.isPresent()) {
				keyboard.get().backspace();
				HearthSounds.play(HearthSounds.Ui.KEY_DELETE);
			}
			return true;
		}
		if (keyboard.isEmpty()) {
			return false;
		}
		heldKey = key;
		Target target = keyboard.get();
		switch (key) {
			case InputConstants.KEY_BACKSPACE -> {
				used(target);
				target.backspace();
				HearthSounds.play(HearthSounds.Ui.KEY_DELETE);
			}
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				used(target);
				target.enter();
			}
			case InputConstants.KEY_ESCAPE -> target.escape();
			default -> {
			}
		}
		return true;
	}

	/** A key let go (any key, on any screen or none): if a keyboard took its press, its repeats are over. */
	public static void keyReleased(int key) {
		if (key == heldKey) {
			heldKey = NO_KEY;
		}
	}

	/** F1 to F24 (fullscreen, screenshots, the debug screen): never typing, so always player 1's own. */
	private static boolean functionKey(int key) {
		return key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12
				|| key >= InputConstants.KEY_F13 && key <= InputConstants.KEY_F24;
	}
}
