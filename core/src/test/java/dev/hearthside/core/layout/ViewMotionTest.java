package dev.hearthside.core.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ViewMotionTest {
	static final long MS = 1_000_000L;
	static final Rect FULL = new Rect(0, 0, 1000, 500);
	static final Rect LEFT = new Rect(0, 0, 499, 500);
	static final Rect RIGHT = new Rect(501, 0, 499, 500);

	static ViewFrame frame(List<ViewFrame> frames, int seat) {
		return frames.stream().filter(f -> f.seat() == seat).findFirst().orElse(null);
	}

	/** Player 1 alone, then a second seat arriving at {@code t}. */
	static ViewMotion twoFrom(long t) {
		var motion = new ViewMotion();
		motion.retarget(Map.of(0, FULL), 0, false);
		motion.retarget(Map.of(0, LEFT, 1, RIGHT), t, false);
		return motion;
	}

	@Test
	void firstLayoutAppearsWithoutGliding() {
		var motion = new ViewMotion();
		motion.retarget(Map.of(0, LEFT, 1, RIGHT), 0, false);
		assertThat(motion.frames(0)).containsExactly(new ViewFrame(0, LEFT, 1f), new ViewFrame(1, RIGHT, 1f));
		assertThat(motion.moving(0)).isFalse();
	}

	@Test
	void glideIsEasedOverThreeHundredMs() {
		var motion = twoFrom(0);
		assertThat(frame(motion.frames(0), 0).rect()).isEqualTo(FULL);
		assertThat(frame(motion.frames(150 * MS), 0).rect().width()).isBetween(749, 750);
		assertThat(frame(motion.frames(300 * MS), 0).rect()).isEqualTo(LEFT);
		assertThat(motion.moving(300 * MS)).isFalse();
		assertThat(motion.target(0)).contains(LEFT);
	}

	@Test
	void newSeatFadesInAtItsTarget() {
		var motion = twoFrom(0);
		assertThat(frame(motion.frames(0), 1)).isEqualTo(new ViewFrame(1, RIGHT, 0f));
		ViewFrame mid = frame(motion.frames(150 * MS), 1);
		assertThat(mid.rect()).isEqualTo(RIGHT);
		assertThat(mid.alpha()).isCloseTo(0.5f, within(0.01f));
		assertThat(frame(motion.frames(300 * MS), 1).alpha()).isEqualTo(1f);
	}

	@Test
	void retargetMidGlideStartsFromCurrentRects() {
		var motion = twoFrom(0);
		Rect at100 = frame(motion.frames(100 * MS), 0).rect();
		Rect third = new Rect(0, 0, 333, 500);
		motion.retarget(Map.of(0, third, 1, RIGHT), 100 * MS, false);
		assertThat(frame(motion.frames(100 * MS), 0).rect()).isEqualTo(at100);
		assertThat(frame(motion.frames(250 * MS), 0).rect()).isNotEqualTo(third);
		assertThat(frame(motion.frames(400 * MS), 0).rect()).isEqualTo(third);
	}

	@Test
	void leaveFadesThenGlides() {
		var motion = twoFrom(0);
		long t = 1000 * MS;
		motion.leave(1, Map.of(0, FULL), t, false);
		assertThat(frame(motion.frames(t + 125 * MS), 1).alpha()).isCloseTo(0.5f, within(0.01f));
		assertThat(frame(motion.frames(t + 125 * MS), 0).rect()).isEqualTo(LEFT);
		assertThat(frame(motion.frames(t + 250 * MS), 1)).isNull();
		assertThat(frame(motion.frames(t + 250 * MS), 0).rect()).isEqualTo(LEFT);
		assertThat(frame(motion.frames(t + 400 * MS), 0).rect().width()).isBetween(749, 750);
		assertThat(frame(motion.frames(t + 550 * MS), 0).rect()).isEqualTo(FULL);
		assertThat(motion.moving(t + 550 * MS)).isFalse();
	}

	@Test
	void windowResizeSnaps() {
		var motion = twoFrom(0);
		Rect wideLeft = new Rect(0, 0, 799, 600);
		Rect wideRight = new Rect(801, 0, 799, 600);
		motion.snap(Map.of(0, wideLeft, 1, wideRight));
		assertThat(motion.frames(50 * MS)).containsExactly(new ViewFrame(0, wideLeft, 1f), new ViewFrame(1, wideRight, 1f));
		assertThat(motion.moving(50 * MS)).isFalse();
	}

	@Test
	void reduceMotionSnaps() {
		var motion = new ViewMotion();
		motion.retarget(Map.of(0, FULL), 0, true);
		motion.retarget(Map.of(0, LEFT, 1, RIGHT), 0, true);
		assertThat(motion.frames(0)).containsExactly(new ViewFrame(0, LEFT, 1f), new ViewFrame(1, RIGHT, 1f));
		motion.leave(1, Map.of(0, FULL), 10 * MS, true);
		assertThat(motion.frames(10 * MS)).containsExactly(new ViewFrame(0, FULL, 1f));
		assertThat(motion.moving(10 * MS)).isFalse();
	}

	@Test
	void easingIsSymmetric() {
		assertThat(Ease.inOutCubic(0)).isEqualTo(0);
		assertThat(Ease.inOutCubic(0.5)).isEqualTo(0.5);
		assertThat(Ease.inOutCubic(1)).isEqualTo(1);
		assertThat(Ease.inOutCubic(0.25) + Ease.inOutCubic(0.75)).isCloseTo(1.0, within(1e-9));
		assertThat(Ease.outCubic(0.5)).isEqualTo(0.875);
	}
}
