package dev.hearthside.core.input;

import static dev.hearthside.core.input.ControlScheme.BEDROCK;
import static dev.hearthside.core.input.ControlScheme.SOUTHPAW;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Bundle hints (Spec D section 3.5, plan R8), following what vanilla does with each click: over a bundle with an empty
 * hand the look stick chooses and X takes the chosen item out; with an item held, A puts it in; holding a bundle, A
 * scoops a stack into it and X over an empty slot takes one out.
 */
class ButtonGuideBundleTest {
	static GuideContext bundle(BundleState state, boolean holding) {
		return new GuideContext(true, false, false, false, false, holding, false, state);
	}

	static List<String> labels(GuideContext context, ControlScheme scheme) {
		return ButtonGuideTest.labels(ButtonGuide.prompts(context, scheme));
	}

	@Test
	void overABundleWithAnEmptyHandShowsChooseAndTakeOut() {
		assertThat(labels(bundle(BundleState.OVER_BUNDLE_EMPTY_HAND, false), BEDROCK)).containsExactly(
				"A hearthside.guide.take", "RIGHT_STICK hearthside.guide.bundle_choose",
				"X hearthside.guide.bundle_take_out", "Y hearthside.guide.quick_move", "B hearthside.guide.close");
	}

	@Test
	void overABundleWhileHoldingShowsPutIn() {
		assertThat(labels(bundle(BundleState.OVER_BUNDLE_HOLDING, true), BEDROCK))
				.containsExactly("A hearthside.guide.bundle_put_in", "B hearthside.guide.close");
	}

	@Test
	void holdingABundleOverAStackShowsPutIn() {
		assertThat(labels(bundle(BundleState.HOLDING_BUNDLE_OVER_STACK, true), BEDROCK))
				.containsExactly("A hearthside.guide.bundle_put_in", "B hearthside.guide.close");
	}

	@Test
	void holdingABundleOverAnEmptySlotShowsTakeOut() {
		assertThat(labels(bundle(BundleState.HOLDING_BUNDLE_OVER_EMPTY, true), BEDROCK)).containsExactly(
				"A hearthside.guide.place", "X hearthside.guide.bundle_take_out", "B hearthside.guide.close");
	}

	@Test
	void noBundleKeepsTheUsualContainerPrompts() {
		assertThat(labels(bundle(BundleState.NONE, false), BEDROCK))
				.isEqualTo(labels(new GuideContext(true, false, false, false, false, false, false), BEDROCK));
		assertThat(labels(bundle(BundleState.NONE, true), BEDROCK))
				.isEqualTo(labels(new GuideContext(true, false, false, false, false, true, false), BEDROCK));
	}

	@Test
	void chooseUsesTheLookStick() {
		assertThat(labels(bundle(BundleState.OVER_BUNDLE_EMPTY_HAND, false), SOUTHPAW))
				.contains("LEFT_STICK hearthside.guide.bundle_choose")
				.doesNotContain("RIGHT_STICK hearthside.guide.bundle_choose");
	}

	@Test
	void bundleStatesOutsideAContainerChangeNothing() {
		var world = new GuideContext(false, false, true, false, false, false, false, BundleState.OVER_BUNDLE_EMPTY_HAND);
		assertThat(labels(world, BEDROCK))
				.isEqualTo(labels(new GuideContext(false, false, true, false, false, false, false), BEDROCK));
	}
}
