package dev.hearthside.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RoleTest {
	@Test
	void nullOrBlankMeansHost() {
		assertThat(Role.fromProperty(null)).isEqualTo(Role.HOST);
		assertThat(Role.fromProperty("  ")).isEqualTo(Role.HOST);
	}

	@Test
	void parsesCaseInsensitively() {
		assertThat(Role.fromProperty("Satellite")).isEqualTo(Role.SATELLITE);
		assertThat(Role.fromProperty("host")).isEqualTo(Role.HOST);
	}

	@Test
	void unknownValueFailsLoudly() {
		assertThatThrownBy(() -> Role.fromProperty("spectator"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("hearthside.role");
	}
}
