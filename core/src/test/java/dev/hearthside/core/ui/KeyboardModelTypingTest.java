package dev.hearthside.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Player 1's PC keyboard typing into an on-screen keyboard (Spec E section 3.6): a name allows letters, digits and "_"
 * (a space becomes "_", as Y's does); text allows the keyboard's symbols too. The first character replaces an untouched
 * suggestion, as typing on the controller does. A refused character changes nothing.
 */
class KeyboardModelTypingTest {
	@Test
	void typingReplacesTheSuggestionThenAppends() {
		KeyboardModel name = KeyboardModel.forName("Player2");
		assertThat(name.typeChar('R')).isTrue();
		assertThat(name.typeChar('o')).isTrue();
		assertThat(name.text()).isEqualTo("Ro");
		assertThat(name.suggested()).isFalse();
	}

	@Test
	void nameAllowsLettersDigitsAndUnderscore() {
		KeyboardModel name = KeyboardModel.forName("");
		for (char c : "Ab9_".toCharArray()) {
			assertThat(name.typeChar(c)).isTrue();
		}
		assertThat(name.text()).isEqualTo("Ab9_");
	}

	@Test
	void aSpaceInANameIsAnUnderscore() {
		KeyboardModel name = KeyboardModel.forName("");
		name.typeChar('A');
		assertThat(name.typeChar(' ')).isTrue();
		assertThat(name.text()).isEqualTo("A_");
	}

	@Test
	void aNameRefusesOtherCharactersAndKeepsTheSuggestion() {
		KeyboardModel name = KeyboardModel.forName("Player2");
		assertThat(name.typeChar('#')).isFalse();
		assertThat(name.typeChar('é')).isFalse();
		assertThat(name.text()).isEqualTo("Player2");
		assertThat(name.suggested()).isTrue();
	}

	@Test
	void textAllowsTheKeyboardsSymbolsAndSpaces() {
		KeyboardModel text = KeyboardModel.forText("", 32);
		for (char c : "Hi, you! #1".toCharArray()) {
			assertThat(text.typeChar(c)).isTrue();
		}
		assertThat(text.text()).isEqualTo("Hi, you! #1");
	}

	@Test
	void textRefusesCharactersTheKeyboardHasNot() {
		KeyboardModel text = KeyboardModel.forText("", 32);
		assertThat(text.typeChar('é')).isFalse();
		assertThat(text.typeChar('\n')).isFalse();
		assertThat(text.text()).isEmpty();
	}

	@Test
	void nothingPastTheMaximum() {
		KeyboardModel name = KeyboardModel.forName("");
		for (int i = 0; i < KeyboardModel.NAME_MAX; i++) {
			assertThat(name.typeChar('a')).isTrue();
		}
		assertThat(name.typeChar('b')).isFalse();
		assertThat(name.text()).hasSize(KeyboardModel.NAME_MAX);
	}
}
