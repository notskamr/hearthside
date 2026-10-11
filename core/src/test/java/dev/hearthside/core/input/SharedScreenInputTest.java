package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SharedScreenInputTest {
	static InputFrame frame(float leftX, PadButton... held) {
		return new InputFrame(7, InputFrame.buttons(held), leftX, 0, 0, 0, 0, 0);
	}

	@Test
	void hostInUseWins() {
		var host = frame(0.8f);
		assertThat(SharedScreenInput.drive(Map.of(0, host, 1, frame(0, PadButton.A)), 0)).isEqualTo(host);
	}

	@Test
	void idleHostLetsTheLowestActiveGuestDrive() {
		var two = frame(0, PadButton.A);
		var three = frame(0.9f);
		assertThat(SharedScreenInput.drive(Map.of(0, frame(0), 2, two, 3, three), 0)).isEqualTo(two);
	}

	@Test
	void guestsWorkWithoutAHostPad() {
		var one = frame(-0.5f);
		assertThat(SharedScreenInput.drive(Map.of(1, one), 0)).isEqualTo(one);
	}

	@Test
	void maskedButtonsAreRemovedFromGuestsOnly() {
		int mask = InputFrame.buttons(PadButton.B, PadButton.START);
		assertThat(SharedScreenInput.drive(Map.of(1, frame(0, PadButton.B, PadButton.A)), mask).buttons())
				.isEqualTo(InputFrame.buttons(PadButton.A));
		// A guest holding only a masked button is idle, not driving.
		assertThat(SharedScreenInput.drive(Map.of(1, frame(0, PadButton.B)), mask)).isEqualTo(InputFrame.NEUTRAL);
		assertThat(SharedScreenInput.drive(Map.of(0, frame(0, PadButton.B)), mask).buttons())
				.isEqualTo(InputFrame.buttons(PadButton.B));
	}

	@Test
	void smallStickDriftIsIdle() {
		var host = frame(0);
		assertThat(SharedScreenInput.drive(Map.of(0, host, 1, frame(0.1f)), 0)).isEqualTo(host);
	}

	@Test
	void nobodyActiveGivesTheHostFrameOrNeutral() {
		assertThat(SharedScreenInput.drive(Map.of(), 0)).isEqualTo(InputFrame.NEUTRAL);
		var host = frame(0);
		assertThat(SharedScreenInput.drive(Map.of(0, host, 1, frame(0)), 0)).isEqualTo(host);
	}
}
