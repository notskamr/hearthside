package dev.hearthside.core.input;

import static dev.hearthside.core.input.ControlScheme.BEDROCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GamepadMappingTest {
	static final InputFrame NONE = InputFrame.NEUTRAL;

	static InputFrame sticks(float lx, float ly, float rx, float ry) {
		return new InputFrame(1, 0, lx, ly, rx, ry, 0, 0);
	}

	static InputFrame buttons(PadButton... held) {
		return new InputFrame(1, InputFrame.buttons(held), 0, 0, 0, 0, 0, 0);
	}

	static ControlScheme withSettings(float sensitivity, boolean invertY) {
		return new ControlScheme(BEDROCK.gameplay(), BEDROCK.menu(), sensitivity, invertY, BEDROCK.deadZone(),
				false, true, true);
	}

	@Test
	void deadZoneSwallowsDrift() {
		var intent = GamepadMapping.gameplay(BEDROCK, NONE, sticks(0.1f, 0.1f, 0.1f, -0.1f), 0.016f);
		assertThat(intent.moveX()).isZero();
		assertThat(intent.moveY()).isZero();
		assertThat(intent.yawDegrees()).isZero();
		assertThat(intent.pitchDegrees()).isZero();
	}

	@Test
	void fullForwardIsFullSpeed() {
		var intent = GamepadMapping.gameplay(BEDROCK, NONE, sticks(0, -1, 0, 0), 0.016f);
		assertThat(intent.moveY()).isCloseTo(1f, within(1e-4f));
		assertThat(intent.moveX()).isZero();
	}

	@Test
	void diagonalIsCappedAtOne() {
		var intent = GamepadMapping.gameplay(BEDROCK, NONE, sticks(1, 1, 0, 0), 0.016f);
		assertThat((float) Math.hypot(intent.moveX(), intent.moveY())).isCloseTo(1f, within(1e-4f));
	}

	@Test
	void lookUsesSquaredCurve() {
		var intent = GamepadMapping.gameplay(BEDROCK, NONE, sticks(0, 0, 0.575f, 0), 0.01f);
		assertThat(intent.yawDegrees()).isCloseTo(300f * 0.25f * 0.01f, within(1e-3f));
	}

	@Test
	void sensitivityScalesLook() {
		var normal = GamepadMapping.gameplay(BEDROCK, NONE, sticks(0, 0, 1, 0), 0.01f);
		var fast = GamepadMapping.gameplay(withSettings(2f, false), NONE, sticks(0, 0, 1, 0), 0.01f);
		assertThat(fast.yawDegrees()).isCloseTo(normal.yawDegrees() * 2, within(1e-4f));
	}

	@Test
	void invertYFlipsPitch() {
		var normal = GamepadMapping.gameplay(BEDROCK, NONE, sticks(0, 0, 0, 1), 0.01f);
		var inverted = GamepadMapping.gameplay(withSettings(1f, true), NONE, sticks(0, 0, 0, 1), 0.01f);
		assertThat(normal.pitchDegrees()).isPositive();
		assertThat(inverted.pitchDegrees()).isCloseTo(-normal.pitchDegrees(), within(1e-5f));
	}

	@Test
	void lookIsClampedForLongFrames() {
		var intent = GamepadMapping.gameplay(BEDROCK, NONE, sticks(0, 0, 1, 0), 0.5f);
		assertThat(intent.yawDegrees()).isCloseTo(300f * GamepadMapping.MAX_DT, within(1e-3f));
	}

	@Test
	void heldFollowsTheScheme() {
		var rebound = BEDROCK.withBinding(Action.JUMP, Control.Y);
		var intent = GamepadMapping.gameplay(rebound, NONE, buttons(PadButton.Y), 0.016f);
		assertThat(intent.held()).contains(Action.JUMP).doesNotContain(Action.INVENTORY);
	}

	@Test
	void pressesFireOnlyOnTheEdge() {
		assertThat(GamepadMapping.gameplay(BEDROCK, NONE, buttons(PadButton.Y), 0.016f).pressed()).contains(Action.INVENTORY);
		assertThat(GamepadMapping.gameplay(BEDROCK, buttons(PadButton.Y), buttons(PadButton.Y), 0.016f).pressed()).isEmpty();
	}

	@Test
	void triggersDriveHeldActions() {
		var intent = GamepadMapping.gameplay(BEDROCK, NONE, new InputFrame(1, 0, 0, 0, 0, 0, 0.9f, 0.9f), 0.016f);
		assertThat(intent.held()).contains(Action.ATTACK, Action.USE);
	}

	@Test
	void unboundActionNeverFires() {
		var all = new InputFrame(1, -1, 0, 0, 0, 0, 1, 1);
		var intent = GamepadMapping.gameplay(BEDROCK, NONE, all, 0.016f);
		assertThat(intent.held()).doesNotContain(Action.PICK_BLOCK);
		assertThat(intent.pressed()).doesNotContain(Action.PICK_BLOCK);
	}

	@Test
	void bumpersStepTheHotbar() {
		assertThat(GamepadMapping.gameplay(BEDROCK, NONE, buttons(PadButton.RIGHT_BUMPER), 0.016f).hotbarDelta()).isEqualTo(1);
		assertThat(GamepadMapping.gameplay(BEDROCK, NONE, buttons(PadButton.LEFT_BUMPER), 0.016f).hotbarDelta()).isEqualTo(-1);
		assertThat(GamepadMapping.gameplay(BEDROCK, NONE,
				buttons(PadButton.LEFT_BUMPER, PadButton.RIGHT_BUMPER), 0.016f).hotbarDelta()).isZero();
	}

	@Test
	void menuEdgesUseMenuBindings() {
		var down = GamepadMapping.menu(BEDROCK, NONE, buttons(PadButton.A, PadButton.Y), 0.016f);
		assertThat(down.down()).containsExactlyInAnyOrder(MenuAction.PICK_PLACE, MenuAction.QUICK_MOVE);
		var up = GamepadMapping.menu(BEDROCK, buttons(PadButton.A), NONE, 0.016f);
		assertThat(up.up()).containsExactly(MenuAction.PICK_PLACE);
	}

	@Test
	void startClosesMenus() {
		assertThat(GamepadMapping.menu(BEDROCK, NONE, buttons(PadButton.START), 0.016f).down()).contains(MenuAction.CLOSE);
	}

	@Test
	void dpadHopsInMenus() {
		var right = GamepadMapping.menu(BEDROCK, NONE, buttons(PadButton.DPAD_RIGHT), 0.016f);
		assertThat(right.hopX()).isEqualTo(1);
		assertThat(right.hopY()).isZero();
		var up = GamepadMapping.menu(BEDROCK, NONE, buttons(PadButton.DPAD_UP), 0.016f);
		assertThat(up.hopY()).isEqualTo(-1);
		assertThat(GamepadMapping.menu(BEDROCK, buttons(PadButton.DPAD_UP), buttons(PadButton.DPAD_UP), 0.016f).hopY()).isZero();
	}

	@Test
	void menuCursorFollowsLeftStick() {
		var intent = GamepadMapping.menu(BEDROCK, NONE, sticks(1, 0, 0, 0), 0.01f);
		assertThat(intent.cursorDx()).isCloseTo(GamepadMapping.CURSOR_PX_PER_SEC * 0.01f, within(1e-3f));
		assertThat(intent.cursorDy()).isZero();
	}

	@Test
	void menuScrollFollowsRightStick() {
		assertThat(GamepadMapping.menu(BEDROCK, NONE, sticks(0, 0, 0, -1), 0.05f).scroll()).isCloseTo(0.4f, within(1e-4f));
	}
}
