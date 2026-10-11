package dev.hearthside.core.layout;

import static dev.hearthside.core.layout.TwoPlayerSplit.HORIZONTAL;
import static dev.hearthside.core.layout.TwoPlayerSplit.VERTICAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConsoleLayoutTest {
	final Rect hd = new Rect(0, 0, 1920, 1080);

	@Test
	void onePlayerGetsWholeScreen() {
		assertThat(new ConsoleLayout(VERTICAL).layout(1, hd)).containsExactly(hd);
	}

	@Test
	void twoPlayersVertical() {
		assertThat(new ConsoleLayout(VERTICAL).layout(2, hd))
				.containsExactly(new Rect(0, 0, 960, 1080), new Rect(960, 0, 960, 1080));
	}

	@Test
	void twoPlayersHorizontal() {
		assertThat(new ConsoleLayout(HORIZONTAL).layout(2, hd))
				.containsExactly(new Rect(0, 0, 1920, 540), new Rect(0, 540, 1920, 540));
	}

	@Test
	void threePlayersTopWide() {
		assertThat(new ConsoleLayout(VERTICAL).layout(3, hd)).containsExactly(
				new Rect(0, 0, 1920, 540), new Rect(0, 540, 960, 540), new Rect(960, 540, 960, 540));
	}

	@Test
	void fourPlayersOddScreenGivesRemainderToRightAndBottom() {
		assertThat(new ConsoleLayout(VERTICAL).layout(4, new Rect(100, 50, 1921, 1081))).containsExactly(
				new Rect(100, 50, 960, 540), new Rect(1060, 50, 961, 540),
				new Rect(100, 590, 960, 541), new Rect(1060, 590, 961, 541));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 5, -1})
	void rejectsBadPlayerCounts(int n) {
		assertThatThrownBy(() -> new ConsoleLayout(VERTICAL).layout(n, hd))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsDegenerateScreen() {
		assertThatThrownBy(() -> new ConsoleLayout(VERTICAL).layout(2, new Rect(0, 0, 1, 1080)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rectRejectsNegativeSize() {
		assertThatThrownBy(() -> new Rect(0, 0, -1, 10)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void edgeSharingRectsDoNotIntersect() {
		assertThat(new Rect(0, 0, 10, 10).intersects(new Rect(10, 0, 10, 10))).isFalse();
		assertThat(new Rect(0, 0, 10, 10).intersects(new Rect(9, 9, 10, 10))).isTrue();
	}
}
