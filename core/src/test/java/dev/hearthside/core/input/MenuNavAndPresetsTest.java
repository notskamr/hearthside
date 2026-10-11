package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.input.MenuNav.Action;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuNavAndPresetsTest {
	static final long MS = 1_000_000L;

	static InputFrame frame(float leftX, float leftY, PadButton... held) {
		return new InputFrame(1, InputFrame.buttons(held), leftX, leftY, 0, 0, 0, 0);
	}

	@Test
	void menuNavReportsEdgesAndRepeats() {
		var nav = new MenuNav();
		assertThat(nav.step(frame(0, 0), 0)).isEmpty();
		assertThat(nav.step(frame(0, 0, PadButton.A), 10 * MS)).containsExactly(Action.ACCEPT);
		assertThat(nav.step(frame(0, 0, PadButton.A), 20 * MS)).isEmpty();
		assertThat(nav.step(frame(0, 0, PadButton.B, PadButton.X), 30 * MS)).containsExactlyInAnyOrder(Action.BACK, Action.MORE);
		assertThat(nav.step(frame(0, 0, PadButton.DPAD_DOWN), 40 * MS)).containsExactly(Action.DOWN);
		assertThat(nav.step(frame(0, 0, PadButton.DPAD_DOWN), 200 * MS)).isEmpty();
		assertThat(nav.step(frame(0, 0, PadButton.DPAD_DOWN), 340 * MS)).containsExactly(Action.DOWN);
	}

	@Test
	void menuNavIgnoresButtonsHeldAtStart() {
		// The A that opened a screen must not also press something on it.
		var nav = new MenuNav();
		assertThat(nav.step(frame(0, 0, PadButton.A), 0)).isEmpty();
		assertThat(nav.step(frame(0, 0), 10 * MS)).isEmpty();
		assertThat(nav.step(frame(0, 0, PadButton.A), 20 * MS)).containsExactly(Action.ACCEPT);
	}

	@Test
	void menuNavUsesTheStick() {
		var nav = new MenuNav();
		nav.step(frame(0, 0), 0);
		assertThat(nav.step(frame(0.9f, 0), 10 * MS)).containsExactly(Action.RIGHT);
		assertThat(nav.step(frame(0, 0), 20 * MS)).isEmpty();
		assertThat(nav.step(frame(0, -0.9f), 30 * MS)).containsExactly(Action.UP);
		assertThat(nav.step(frame(0.3f, 0), 40 * MS)).isEmpty();
	}

	@Test
	void javaLikeAndSouthpawBindings() {
		var java = ControlScheme.JAVA_LIKE;
		assertThat(java.controlFor(dev.hearthside.core.input.Action.SWAP_OFFHAND)).contains(Control.X);
		assertThat(java.controlFor(dev.hearthside.core.input.Action.PICK_BLOCK)).contains(Control.DPAD_LEFT);
		assertThat(java.controlFor(dev.hearthside.core.input.Action.CRAFTING)).isEmpty();
		assertThat(java.swapSticks()).isFalse();
		var south = ControlScheme.SOUTHPAW;
		assertThat(south.swapSticks()).isTrue();
		assertThat(south.controlFor(dev.hearthside.core.input.Action.ATTACK)).contains(Control.LEFT_TRIGGER);
		assertThat(south.controlFor(dev.hearthside.core.input.Action.USE)).contains(Control.RIGHT_TRIGGER);
		assertThat(south.controlFor(dev.hearthside.core.input.Action.HOTBAR_PREV)).contains(Control.RIGHT_BUMPER);
		assertThat(south.controlFor(dev.hearthside.core.input.Action.HOTBAR_NEXT)).contains(Control.LEFT_BUMPER);
		assertThat(ControlScheme.PRESETS).extracting(ControlScheme.Named::name)
				.containsExactly("Bedrock", "Java-like", "Southpaw");
	}

	@Test
	void remapSwapsAndStaysDistinct() {
		var scheme = ControlScheme.BEDROCK.withSwap(dev.hearthside.core.input.Action.JUMP, Control.Y);
		assertThat(scheme.controlFor(dev.hearthside.core.input.Action.JUMP)).contains(Control.Y);
		// Inventory had Y: it takes Jump's old A.
		assertThat(scheme.controlFor(dev.hearthside.core.input.Action.INVENTORY)).contains(Control.A);
		assertThat(List.copyOf(scheme.gameplay().values())).doesNotHaveDuplicates();
		// An unused control is simply taken.
		var free = ControlScheme.JAVA_LIKE.withSwap(dev.hearthside.core.input.Action.JUMP, Control.GUIDE);
		assertThat(free.controlFor(dev.hearthside.core.input.Action.JUMP)).contains(Control.GUIDE);
	}

	@Test
	void swapSticksMovesWithTheRightStick() {
		var right = new InputFrame(1, 0, 0, 0, 0, -1, 0, 0);
		var intent = GamepadMapping.gameplay(ControlScheme.SOUTHPAW, InputFrame.NEUTRAL, right, 0.016f);
		assertThat(intent.moveY()).isGreaterThan(0.5f);
		assertThat(intent.pitchDegrees()).isZero();
	}

	@Test
	void schemeJsonKeepsSwapSticks() {
		var back = ControlSchemeJson.fromJson(ControlSchemeJson.toJson(ControlScheme.SOUTHPAW));
		assertThat(back).isEqualTo(ControlScheme.SOUTHPAW);
		assertThat(ControlSchemeJson.fromJson("{}").swapSticks()).isFalse();
	}
}
