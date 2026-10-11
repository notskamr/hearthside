package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ControlSchemeJsonProperties {
	@Property
	void neverThrows(@ForAll String json) {
		assertThat(ControlSchemeJson.fromJson(json)).isNotNull();
	}

	@Property
	void roundTripsAnyValidScheme(@ForAll("schemes") ControlScheme scheme) {
		assertThat(ControlSchemeJson.fromJson(ControlSchemeJson.toJson(scheme))).isEqualTo(scheme);
	}

	@Provide
	Arbitrary<ControlScheme> schemes() {
		Arbitrary<List<Action>> actions = Arbitraries.of(Action.class).list().ofMaxSize(10);
		Arbitrary<List<Control>> controls = Arbitraries.of(Control.class).list().ofSize(10);
		Arbitrary<Float> sensitivity = Arbitraries.floats().between(0.1f, 3.0f);
		Arbitrary<Float> deadZone = Arbitraries.floats().between(0.05f, 0.5f);
		Arbitrary<Boolean> flag = Arbitraries.of(true, false);
		return Combinators.combine(actions, controls, sensitivity, deadZone, flag, flag, flag, flag)
				.as((as, cs, sens, dz, invert, toggle, autoJump, guide) -> {
					ControlScheme s = ControlScheme.BEDROCK;
					for (int i = 0; i < as.size(); i++) {
						s = s.withBinding(as.get(i), cs.get(i));
					}
					return new ControlScheme(s.gameplay(), s.menu(), sens, invert, dz, toggle, autoJump, guide);
				});
	}
}
