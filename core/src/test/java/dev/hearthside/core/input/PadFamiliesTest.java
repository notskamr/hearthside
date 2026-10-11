package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PadFamiliesTest {
	// SDL3 SDL_GamepadType values.
	private static final int UNKNOWN = 0, STANDARD = 1, XBOX360 = 2, XBOXONE = 3, PS3 = 4, PS4 = 5, PS5 = 6,
			SWITCH_PRO = 7, JOYCON_PAIR = 10;

	@Test
	void xboxTypesAreXbox() {
		assertThat(PadFamilies.fromSdl(XBOX360, 0)).isEqualTo(PadStyle.XBOX);
		assertThat(PadFamilies.fromSdl(XBOXONE, 0)).isEqualTo(PadStyle.XBOX);
	}

	@Test
	void playStationTypesArePlayStation() {
		assertThat(PadFamilies.fromSdl(PS3, 0)).isEqualTo(PadStyle.PLAYSTATION);
		assertThat(PadFamilies.fromSdl(PS4, 0)).isEqualTo(PadStyle.PLAYSTATION);
		assertThat(PadFamilies.fromSdl(PS5, 0)).isEqualTo(PadStyle.PLAYSTATION);
	}

	@Test
	void nintendoTypesAreNintendo() {
		assertThat(PadFamilies.fromSdl(SWITCH_PRO, 0)).isEqualTo(PadStyle.NINTENDO);
		assertThat(PadFamilies.fromSdl(JOYCON_PAIR, 0)).isEqualTo(PadStyle.NINTENDO);
	}

	@Test
	void unknownTypeUsesTheVendor() {
		assertThat(PadFamilies.fromSdl(UNKNOWN, 0x054C)).isEqualTo(PadStyle.PLAYSTATION);
		assertThat(PadFamilies.fromSdl(STANDARD, 0x045E)).isEqualTo(PadStyle.XBOX);
		assertThat(PadFamilies.fromSdl(STANDARD, 0x057E)).isEqualTo(PadStyle.NINTENDO);
	}

	@Test
	void unknownEverythingIsGeneric() {
		assertThat(PadFamilies.fromSdl(UNKNOWN, 0x1234)).isEqualTo(PadStyle.GENERIC);
		assertThat(PadFamilies.fromSdl(99, 0)).isEqualTo(PadStyle.GENERIC);
	}

	@ParameterizedTest
	@EnumSource(PadStyle.class)
	void startIsNeverCalledStart(PadStyle style) {
		assertThat(ControlLabels.name(Control.START, style)).isNotEqualToIgnoringCase("start");
		assertThat(ControlLabels.glyph(Control.START, style)).isNotEqualToIgnoringCase("start");
	}

	@Test
	void menuButtonsUseEachControllersOwnName() {
		assertThat(ControlLabels.name(Control.START, PadStyle.XBOX)).isEqualTo("Menu");
		assertThat(ControlLabels.name(Control.BACK, PadStyle.XBOX)).isEqualTo("View");
		assertThat(ControlLabels.name(Control.START, PadStyle.PLAYSTATION)).isEqualTo("Options");
		assertThat(ControlLabels.name(Control.BACK, PadStyle.PLAYSTATION)).isEqualTo("Create");
		assertThat(ControlLabels.name(Control.START, PadStyle.NINTENDO)).isEqualTo("+");
		assertThat(ControlLabels.name(Control.BACK, PadStyle.NINTENDO)).isEqualTo("−");
		assertThat(ControlLabels.name(Control.A, PadStyle.PLAYSTATION)).isEqualTo("Cross");
	}

	@Test
	void glyphIdIsFamilySlashControl() {
		assertThat(ControlLabels.glyphId(Control.A, PadStyle.XBOX)).isEqualTo("xbox/a");
		assertThat(ControlLabels.glyphId(Control.START, PadStyle.PLAYSTATION)).isEqualTo("playstation/start");
		assertThat(ControlLabels.glyphId(Control.DPAD_UP, PadStyle.GENERIC)).isEqualTo("generic/dpad_up");
	}
}
