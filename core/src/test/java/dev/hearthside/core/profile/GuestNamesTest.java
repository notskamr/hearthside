package dev.hearthside.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GuestNamesTest {
	@ParameterizedTest
	@ValueSource(strings = {"Bob", "player_2", "ABCDEFGHIJKLMNOP"})
	void acceptsMinecraftNames(String name) {
		assertThat(GuestNames.isValid(name)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "ab", "ABCDEFGHIJKLMNOPQ", "has space", "émile", "a-b"})
	void rejectsInvalidNames(String name) {
		assertThat(GuestNames.isValid(name)).isFalse();
	}

	@Test
	void nullIsInvalid() {
		assertThat(GuestNames.isValid(null)).isFalse();
	}

	@Test
	void profileConstructorRejectsBadName() {
		assertThatThrownBy(() -> new GuestProfile(UUID.randomUUID(), "x", SkinRef.defaultSkin("steve"), null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
