package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HeldSuppressionTest {
	final HeldSuppression suppression = new HeldSuppression();

	static InputFrame frame(float leftTrigger, PadButton... held) {
		return new InputFrame(1, InputFrame.buttons(held), 0.5f, -0.5f, 0.2f, 0.1f, leftTrigger, 0);
	}

	@Test
	void suppressedButtonIsHiddenUntilReleased() {
		suppression.suppressHeld(frame(0, PadButton.B));
		assertThat(suppression.filter(frame(0, PadButton.B)).pressed(PadButton.B)).isFalse();
		assertThat(suppression.filter(frame(0)).pressed(PadButton.B)).isFalse();
		assertThat(suppression.filter(frame(0, PadButton.B)).pressed(PadButton.B)).isTrue();
	}

	@Test
	void triggersAreSuppressedToo() {
		suppression.suppressHeld(frame(0.9f));
		assertThat(suppression.filter(frame(0.9f)).leftTrigger()).isZero();
		suppression.filter(frame(0));
		assertThat(suppression.filter(frame(0.9f)).leftTrigger()).isEqualTo(0.9f);
	}

	@Test
	void otherButtonsAndSticksPassThrough() {
		suppression.suppressHeld(frame(0, PadButton.A));
		InputFrame filtered = suppression.filter(frame(0, PadButton.A, PadButton.X));
		assertThat(filtered.pressed(PadButton.X)).isTrue();
		assertThat(filtered.leftX()).isEqualTo(0.5f);
		assertThat(filtered.rightY()).isEqualTo(0.1f);
	}

	@Test
	void nothingSuppressedByDefault() {
		assertThat(suppression.filter(frame(0.9f, PadButton.B))).isEqualTo(frame(0.9f, PadButton.B));
	}
}
