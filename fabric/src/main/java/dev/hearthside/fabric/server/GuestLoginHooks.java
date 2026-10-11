package dev.hearthside.fabric.server;

import dev.hearthside.core.session.GuestAdmission;
import dev.hearthside.fabric.HearthsideMod;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Holds every guest login at a token query; only a valid one-time token lets it finish. */
public final class GuestLoginHooks {
	public static final Identifier GUEST_TOKEN = Identifier.fromNamespaceAndPath(HearthsideMod.MOD_ID, "guest_token");

	private GuestLoginHooks() {}

	public static void register() {
		ServerLoginConnectionEvents.QUERY_START.register((listener, server, sender, synchronizer) -> {
			if (((GuestLoginState) listener).hearthside$guestUuid() != null) {
				sender.sendPacket(GUEST_TOKEN, new FriendlyByteBuf(Unpooled.buffer()));
			}
		});

		ServerLoginNetworking.registerGlobalReceiver(GUEST_TOKEN, (server, listener, understood, buf, synchronizer, sender) -> {
			UUID guest = ((GuestLoginState) listener).hearthside$guestUuid();
			var active = HostServerBridge.active();
			boolean valid = guest != null && understood && active.isPresent()
					&& GuestAdmission.tokenMatches(buf.readUtf(), guest, active.get().tokens());
			if (!valid) {
				listener.disconnect(Component.literal("Hearthside guest verification failed"));
				return;
			}
			HearthsideMod.LOGGER.info("Hearthside guest {} verified", listener.getUserName());
		});
	}
}
