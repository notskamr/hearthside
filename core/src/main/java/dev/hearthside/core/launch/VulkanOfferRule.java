package dev.hearthside.core.launch;

import dev.hearthside.core.config.VulkanOffer;
import java.util.Objects;
import java.util.Optional;

/**
 * Whether player 1 is offered Vulkan when Co-op opens (Spec D §3.1): on OpenGL that can't share frames between games,
 * where Vulkan on the same GPU can, never with Iris (it aborts the game on Vulkan), and only until the player has
 * answered. Only the two reasons a player might wonder about are explained, for the log.
 */
public final class VulkanOfferRule {
	public static final String IRIS = "Iris is installed";
	public static final String VULKAN_CANT_SHARE = "Vulkan can't share frames";

	private VulkanOfferRule() {}

	/** Whether to show the offer, and why not when the player might wonder. */
	public record Decision(boolean offer, Optional<String> notOfferedBecause) {
		public Decision {
			Objects.requireNonNull(notOfferedBecause, "notOfferedBecause");
		}

		static Decision silentlyNot() {
			return new Decision(false, Optional.empty());
		}

		static Decision not(String because) {
			return new Decision(false, Optional.of(because));
		}
	}

	/**
	 * @param onOpenGl player 1 renders with OpenGL
	 * @param glCanImport OpenGL can import other games' frames, so nothing is copied
	 * @param vulkanCanShare a Vulkan device on this GPU can share frames
	 * @param irisLoaded the Iris mod is installed
	 * @param stored the remembered answer
	 */
	public static Decision decide(boolean onOpenGl, boolean glCanImport, boolean vulkanCanShare, boolean irisLoaded,
			VulkanOffer stored) {
		Objects.requireNonNull(stored, "stored");
		if (!onOpenGl || glCanImport || stored != VulkanOffer.PENDING) {
			return Decision.silentlyNot();
		}
		if (irisLoaded) {
			return Decision.not(IRIS);
		}
		if (!vulkanCanShare) {
			return Decision.not(VULKAN_CANT_SHARE);
		}
		return new Decision(true, Optional.empty());
	}
}
