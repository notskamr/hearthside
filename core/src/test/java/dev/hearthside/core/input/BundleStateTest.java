package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Where a bundle is, for its hints (plan R8): from what the cursor holds and the slot under the pointer. Creative's item
 * picker handles its own clicks and never reaches the bundle's, so its slots get no bundle hints (Task 7 review).
 */
class BundleStateTest {
	// carriedBundle, carriedEmpty, hoveredBundle, hoveredEmpty, realSlot
	@Test
	void overABundleWithAnEmptyHand() {
		assertThat(BundleState.of(false, true, true, false, true)).isEqualTo(BundleState.OVER_BUNDLE_EMPTY_HAND);
	}

	@Test
	void overABundleWhileHolding() {
		assertThat(BundleState.of(false, false, true, false, true)).isEqualTo(BundleState.OVER_BUNDLE_HOLDING);
	}

	@Test
	void holdingABundleOverAStack() {
		assertThat(BundleState.of(true, false, false, false, true)).isEqualTo(BundleState.HOLDING_BUNDLE_OVER_STACK);
	}

	@Test
	void holdingABundleOverAnEmptySlot() {
		assertThat(BundleState.of(true, false, false, true, true)).isEqualTo(BundleState.HOLDING_BUNDLE_OVER_EMPTY);
	}

	@Test
	void holdingABundleComesFirst() {
		// A bundle held over another bundle scoops it in, as over any stack.
		assertThat(BundleState.of(true, false, true, false, true)).isEqualTo(BundleState.HOLDING_BUNDLE_OVER_STACK);
	}

	@Test
	void noBundleAnywhereIsNone() {
		assertThat(BundleState.of(false, true, false, false, true)).isEqualTo(BundleState.NONE);
		assertThat(BundleState.of(false, false, false, true, true)).isEqualTo(BundleState.NONE);
	}

	@Test
	void creativePickerSlotsGetNoBundleHints() {
		// Holding a bundle over a picker stack: a click there would replace (destroy) the bundle, not fill it.
		assertThat(BundleState.of(true, false, false, false, false)).isEqualTo(BundleState.NONE);
		assertThat(BundleState.of(true, false, false, true, false)).isEqualTo(BundleState.NONE);
		// A picker bundle with an empty hand: X takes a new bundle, the stick chooses nothing.
		assertThat(BundleState.of(false, true, true, false, false)).isEqualTo(BundleState.NONE);
		assertThat(BundleState.of(false, false, true, false, false)).isEqualTo(BundleState.NONE);
	}
}
