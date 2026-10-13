package dev.hearthside.fabric.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hearthside.fabric.client.ui.HearthSounds;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Optional;

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

	private KeyboardFocus() {}

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

	/** A key pressed on player 1's PC keyboard: true if it was Backspace, Enter or Esc, done on the keyboard. */
	public static boolean keyPressed(Target target, int key) {
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
				return false;
			}
		}
		return true;
	}
}
