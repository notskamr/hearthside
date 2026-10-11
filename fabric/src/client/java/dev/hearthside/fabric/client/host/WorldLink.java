package dev.hearthside.fabric.client.host;

import dev.hearthside.core.auth.GuestTokenAuthority;
import dev.hearthside.core.channel.ControlConnection;
import dev.hearthside.core.profile.GuestProfile;
import dev.hearthside.core.profile.GuestUuids;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.core.session.GuestRoster;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.server.HostServerBridge;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.HttpUtil;

/**
 * One open world's side of split-screen: who may log in as a guest (roster and tokens) and the loopback listener they
 * join on. Created when the world's integrated server starts and attached to the party; closed with the world.
 */
final class WorldLink {
	private final MinecraftServer server;
	private final GuestRoster roster;
	private final GuestTokenAuthority tokens;
	/** Loopback port guests join on; 0 until the first guest needs it. */
	private volatile int gamePort;

	WorldLink(MinecraftServer server, GuestTokenAuthority tokens) {
		this.server = server;
		this.tokens = tokens;
		this.roster = new GuestRoster(server.getSingleplayerProfile().name());
		HostServerBridge.activate(new HostServerBridge.Active(roster, tokens, this::reopenGuestListener));
	}

	/** Lets this guest log in and tells their game to join, as {@code profile}. */
	void sendIn(int index, GuestProfile profile, ControlConnection connection) throws IOException {
		ensureGuestListener();
		Optional<GuestProfile> registered = roster.byIndex(index);
		if (registered.isPresent() && !registered.get().id().equals(profile.id())) {
			roster.remove(index);
			registered = Optional.empty();
		}
		if (registered.isEmpty()) {
			roster.register(index, profile);
		}
		String token = tokens.issue(GuestUuids.forProfile(profile.id()));
		connection.send(new ControlMessage.JoinWorld(gamePort, token, profile.name()));
	}

	/** Whether a player with this name is in the world (a guest's game has finished joining). */
	boolean hasPlayer(String name) {
		return server.getPlayerList().getPlayerByName(name) != null;
	}

	void remove(int index) {
		roster.remove(index);
	}

	void close() {
		HostServerBridge.deactivate();
	}

	private void ensureGuestListener() throws IOException {
		if (gamePort == 0) {
			int port = HttpUtil.getAvailablePort();
			server.getConnection().startTcpServerListener(InetAddress.getLoopbackAddress(), port);
			gamePort = port;
			HearthsideMod.LOGGER.info("Hearthside guests join on 127.0.0.1:{}", port);
		}
	}

	/** Vanilla's Stop Sharing closed every TCP listener, including the guests'; open it again. */
	private void reopenGuestListener() {
		int port = gamePort;
		if (port == 0) {
			return;
		}
		try {
			server.getConnection().startTcpServerListener(InetAddress.getLoopbackAddress(), port);
			HearthsideMod.LOGGER.info("Reopened the Hearthside guest listener on 127.0.0.1:{}", port);
		} catch (IOException e) {
			gamePort = 0;
			HearthsideMod.LOGGER.error("Could not reopen the Hearthside guest listener", e);
		}
	}
}
