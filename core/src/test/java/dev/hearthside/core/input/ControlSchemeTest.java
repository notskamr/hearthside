package dev.hearthside.core.input;

import static dev.hearthside.core.input.ControlScheme.BEDROCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ControlSchemeTest {
	@Test
	void bedrockMatchesTheWikiLayout() {
		assertThat(BEDROCK.gameplay()).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
				Map.entry(Action.JUMP, Control.A),
				Map.entry(Action.SNEAK, Control.B),
				Map.entry(Action.CRAFTING, Control.X),
				Map.entry(Action.INVENTORY, Control.Y),
				Map.entry(Action.ATTACK, Control.RIGHT_TRIGGER),
				Map.entry(Action.USE, Control.LEFT_TRIGGER),
				Map.entry(Action.HOTBAR_PREV, Control.LEFT_BUMPER),
				Map.entry(Action.HOTBAR_NEXT, Control.RIGHT_BUMPER),
				Map.entry(Action.SPRINT, Control.LEFT_STICK),
				Map.entry(Action.DROP, Control.DPAD_DOWN),
				Map.entry(Action.PERSPECTIVE, Control.DPAD_UP),
				Map.entry(Action.CHAT, Control.DPAD_RIGHT),
				Map.entry(Action.SWAP_OFFHAND, Control.DPAD_LEFT),
				Map.entry(Action.PAUSE, Control.START),
				Map.entry(Action.PLAYER_LIST, Control.BACK)));
		assertThat(BEDROCK.controlFor(Action.PICK_BLOCK)).isEmpty();
		assertThat(BEDROCK.menu()).containsExactlyInAnyOrderEntriesOf(Map.of(
				MenuAction.PICK_PLACE, Control.A,
				MenuAction.TAKE_HALF, Control.X,
				MenuAction.QUICK_MOVE, Control.Y,
				MenuAction.CLOSE, Control.B));
		assertThat(BEDROCK.lookSensitivity()).isEqualTo(1.0f);
		assertThat(BEDROCK.invertY()).isFalse();
		assertThat(BEDROCK.deadZone()).isEqualTo(0.15f);
		assertThat(BEDROCK.toggleSneak()).isFalse();
		assertThat(BEDROCK.autoJump()).isTrue();
		assertThat(BEDROCK.showButtonGuide()).isTrue();
	}

	@Test
	void sensitivityAndDeadZoneAreClamped() {
		var wild = new ControlScheme(BEDROCK.gameplay(), BEDROCK.menu(), 50f, false, 0.9f, false, true, true);
		assertThat(wild.lookSensitivity()).isEqualTo(3.0f);
		assertThat(wild.deadZone()).isEqualTo(0.5f);
		var tiny = new ControlScheme(BEDROCK.gameplay(), BEDROCK.menu(), 0f, false, 0f, false, true, true);
		assertThat(tiny.lookSensitivity()).isEqualTo(0.1f);
		assertThat(tiny.deadZone()).isEqualTo(0.05f);
	}

	@Test
	void oneControlCannotDriveTwoActions() {
		assertThatThrownBy(() -> new ControlScheme(Map.of(Action.JUMP, Control.A, Action.SNEAK, Control.A),
				BEDROCK.menu(), 1f, false, 0.15f, false, true, true)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rebindingMovesTheControl() {
		var scheme = BEDROCK.withBinding(Action.DROP, Control.A);
		assertThat(scheme.controlFor(Action.DROP)).contains(Control.A);
		assertThat(scheme.controlFor(Action.JUMP)).isEmpty();
	}

	@Test
	void triggersCountAsDownPastHalf() {
		assertThat(Control.RIGHT_TRIGGER.isDown(new InputFrame(1, 0, 0, 0, 0, 0, 0, 0.6f))).isTrue();
		assertThat(Control.RIGHT_TRIGGER.isDown(new InputFrame(1, 0, 0, 0, 0, 0, 0, 0.4f))).isFalse();
		assertThat(Control.A.isDown(new InputFrame(1, InputFrame.buttons(PadButton.A), 0, 0, 0, 0, 0, 0))).isTrue();
	}
}
