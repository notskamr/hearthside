package dev.hearthside.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MotionTest {
	@Test
	void emberFieldIsDeterministicForASeed() {
		var a = new EmberField(42, 30);
		var b = new EmberField(42, 30);
		for (int i = 0; i < 120; i++) {
			a.step(1 / 60.0, false);
			b.step(1 / 60.0, false);
		}
		assertThat(a.embers()).isNotEmpty().isEqualTo(b.embers());
	}

	@Test
	void embersRiseAndDieAtTheTop() {
		var field = new EmberField(1, 1);
		field.step(0.05, false);
		assertThat(field.embers()).hasSize(1);
		double y = field.embers().getFirst().y();
		field.step(0.5, false);
		assertThat(field.embers().getFirst().y()).isGreaterThan(y + 0.5 * EmberField.MIN_RISE - 1e-9);
		// At most 1 / MIN_RISE seconds later it has left the top.
		for (int i = 0; i < 70; i++) {
			field.step(0.1, false);
			assertThat(field.embers()).allSatisfy(e -> assertThat(e.y()).isBetween(0.0, 1.0));
		}
	}

	@Test
	void emberCountNeverExceedsMax() {
		var field = new EmberField(3, 12);
		for (int i = 0; i < 600; i++) {
			field.step(1 / 30.0, false);
			assertThat(field.embers().size()).isLessThanOrEqualTo(12);
		}
		assertThat(field.embers()).hasSizeGreaterThan(6);
	}

	@Test
	void reduceMotionClearsEmbers() {
		var field = new EmberField(5, 20);
		for (int i = 0; i < 60; i++) {
			field.step(1 / 30.0, false);
		}
		field.step(1 / 30.0, true);
		assertThat(field.embers()).isEmpty();
	}

	@Test
	void easeOutCubicEndsAtOneAndIsMonotonic() {
		assertThat(Tween.easeOutCubic(0)).isZero();
		assertThat(Tween.easeOutCubic(1)).isEqualTo(1.0);
		double last = 0;
		for (int i = 1; i <= 100; i++) {
			double v = Tween.easeOutCubic(i / 100.0);
			assertThat(v).isGreaterThanOrEqualTo(last);
			last = v;
		}
		assertThat(Tween.easeOutCubic(-1)).isZero();
		assertThat(Tween.easeOutCubic(2)).isEqualTo(1.0);
	}

	@Test
	void tweenRunsOverItsLength() {
		var tween = new Tween(Duration.ofMillis(250));
		tween.start(1_000_000_000L);
		assertThat(tween.progress(1_000_000_000L, false)).isZero();
		assertThat(tween.progress(1_125_000_000L, false)).isBetween(0.5, 1.0);
		assertThat(tween.progress(1_250_000_000L, false)).isEqualTo(1.0);
		assertThat(tween.progress(2_000_000_000L, false)).isEqualTo(1.0);
	}

	@Test
	void tweenIsDoneAtOnceWithReduceMotion() {
		var tween = new Tween(Duration.ofMillis(250));
		tween.start(0);
		assertThat(tween.progress(0, true)).isEqualTo(1.0);
	}

	@Test
	void unstartedTweenIsDone() {
		assertThat(new Tween(Duration.ofMillis(250)).progress(5, false)).isEqualTo(1.0);
	}
}
