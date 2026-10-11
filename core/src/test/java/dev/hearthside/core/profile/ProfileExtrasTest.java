package dev.hearthside.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileExtrasTest {
	@Test
	void firstFreeSkipsTaken() {
		assertThat(PlayerColors.firstFree(List.of(PlayerColors.DEFAULTS[0], PlayerColors.DEFAULTS[2])))
				.isEqualTo(PlayerColors.DEFAULTS[1]);
	}

	@Test
	void allTakenReturnsFirst() {
		List<Integer> all = List.of(PlayerColors.DEFAULTS[0], PlayerColors.DEFAULTS[1], PlayerColors.DEFAULTS[2],
				PlayerColors.DEFAULTS[3]);
		assertThat(PlayerColors.firstFree(all)).isEqualTo(PlayerColors.DEFAULTS[0]);
	}

	@Test
	void defaultColoursMatchTheSpec() {
		assertThat(PlayerColors.DEFAULTS).containsExactly(0x3f7fd0, 0xd0603f, 0x3fa060, 0x9a5fd0);
	}

	@Test
	void settingsClampOutOfRange() {
		PersonalSettings s = new PersonalSettings(500, 99, true, false, false, null, 7f);
		assertThat(s.fov()).isEqualTo(110);
		assertThat(s.guiScale()).isEqualTo(6);
		assertThat(s.volume()).isEqualTo(1f);
		PersonalSettings low = new PersonalSettings(1, -3, true, false, false, null, -1f);
		assertThat(low.fov()).isEqualTo(30);
		assertThat(low.guiScale()).isZero();
		assertThat(low.volume()).isZero();
	}

	@Test
	void alexIsSlimSteveIsNot() {
		assertThat(SkinRef.defaultSkin("alex").slim()).isTrue();
		assertThat(SkinRef.defaultSkin("steve").slim()).isFalse();
	}

	@Test
	void borrowedSkinKeepsTheUsername() {
		SkinRef borrowed = SkinRef.borrowed("Notch", false);
		assertThat(borrowed.kind()).isEqualTo(SkinRef.Kind.BORROWED);
		assertThat(borrowed.value()).isEqualTo("Notch");
	}
}
