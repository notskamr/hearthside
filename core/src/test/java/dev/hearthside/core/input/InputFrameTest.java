package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InputFrameTest {
	@Test
	void reportsPressedButtons() {
		var f = new InputFrame(1, InputFrame.buttons(PadButton.A, PadButton.START), 0, 0, 0, 0, 0, 0);
		assertThat(f.pressed(PadButton.A)).isTrue();
		assertThat(f.pressed(PadButton.START)).isTrue();
		assertThat(f.pressed(PadButton.B)).isFalse();
	}

	@Test
	void clampsAxesAndTriggers() {
		var f = new InputFrame(1, 0, 2f, -3f, 0.5f, -0.5f, -1f, 7f);
		assertThat(f.leftX()).isEqualTo(1f);
		assertThat(f.leftY()).isEqualTo(-1f);
		assertThat(f.rightX()).isEqualTo(0.5f);
		assertThat(f.leftTrigger()).isEqualTo(0f);
		assertThat(f.rightTrigger()).isEqualTo(1f);
	}

	@Test
	void nanBecomesZero() {
		assertThat(new InputFrame(1, 0, Float.NaN, 0, 0, 0, Float.NaN, 0).leftX()).isZero();
	}

	@Test
	void buttonMasksAreStable() {
		assertThat(PadButton.A.mask()).isEqualTo(1);
		assertThat(PadButton.DPAD_RIGHT.mask()).isEqualTo(1 << 14);
	}

	@Test
	void neutralHasNothingPressed() {
		assertThat(InputFrame.NEUTRAL.buttons()).isZero();
		assertThat(InputFrame.NEUTRAL.sequence()).isZero();
	}
}
