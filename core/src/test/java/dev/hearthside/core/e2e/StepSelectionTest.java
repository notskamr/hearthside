package dev.hearthside.core.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StepSelectionTest {
	static final List<String> ORDERED = List.of("1", "C1", "C2", "C7", "C7b", "D5", "D6");
	static final Map<String, List<String>> NEEDS = Map.of(
			"C2", List.of("1"), "C7", List.of("C2"), "C7b", List.of("C7"), "D5", List.of("C7b"), "D6", List.of("D5"));

	@Test
	void withoutOnlyEveryStepRuns() {
		StepSelection s = StepSelection.parse("", ORDERED, NEEDS);
		assertThat(ORDERED).allMatch(s::runs);
		assertThat(s.everything()).isTrue();
		assertThat(StepSelection.parse(null, ORDERED, NEEDS).everything()).isTrue();
	}

	@Test
	void onlyRunsTheNamedStepsAndWhatTheyNeedTransitively() {
		StepSelection s = StepSelection.parse("D5", ORDERED, NEEDS);
		assertThat(ORDERED.stream().filter(s::runs)).containsExactly("1", "C2", "C7", "C7b", "D5");
		assertThat(s.everything()).isFalse();
	}

	@Test
	void idsAreCommaSeparatedAndTrimmed() {
		StepSelection s = StepSelection.parse(" C2 , D6 ", ORDERED, NEEDS);
		assertThat(s.runs("C2")).isTrue();
		assertThat(s.runs("D6")).isTrue();
		assertThat(s.runs("C1")).isFalse();
	}

	@Test
	void anUnknownStepIsRefusedNamingTheKnownOnes() {
		assertThatThrownBy(() -> StepSelection.parse("Z9", ORDERED, NEEDS))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Z9").hasMessageContaining("C7b");
	}

	@Test
	void aNeedThatComesLaterIsRefused() {
		assertThatThrownBy(() -> StepSelection.parse("", ORDERED, Map.of("C2", List.of("D5"))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("C2").hasMessageContaining("D5");
	}

	@Test
	void anyRunsTellsWhetherASectionIsNeeded() {
		StepSelection s = StepSelection.parse("C2", ORDERED, NEEDS);
		assertThat(s.anyRuns(List.of("C7", "C2"))).isTrue();
		assertThat(s.anyRuns(List.of("D6"))).isFalse();
	}
}
