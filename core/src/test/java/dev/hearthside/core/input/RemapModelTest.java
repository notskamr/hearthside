package dev.hearthside.core.input;

import static dev.hearthside.core.input.ControlScheme.BEDROCK;
import static dev.hearthside.core.input.ControlScheme.JAVA_LIKE;
import static dev.hearthside.core.input.ControlScheme.SOUTHPAW;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RemapModelTest {
	/** Someone's own settings, none of them the defaults. */
	static final ControlScheme MINE = new ControlScheme(BEDROCK.gameplay(), BEDROCK.menu(), 2.0f, true, 0.2f, true, false,
			false);

	@Test
	void aPresetLayoutKeepsThePlayersSettings() {
		ControlScheme south = MINE.withLayoutOf(SOUTHPAW);
		assertThat(south.gameplay()).isEqualTo(SOUTHPAW.gameplay());
		assertThat(south.swapSticks()).isTrue();
		assertThat(south.lookSensitivity()).isEqualTo(2.0f);
		assertThat(south.invertY()).isTrue();
		assertThat(south.deadZone()).isEqualTo(0.2f);
		assertThat(south.toggleSneak()).isTrue();
		assertThat(south.autoJump()).isFalse();
		assertThat(south.showButtonGuide()).isFalse();
	}

	@Test
	void presetNamesMatchOnLayoutOnly() {
		assertThat(ControlScheme.presetName(MINE)).contains("Bedrock");
		assertThat(ControlScheme.presetName(MINE.withLayoutOf(JAVA_LIKE))).contains("Java-like");
		assertThat(ControlScheme.presetName(MINE.withSwap(Action.JUMP, Control.Y))).isEmpty();
	}

	@Test
	void startsOnTheProfilesControls() {
		var model = new RemapModel(MINE);
		assertThat(model.scheme()).isEqualTo(MINE);
		assertThat(model.name()).isEqualTo("Bedrock");
		assertThat(model.listening()).isNull();
	}

	@Test
	void theNextControlPressedIsBoundBySwapping() {
		var model = new RemapModel(MINE);
		model.listen(Action.JUMP);
		assertThat(model.listening()).isEqualTo(Action.JUMP);
		assertThat(model.press(Control.Y)).isTrue();
		assertThat(model.scheme().controlFor(Action.JUMP)).contains(Control.Y);
		// Inventory had Y: it takes Jump's old A, so nothing is lost.
		assertThat(model.scheme().controlFor(Action.INVENTORY)).contains(Control.A);
		assertThat(model.listening()).isNull();
		assertThat(model.name()).isEqualTo("Custom");
	}

	@Test
	void aPressWhileNotListeningChangesNothing() {
		var model = new RemapModel(MINE);
		assertThat(model.press(Control.Y)).isFalse();
		assertThat(model.scheme()).isEqualTo(MINE);
	}

	@Test
	void cancellingStopsListening() {
		var model = new RemapModel(MINE);
		model.listen(Action.JUMP);
		model.cancel();
		assertThat(model.listening()).isNull();
		assertThat(model.press(Control.Y)).isFalse();
	}

	@Test
	void presetsCycleAndComeBackToTheEditedControls() {
		var model = new RemapModel(MINE);
		model.listen(Action.JUMP);
		model.press(Control.Y);
		model.nextPreset();
		assertThat(model.name()).isEqualTo("Bedrock");
		model.nextPreset();
		assertThat(model.name()).isEqualTo("Java-like");
		model.nextPreset();
		assertThat(model.name()).isEqualTo("Southpaw");
		assertThat(model.scheme().swapSticks()).isTrue();
		assertThat(model.scheme().lookSensitivity()).isEqualTo(2.0f);
		model.nextPreset();
		assertThat(model.name()).isEqualTo("Custom");
		model.previousPreset();
		model.previousPreset();
		model.previousPreset();
		model.previousPreset();
		assertThat(model.name()).isEqualTo("Custom");
		assertThat(model.scheme().controlFor(Action.JUMP)).contains(Control.Y);
	}

	@Test
	void controlsMatchingAPresetAreNotListedTwice() {
		var model = new RemapModel(MINE);
		model.nextPreset();
		assertThat(model.name()).isEqualTo("Java-like");
		model.nextPreset();
		assertThat(model.name()).isEqualTo("Southpaw");
		model.nextPreset();
		assertThat(model.name()).isEqualTo("Bedrock");
		assertThat(model.scheme()).isEqualTo(MINE);
	}

	@Test
	void rebindingOnAPresetStartsFromThatPreset() {
		var model = new RemapModel(MINE);
		model.nextPreset();
		model.listen(Action.JUMP);
		model.press(Control.Y);
		assertThat(model.scheme()).isEqualTo(MINE.withLayoutOf(JAVA_LIKE).withSwap(Action.JUMP, Control.Y));
		assertThat(model.name()).isEqualTo("Custom");
		model.nextPreset();
		assertThat(model.name()).isEqualTo("Bedrock");
	}

	@Test
	void theTestAreaSaysWhatAControlDoes() {
		var model = new RemapModel(MINE);
		assertThat(model.actionOf(Control.RIGHT_TRIGGER)).contains(Action.ATTACK);
		assertThat(model.actionOf(Control.GUIDE)).isEmpty();
	}

	@Test
	void everyActionHasALabel() {
		for (Action action : Action.values()) {
			assertThat(RemapModel.label(action)).isNotBlank();
		}
		assertThat(RemapModel.label(Action.JUMP)).isEqualTo("Jump");
	}
}
