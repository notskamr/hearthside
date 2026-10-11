package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ControlLabelsTest {
	@Test
	void xboxFaceButtons() {
		assertThat(ControlLabels.glyph(Control.A, PadStyle.XBOX)).isEqualTo("A");
		assertThat(ControlLabels.glyph(Control.B, PadStyle.XBOX)).isEqualTo("B");
		assertThat(ControlLabels.glyph(Control.X, PadStyle.XBOX)).isEqualTo("X");
		assertThat(ControlLabels.glyph(Control.Y, PadStyle.XBOX)).isEqualTo("Y");
		assertThat(ControlLabels.glyph(Control.A, PadStyle.GENERIC)).isEqualTo("A");
	}

	@Test
	void playstationSymbols() {
		assertThat(ControlLabels.glyph(Control.A, PadStyle.PLAYSTATION)).isEqualTo("✕");
		assertThat(ControlLabels.glyph(Control.B, PadStyle.PLAYSTATION)).isEqualTo("○");
		assertThat(ControlLabels.glyph(Control.X, PadStyle.PLAYSTATION)).isEqualTo("□");
		assertThat(ControlLabels.glyph(Control.Y, PadStyle.PLAYSTATION)).isEqualTo("△");
	}

	@Test
	void nintendoSwapsByPosition() {
		assertThat(ControlLabels.glyph(Control.A, PadStyle.NINTENDO)).isEqualTo("B");
		assertThat(ControlLabels.glyph(Control.B, PadStyle.NINTENDO)).isEqualTo("A");
		assertThat(ControlLabels.glyph(Control.X, PadStyle.NINTENDO)).isEqualTo("Y");
		assertThat(ControlLabels.glyph(Control.Y, PadStyle.NINTENDO)).isEqualTo("X");
	}

	@Test
	void triggersPerStyle() {
		assertThat(ControlLabels.glyph(Control.RIGHT_TRIGGER, PadStyle.XBOX)).isEqualTo("RT");
		assertThat(ControlLabels.glyph(Control.RIGHT_TRIGGER, PadStyle.PLAYSTATION)).isEqualTo("R2");
		assertThat(ControlLabels.glyph(Control.RIGHT_TRIGGER, PadStyle.NINTENDO)).isEqualTo("ZR");
		assertThat(ControlLabels.glyph(Control.LEFT_BUMPER, PadStyle.PLAYSTATION)).isEqualTo("L1");
	}

	@Test
	void everyControlHasALabelInEveryStyle() {
		for (PadStyle style : PadStyle.values()) {
			for (Control control : Control.values()) {
				assertThat(ControlLabels.glyph(control, style)).isNotBlank();
			}
		}
	}
}
