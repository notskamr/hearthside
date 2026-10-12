package dev.hearthside.fabric.client.dev;

import dev.hearthside.fabric.HearthsideMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * E2E (Q1b), on a guest's game: two seconds into the world it saves its options once, as closing Video Settings or any
 * options screen does, and logs it. Enabled by {@value #ENV}{@code =1} in the guest's environment, which a host started
 * with {@code -Dhearthside.e2e.guestSaveOptions=true} sets (satellites get no {@code -Dhearthside.*}).
 */
public final class GuestSavesOptions {
	public static final String ENV = "HEARTHSIDE_E2E_GUEST_SAVE_OPTIONS";

	private int inWorldTicks;

	private GuestSavesOptions() {}

	public static void registerIfEnabled() {
		if ("1".equals(System.getenv(ENV))) {
			ClientTickEvents.END_CLIENT_TICK.register(new GuestSavesOptions()::tick);
		}
	}

	private void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null || ++inWorldTicks != 40) {
			return;
		}
		minecraft.options.save();
		HearthsideMod.LOGGER.info("Hearthside e2e guest saved its options (render distance {} in play)",
				minecraft.options.renderDistance().get());
	}
}
