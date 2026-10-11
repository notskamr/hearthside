package dev.hearthside.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.ui.KeyboardModel.Layer;
import org.junit.jupiter.api.Test;

class KeyboardModelTest {
	@Test
	void suggestionIsKeptByConfirm() {
		var keyboard = KeyboardModel.forName("Ember");
		assertThat(keyboard.text()).isEqualTo("Ember");
		assertThat(keyboard.suggested()).isTrue();
		assertThat(keyboard.canConfirm()).isTrue();
	}

	@Test
	void firstTypeReplacesTheSuggestion() {
		var keyboard = KeyboardModel.forName("Ember");
		// The cursor starts on the first letter key, "q".
		assertThat(keyboard.keyAt(keyboard.cursorRow(), keyboard.cursorColumn())).isEqualTo('q');
		keyboard.type();
		assertThat(keyboard.text()).isEqualTo("q");
		keyboard.move(1, 0);
		keyboard.type();
		assertThat(keyboard.text()).isEqualTo("qw");
		assertThat(keyboard.suggested()).isFalse();
	}

	@Test
	void deleteRemovesTheLastCharacter() {
		var text = KeyboardModel.forText("abc", 10);
		text.delete();
		assertThat(text.text()).isEqualTo("ab");
		var name = KeyboardModel.forName("Ember");
		name.delete();
		assertThat(name.text()).isEmpty();
		name.delete();
		assertThat(name.text()).isEmpty();
	}

	@Test
	void cursorWrapsWithinAndBetweenRows() {
		var keyboard = KeyboardModel.forText("", 20);
		keyboard.move(-1, 0);
		assertThat(keyboard.keyAt(keyboard.cursorRow(), keyboard.cursorColumn())).isEqualTo('p');
		keyboard.move(1, 0);
		assertThat(keyboard.keyAt(keyboard.cursorRow(), keyboard.cursorColumn())).isEqualTo('q');
		keyboard.move(0, -1);
		assertThat(keyboard.cursorRow()).isZero();
		keyboard.move(0, -1);
		// Up from the top row is the bottom row.
		assertThat(keyboard.cursorRow()).isEqualTo(3);
		keyboard.move(9, 0);
		keyboard.move(0, -1);
		keyboard.move(0, 1);
		// The bottom row is shorter: the column is clamped to it.
		assertThat(keyboard.cursorColumn()).isLessThan(7);
	}

	@Test
	void layersCycleWithBumpers() {
		var keyboard = KeyboardModel.forText("", 20);
		assertThat(keyboard.layer()).isEqualTo(Layer.LOWER);
		keyboard.nextLayer();
		assertThat(keyboard.layer()).isEqualTo(Layer.UPPER);
		assertThat(keyboard.keyAt(1, 0)).isEqualTo('Q');
		keyboard.nextLayer();
		assertThat(keyboard.layer()).isEqualTo(Layer.SYMBOLS);
		keyboard.nextLayer();
		assertThat(keyboard.layer()).isEqualTo(Layer.LOWER);
		keyboard.previousLayer();
		assertThat(keyboard.layer()).isEqualTo(Layer.SYMBOLS);
	}

	@Test
	void nameModeSkipsSymbolsAndSpace() {
		var keyboard = KeyboardModel.forName("");
		keyboard.nextLayer();
		keyboard.nextLayer();
		assertThat(keyboard.layer()).isEqualTo(Layer.LOWER);
		keyboard.previousLayer();
		assertThat(keyboard.layer()).isEqualTo(Layer.UPPER);
		keyboard.type();
		keyboard.space();
		assertThat(keyboard.text()).isEqualTo("Q");
	}

	@Test
	void nameModeStopsAtSixteen() {
		var keyboard = KeyboardModel.forName("");
		for (int i = 0; i < 20; i++) {
			keyboard.type();
		}
		assertThat(keyboard.text()).hasSize(16);
	}

	@Test
	void canConfirmOnlyValidNames() {
		var keyboard = KeyboardModel.forName("");
		assertThat(keyboard.canConfirm()).isFalse();
		keyboard.type();
		keyboard.type();
		assertThat(keyboard.canConfirm()).isFalse();
		keyboard.type();
		assertThat(keyboard.canConfirm()).isTrue();
	}

	@Test
	void textModeAllowsSymbolsAndSpace() {
		var keyboard = KeyboardModel.forText("", 20);
		keyboard.nextLayer();
		keyboard.nextLayer();
		while (keyboard.cursorRow() != 0) {
			keyboard.move(0, -1);
		}
		while (keyboard.cursorColumn() != 0) {
			keyboard.move(-1, 0);
		}
		keyboard.type();
		keyboard.space();
		assertThat(keyboard.text()).isEqualTo("! ");
		assertThat(keyboard.canConfirm()).isTrue();
	}

	@Test
	void textModeStopsAtItsMax() {
		var keyboard = KeyboardModel.forText("abcd", 5);
		keyboard.type();
		keyboard.type();
		assertThat(keyboard.text()).isEqualTo("abcdq");
	}
}
