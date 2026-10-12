package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Drop on a controller as on Bedrock: a tap throws one; holding half a second throws the rest of the stack. */
class DropGestureTest {
	static final long MS = 1_000_000L;

	@Test
	void holdIsHalfASecond() {
		assertThat(DropGesture.HOLD.toMillis()).isEqualTo(500);
	}

	@Test
	void aTapDropsOne() {
		DropGesture gesture = new DropGesture();
		assertThat(gesture.step(true, 0)).contains(DropGesture.Drop.ONE);
		assertThat(gesture.step(true, 100 * MS)).isEmpty();
		assertThat(gesture.step(false, 200 * MS)).isEmpty();
		assertThat(gesture.step(false, 900 * MS)).isEmpty();
	}

	@Test
	void holdingHalfASecondDropsTheRestOfTheStack() {
		DropGesture gesture = new DropGesture();
		assertThat(gesture.step(true, 0)).contains(DropGesture.Drop.ONE);
		assertThat(gesture.step(true, 499 * MS)).isEmpty();
		assertThat(gesture.step(true, 500 * MS)).contains(DropGesture.Drop.STACK);
		assertThat(gesture.step(true, 900 * MS)).isEmpty();
		assertThat(gesture.step(true, 2000 * MS)).isEmpty();
	}

	@Test
	void aNewPressStartsOver() {
		DropGesture gesture = new DropGesture();
		gesture.step(true, 0);
		gesture.step(true, 600 * MS);
		gesture.step(false, 700 * MS);
		assertThat(gesture.step(true, 800 * MS)).contains(DropGesture.Drop.ONE);
		assertThat(gesture.step(true, 1299 * MS)).isEmpty();
		assertThat(gesture.step(true, 1300 * MS)).contains(DropGesture.Drop.STACK);
	}

	@Test
	void nothingWithoutAPress() {
		DropGesture gesture = new DropGesture();
		assertThat(gesture.step(false, 0)).isEqualTo(Optional.empty());
		assertThat(gesture.step(false, 600 * MS)).isEmpty();
	}

	@Test
	void dropStackIsAnActionPlayersCanBindButNoPresetBinds() {
		assertThat(Action.valueOf("DROP_STACK")).isNotNull();
		assertThat(RemapModel.label(Action.DROP_STACK)).isEqualTo("Drop stack");
		for (ControlScheme.Named preset : ControlScheme.PRESETS) {
			assertThat(preset.scheme().gameplay()).as(preset.name()).doesNotContainKey(Action.DROP_STACK);
		}
	}
}
