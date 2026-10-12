package dev.hearthside.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Session-only option values (Spec D section 3.3): a preset's values hold for the session, but a player's options file
 * always gets the player's own value back. A player who changes one of those options themselves mid-session keeps
 * their change: it is saved, and the session value is never put back over it (plan Review Focus 2).
 */
class SessionOverridesTest {
	@Test
	void theOwnValueIsSaved() {
		var overrides = new SessionOverrides<String>();
		overrides.apply("renderDistance", 12, 6);
		assertThat(overrides.valueToSave("renderDistance", 6)).isEqualTo(12);
	}

	@Test
	void afterSaveRestoresTheSessionValue() {
		var overrides = new SessionOverrides<String>();
		overrides.apply("renderDistance", 12, 6);
		overrides.valueToSave("renderDistance", 6);
		assertThat(overrides.valueAfterSave("renderDistance")).isEqualTo(6);
	}

	@Test
	void aChangedOptionBecomesThePlayers() {
		var overrides = new SessionOverrides<String>();
		overrides.apply("renderDistance", 12, 6);
		assertThat(overrides.valueToSave("renderDistance", 10)).isEqualTo(10);
		// From then on it is simply the player's: saved as it is.
		assertThat(overrides.valueToSave("renderDistance", 10)).isEqualTo(10);
	}

	@Test
	void aChangedOptionIsNotRestoredAfterSave() {
		var overrides = new SessionOverrides<String>();
		overrides.apply("renderDistance", 12, 6);
		overrides.valueToSave("renderDistance", 10);
		assertThat(overrides.valueAfterSave("renderDistance")).isNull();
	}

	@Test
	void inactiveWithNothingApplied() {
		var overrides = new SessionOverrides<String>();
		assertThat(overrides.active()).isFalse();
		overrides.apply("particles", "all", "decreased");
		assertThat(overrides.active()).isTrue();
		overrides.valueToSave("particles", "minimal");
		assertThat(overrides.active()).isFalse();
	}

	@Test
	void keysAreIndependent() {
		var overrides = new SessionOverrides<String>();
		overrides.apply("renderDistance", 12, 6);
		overrides.apply("clouds", "fancy", "off");
		overrides.valueToSave("renderDistance", 10);
		assertThat(overrides.valueToSave("clouds", "off")).isEqualTo("fancy");
		assertThat(overrides.valueAfterSave("clouds")).isEqualTo("off");
		assertThat(overrides.active()).isTrue();
	}

	@Test
	void anUnknownKeySavesWhatIsThere() {
		var overrides = new SessionOverrides<String>();
		assertThat(overrides.valueToSave("fov", 90)).isEqualTo(90);
		assertThat(overrides.valueAfterSave("fov")).isNull();
	}

	@Test
	void valuesCompareByEquality() {
		var overrides = new SessionOverrides<String>();
		overrides.apply("entityDistance", Double.valueOf(1.0), Double.valueOf(0.75));
		assertThat(overrides.valueToSave("entityDistance", Double.valueOf(0.75))).isEqualTo(1.0);
	}

	@Test
	void thePlayersChoiceIsRememberedOnceTheyChangeIt() {
		// The frame rate is set again at each new layout; once the player has chosen their own, pacing follows it.
		var overrides = new SessionOverrides<String>();
		assertThat(overrides.playersChoice("maxFps")).isNull();
		overrides.apply("maxFps", 120, 40);
		overrides.valueToSave("maxFps", 40);
		assertThat(overrides.playersChoice("maxFps")).isNull();
		overrides.valueToSave("maxFps", 90);
		assertThat(overrides.playersChoice("maxFps")).isEqualTo(90);
		// Set over again for the session (their 200 is above the host's refresh rate), then changed again: the latest.
		overrides.apply("maxFps", 200, 140);
		assertThat(overrides.playersChoice("maxFps")).isEqualTo(90);
		overrides.valueToSave("maxFps", 30);
		assertThat(overrides.playersChoice("maxFps")).isEqualTo(30);
	}

	@Test
	void anUnchangedOptionHasNoPlayersChoice() {
		var overrides = new SessionOverrides<String>();
		overrides.apply("renderDistance", 12, 6);
		assertThat(overrides.valueToSave("renderDistance", 6)).isEqualTo(12);
		assertThat(overrides.playersChoice("renderDistance")).isNull();
	}

	@Test
	void applyingAgainKeepsTheFirstOwnValue() {
		// The frame-rate cap is applied again on each new viewport; the player's own value stays the one from before.
		var overrides = new SessionOverrides<String>();
		overrides.apply("maxFps", 120, 60);
		overrides.apply("maxFps", 60, 45);
		assertThat(overrides.valueToSave("maxFps", 45)).isEqualTo(120);
		assertThat(overrides.valueAfterSave("maxFps")).isEqualTo(45);
	}
}
