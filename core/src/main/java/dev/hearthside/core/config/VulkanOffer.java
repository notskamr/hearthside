package dev.hearthside.core.config;

/**
 * The player's answer to the offer to switch to Vulkan (Spec D §3.1), remembered in config/hearthside.json as
 * {@code "vulkanOffer"} so it is asked once.
 */
public enum VulkanOffer {
	/** Not answered yet: offered the next time Co-op opens on a PC that needs it. */
	PENDING,
	/** Switched: Minecraft's Graphics API option was set to Vulkan. */
	ACCEPTED,
	/** "Not now": not offered again. */
	DECLINED
}
