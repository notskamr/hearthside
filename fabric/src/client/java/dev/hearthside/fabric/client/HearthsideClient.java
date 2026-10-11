package dev.hearthside.fabric.client;

import dev.hearthside.core.Role;
import dev.hearthside.fabric.HearthsideMod;
import net.fabricmc.api.ClientModInitializer;

public final class HearthsideClient implements ClientModInitializer {
	private static Role role = Role.HOST;

	/** The role this client was started in; satellites are launched with {@code -Dhearthside.role=satellite}. */
	public static Role role() {
		return role;
	}

	@Override
	public void onInitializeClient() {
		role = Role.fromProperty(System.getProperty(Role.PROPERTY));
		HearthsideMod.LOGGER.info("Hearthside client starting as {}", role);
	}
}
