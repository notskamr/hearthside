package dev.hearthside.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HearthsideMod implements ModInitializer {
	public static final String MOD_ID = "hearthside";
	public static final Logger LOGGER = LoggerFactory.getLogger("Hearthside");

	@Override
	public void onInitialize() {
		String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
		LOGGER.info("Hearthside {} initialising", version);
	}
}
