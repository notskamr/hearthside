package dev.hearthside.core.input;

import static dev.hearthside.core.input.ControlScheme.BEDROCK;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ControlSchemeJsonTest {
	@Test
	void roundTripsBedrock() {
		assertThat(ControlSchemeJson.fromJson(ControlSchemeJson.toJson(BEDROCK))).isEqualTo(BEDROCK);
	}

	@Test
	void unknownNamesAreIgnored() {
		var scheme = ControlSchemeJson.fromJson("{\"gameplay\":{\"FLY\":\"A\",\"JUMP\":\"Z\",\"SNEAK\":\"B\"}}");
		assertThat(scheme.controlFor(Action.JUMP)).isEmpty();
		assertThat(scheme.controlFor(Action.SNEAK)).contains(Control.B);
		assertThat(scheme.menu()).isEqualTo(BEDROCK.menu());
	}

	@Test
	void badValuesFallBackPerField() {
		var scheme = ControlSchemeJson.fromJson("{\"lookSensitivity\":\"fast\",\"invertY\":3,\"deadZone\":9,\"autoJump\":false}");
		assertThat(scheme.lookSensitivity()).isEqualTo(1.0f);
		assertThat(scheme.invertY()).isFalse();
		assertThat(scheme.deadZone()).isEqualTo(0.5f);
		assertThat(scheme.autoJump()).isFalse();
		assertThat(scheme.gameplay()).isEqualTo(BEDROCK.gameplay());
	}

	@Test
	void duplicateControlsKeepOneAction() {
		var scheme = ControlSchemeJson.fromJson("{\"gameplay\":{\"SNEAK\":\"A\",\"JUMP\":\"A\"}}");
		assertThat(scheme.controlFor(Action.JUMP)).contains(Control.A);
		assertThat(scheme.controlFor(Action.SNEAK)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "null", "[]", "42", "{", "{\"gameplay\":7}", "\"text\""})
	void garbageGivesBedrock(String json) {
		var scheme = ControlSchemeJson.fromJson(json);
		assertThat(scheme.gameplay()).isEqualTo(BEDROCK.gameplay());
		assertThat(scheme.lookSensitivity()).isEqualTo(BEDROCK.lookSensitivity());
	}
}
