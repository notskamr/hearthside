package dev.hearthside.fabric.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.hearthside.core.Role;
import dev.hearthside.fabric.client.HearthsideClient;
import dev.hearthside.fabric.client.host.SplitScreenScreen;

/**
 * Mod Menu's Configure button opens player 1's Split-screen settings (Spec E section 3.5), with or without a party (at
 * the title screen a change is only saved). Loaded only where Mod Menu is installed: an optional entrypoint, not a
 * dependency. A guest's game opens nothing: these are player 1's settings. The role is checked as Configure is pressed,
 * since Mod Menu may ask for the factory before Hearthside's client has started.
 */
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> HearthsideClient.role() == Role.HOST ? new SplitScreenScreen(parent) : null;
	}
}
