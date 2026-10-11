package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.FloatRange;

class GamepadMappingProperties {
	@Property
	void moveVectorNeverExceedsOne(@ForAll @FloatRange(min = -1, max = 1) float x,
			@ForAll @FloatRange(min = -1, max = 1) float y) {
		var intent = GamepadMapping.gameplay(ControlScheme.BEDROCK, InputFrame.NEUTRAL,
				new InputFrame(1, 0, x, y, 0, 0, 0, 0), 0.016f);
		assertThat(Math.hypot(intent.moveX(), intent.moveY())).isLessThanOrEqualTo(1.0 + 1e-5);
	}

	@Property
	void lookIsBoundedByMaxDt(@ForAll @FloatRange(min = -1, max = 1) float rx,
			@ForAll @FloatRange(min = -1, max = 1) float ry,
			@ForAll @FloatRange(min = 0, max = 10) float dt,
			@ForAll @FloatRange(min = 0.1f, max = 3f) float sensitivity) {
		var scheme = new ControlScheme(ControlScheme.BEDROCK.gameplay(), ControlScheme.BEDROCK.menu(), sensitivity,
				false, 0.15f, false, true, true);
		var intent = GamepadMapping.gameplay(scheme, InputFrame.NEUTRAL, new InputFrame(1, 0, 0, 0, rx, ry, 0, 0), dt);
		assertThat(Math.abs(intent.yawDegrees())).isLessThanOrEqualTo(300f * 3f * GamepadMapping.MAX_DT + 1e-3f);
		assertThat(Math.abs(intent.pitchDegrees())).isLessThanOrEqualTo(200f * 3f * GamepadMapping.MAX_DT + 1e-3f);
	}
}
