package dev.hearthside.core.launch;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.config.VulkanOffer;
import org.junit.jupiter.api.Test;

/**
 * When player 1 is offered Vulkan (Spec D section 3.1): on OpenGL that can't share frames, where Vulkan can, never with
 * Iris (it can't start on Vulkan), and only until the player has answered. Only the two reasons a player might wonder
 * about are explained in the log.
 */
class VulkanOfferRuleTest {
	static VulkanOfferRule.Decision decide(boolean onOpenGl, boolean glCanImport, boolean vulkanCanShare, boolean iris,
			VulkanOffer stored) {
		return VulkanOfferRule.decide(onOpenGl, glCanImport, vulkanCanShare, iris, stored);
	}

	@Test
	void offersOnIntelLikeOpenGl() {
		var decision = decide(true, false, true, false, VulkanOffer.PENDING);
		assertThat(decision.offer()).isTrue();
		assertThat(decision.notOfferedBecause()).isEmpty();
	}

	@Test
	void neverWithIris() {
		var decision = decide(true, false, true, true, VulkanOffer.PENDING);
		assertThat(decision.offer()).isFalse();
		assertThat(decision.notOfferedBecause()).contains("Iris is installed");
	}

	@Test
	void irisIsTheReasonEvenWhenVulkanCantShare() {
		assertThat(decide(true, false, false, true, VulkanOffer.PENDING).notOfferedBecause()).contains("Iris is installed");
	}

	@Test
	void explainsWhenVulkanCantShare() {
		var decision = decide(true, false, false, false, VulkanOffer.PENDING);
		assertThat(decision.offer()).isFalse();
		assertThat(decision.notOfferedBecause()).contains("Vulkan can't share frames");
	}

	@Test
	void neverWhenGlCanShare() {
		var decision = decide(true, true, true, false, VulkanOffer.PENDING);
		assertThat(decision.offer()).isFalse();
		assertThat(decision.notOfferedBecause()).isEmpty();
	}

	@Test
	void neverOnVulkan() {
		var decision = decide(false, false, true, false, VulkanOffer.PENDING);
		assertThat(decision.offer()).isFalse();
		assertThat(decision.notOfferedBecause()).isEmpty();
	}

	@Test
	void neverOnceAnswered() {
		for (VulkanOffer answered : new VulkanOffer[] {VulkanOffer.ACCEPTED, VulkanOffer.DECLINED}) {
			var decision = decide(true, false, true, false, answered);
			assertThat(decision.offer()).isFalse();
			assertThat(decision.notOfferedBecause()).isEmpty();
			assertThat(decide(true, false, true, true, answered).notOfferedBecause()).isEmpty();
		}
	}
}
