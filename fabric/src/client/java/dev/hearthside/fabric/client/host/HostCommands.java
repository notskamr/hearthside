package dev.hearthside.fabric.client.host;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.hearthside.core.launch.UnsupportedLauncherException;
import dev.hearthside.core.profile.GuestNames;
import dev.hearthside.fabric.HearthsideMod;
import java.io.IOException;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * {@code /hearthside join <name>} and {@code /hearthside leave <name>}: a developer stand-in for the
 * Co-op lobby that arrives in Plan 4.
 */
public final class HostCommands {
	private HostCommands() {}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
				literal("hearthside")
						.then(literal("join").then(argument("name", StringArgumentType.word()).executes(HostCommands::join)))
						.then(literal("leave").then(argument("name", StringArgumentType.word()).executes(HostCommands::leave)))
						.then(literal("pad").executes(HostCommands::pad))));
	}

	private static int pad(CommandContext<FabricClientCommandSource> ctx) {
		HostSession session = HostSession.current();
		if (session == null) {
			ctx.getSource().sendError(Component.literal("Open a singleplayer world first."));
			return 0;
		}
		try {
			session.armHostClaim();
			ctx.getSource().sendFeedback(Component.literal("Press A on the controller you want to use."));
			return 1;
		} catch (IllegalStateException e) {
			ctx.getSource().sendError(Component.literal(e.getMessage()));
			return 0;
		}
	}

	private static int join(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		HostSession session = HostSession.current();
		if (session == null) {
			ctx.getSource().sendError(Component.literal("Open a singleplayer world first."));
			return 0;
		}
		if (!GuestNames.isValid(name)) {
			ctx.getSource().sendError(Component.literal("Guest names use 3-16 letters, digits or underscores."));
			return 0;
		}
		try {
			session.join(name);
			ctx.getSource().sendFeedback(Component.literal("Starting a game window for " + name + "..."));
			return 1;
		} catch (UnsupportedLauncherException | IllegalArgumentException | IllegalStateException e) {
			ctx.getSource().sendError(Component.literal(e.getMessage()));
		} catch (IOException e) {
			HearthsideMod.LOGGER.error("Could not start a satellite for {}", name, e);
			ctx.getSource().sendError(Component.literal("Couldn't start a game window for " + name + ": " + e.getMessage()));
		}
		return 0;
	}

	private static int leave(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		HostSession session = HostSession.current();
		if (session == null) {
			ctx.getSource().sendError(Component.literal("Open a singleplayer world first."));
			return 0;
		}
		try {
			session.leave(name);
			ctx.getSource().sendFeedback(Component.literal(name + " left."));
			return 1;
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendError(Component.literal(e.getMessage()));
			return 0;
		}
	}
}
