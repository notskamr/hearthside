package dev.hearthside.fabric.mixin;

import com.mojang.authlib.GameProfile;
import dev.hearthside.core.session.GuestAdmission;
import dev.hearthside.fabric.server.GuestLoginState;
import dev.hearthside.fabric.server.HostServerBridge;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.util.StringUtil;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a Hearthside session is open, every login's first Hello is decided by
 * {@link GuestAdmission#decide}: local guests skip Mojang authentication (and are then held at a
 * one-time token query by {@code GuestLoginHooks}), the host's name and unexpected connections to a
 * world not open to LAN are refused, and everything else is left to vanilla.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
abstract class GuestLoginMixin implements GuestLoginState {
	@Shadow @Final private Connection connection;
	@Shadow @Final private MinecraftServer server;
	@Shadow private @Nullable String requestedUsername;

	@Shadow
	protected abstract void startClientVerification(GameProfile profile);

	@Shadow
	public abstract void disconnect(Component reason);

	@Unique private @Nullable UUID hearthside$guestUuid;
	@Unique private boolean hearthside$helloSeen;

	@Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
	private void hearthside$decideLogin(ServerboundHelloPacket packet, CallbackInfo ci) {
		boolean firstHello = !hearthside$helloSeen;
		hearthside$helloSeen = true;
		var active = HostServerBridge.active();
		if (active.isEmpty() || !StringUtil.isValidPlayerName(packet.name())) {
			return;
		}
		var roster = active.get().roster();
		var attempt = new GuestAdmission.LoginAttempt(packet.name(), isLoopback(connection.getRemoteAddress()),
				connection.isMemoryConnection(), firstHello, server.isPublished());
		switch (GuestAdmission.decide(attempt, roster)) {
			case VANILLA -> { }
			case REFUSE -> {
				disconnect(Component.literal("You can't join this world."));
				ci.cancel();
			}
			case GUEST -> {
				UUID guestUuid = roster.expectedUuidForName(packet.name()).orElseThrow();
				this.requestedUsername = packet.name();
				this.hearthside$guestUuid = guestUuid;
				startClientVerification(new GameProfile(guestUuid, packet.name()));
				ci.cancel();
			}
		}
	}

	@Unique
	private static boolean isLoopback(SocketAddress address) {
		return address instanceof InetSocketAddress inet && inet.getAddress() != null && inet.getAddress().isLoopbackAddress();
	}

	@Override
	public UUID hearthside$guestUuid() {
		return hearthside$guestUuid;
	}

	@Override
	public void hearthside$setGuestUuid(UUID id) {
		this.hearthside$guestUuid = id;
	}
}
