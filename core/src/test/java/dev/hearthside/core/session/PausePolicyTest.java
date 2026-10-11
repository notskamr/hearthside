package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.session.PausePolicy.LocalPlayer;
import dev.hearthside.core.session.SharedControl.SharedAction;
import java.util.List;
import org.junit.jupiter.api.Test;

class PausePolicyTest {
	private static LocalPlayer playing(int i, String name) {
		return new LocalPlayer(i, false, false, name);
	}

	private static LocalPlayer paused(int i, String name) {
		return new LocalPlayer(i, true, false, name);
	}

	@Test
	void hostAloneNeverForcesFreeze() {
		assertThat(PausePolicy.decide(List.of(paused(0, "Steve")), false).freeze()).isFalse();
	}

	@Test
	void allPausedFreezes() {
		assertThat(PausePolicy.decide(List.of(paused(0, "Steve"), paused(1, "Alex")), false).freeze()).isTrue();
	}

	@Test
	void onePlayingKeepsTheWorldRunning() {
		var decision = PausePolicy.decide(List.of(paused(0, "Steve"), playing(1, "Alex")), false);
		assertThat(decision.freeze()).isFalse();
	}

	@Test
	void disconnectedCountsAsPaused() {
		LocalPlayer dropped = new LocalPlayer(1, false, true, "Alex");
		assertThat(PausePolicy.decide(List.of(paused(0, "Steve"), dropped), false).freeze()).isTrue();
	}

	@Test
	void remotePlayerPreventsFreeze() {
		assertThat(PausePolicy.decide(List.of(paused(0, "Steve"), paused(1, "Alex")), true).freeze()).isFalse();
	}

	@Test
	void stillPlayingNamesActivePlayers() {
		var decision = PausePolicy.decide(List.of(paused(0, "Steve"), playing(1, "Alex"), playing(2, "Sam"),
				new LocalPlayer(3, false, true, "Jordan")), false);
		assertThat(decision.stillPlaying()).containsExactly("Alex", "Sam");
	}

	@Test
	void guestsCanNavigateAndPickWorld() {
		SharedControl control = new SharedControl(false);
		assertThat(control.canDo(1, SharedAction.NAVIGATE)).isTrue();
		assertThat(control.canDo(2, SharedAction.SELECT_WORLD)).isTrue();
		assertThat(control.canDo(3, SharedAction.CREATE_WORLD)).isTrue();
		assertThat(control.canDo(1, SharedAction.CONTINUE)).isTrue();
	}

	@Test
	void destructiveActionsNeedHost() {
		SharedControl control = new SharedControl(false);
		for (SharedAction action : List.of(SharedAction.DELETE_WORLD, SharedAction.REMOVE_PLAYER, SharedAction.END_SESSION)) {
			assertThat(control.canDo(0, action)).isTrue();
			assertThat(control.canDo(1, action)).as(action.name()).isFalse();
		}
	}

	@Test
	void hostOnlySettingLocksGuests() {
		SharedControl control = new SharedControl(true);
		assertThat(control.canDo(0, SharedAction.NAVIGATE)).isTrue();
		assertThat(control.canDo(1, SharedAction.NAVIGATE)).isFalse();
	}
}
