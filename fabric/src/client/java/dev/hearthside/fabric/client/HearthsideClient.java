package dev.hearthside.fabric.client;

import dev.hearthside.core.Role;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.compat.HearthsideMixinPlugin;
import dev.hearthside.fabric.client.compositor.ViewPipeline;
import dev.hearthside.fabric.client.dev.E2eDriver;
import dev.hearthside.fabric.client.dev.GuestFullscreenAttempt;
import dev.hearthside.fabric.client.dev.GuestSavesOptions;
import dev.hearthside.fabric.client.host.HostCommands;
import dev.hearthside.fabric.client.host.Party;
import dev.hearthside.fabric.client.host.TitleCoopButton;
import dev.hearthside.fabric.client.input.FrameHook;
import dev.hearthside.fabric.client.satellite.GuestVideoSettings;
import dev.hearthside.fabric.client.satellite.SatelliteBoot;
import dev.hearthside.fabric.client.window.FocusLog;
import dev.hearthside.fabric.client.menu.TitleInput;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

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
		FrameHook.register(new FocusLog());
		if (role == Role.HOST) {
			Party.register();
			HostCommands.register();
			TitleCoopButton.register();
			ViewPipeline.registerReloadListener();
			ClientLifecycleEvents.CLIENT_STARTED.register(TitleInput::register);
			// Whether pausing together's freeze hook applied, logged as the game starts if it didn't.
			ClientLifecycleEvents.CLIENT_STARTED.register(client -> HearthsideMixinPlugin.freezeHookApplied());
			E2eDriver.registerIfEnabled();
		} else {
			// Only player 1's game freezes the world: the class kept for that look is let go.
			ClientLifecycleEvents.CLIENT_STARTED.register(client -> HearthsideMixinPlugin.forget());
			SatelliteBoot.register();
			GuestVideoSettings.register();
			GuestFullscreenAttempt.registerIfEnabled();
			GuestSavesOptions.registerIfEnabled();
		}
	}
}
