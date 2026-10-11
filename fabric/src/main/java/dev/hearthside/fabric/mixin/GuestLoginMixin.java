package dev.hearthside.fabric.mixin;

import com.mojang.authlib.GameProfile;
import dev.hearthside.core.session.GuestAdmission;
import dev.hearthside.fabric.server.GuestLoginState;
import dev.hearthside.fabric.server.HostServerBridge;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
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
 * Local guests have no Mojang account, so a loopback login using a name the host's roster expects
 * skips encryption and Mojang authentication. {@link GuestLoginHooks} then holds the login at a
 * one-time token query and disconnects it unless the token proves it is that guest.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
abstract class GuestLoginMixin implements GuestLoginState {
	@Shadow @Final private Connection connection;
	@Shadow private @Nullable String requestedUsername;

	@Shadow
	protected abstract void startClientVerification(GameProfile profile);

	@Unique private @Nullable UUID hearthside$guestUuid;

	@Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
	private void hearthside$admitLocalGuest(ServerboundHelloPacket packet, CallbackInfo ci) {
		var active = HostServerBridge.active();
		if (active.isEmpty() || !StringUtil.isValidPlayerName(packet.name())) {
			return;
		}
		var roster = active.get().roster();
		if (GuestAdmission.pathFor(packet.name(), isLoopback(connection.getRemoteAddress()), roster) != GuestAdmission.Path.GUEST) {
			return;
		}
		UUID guestUuid = roster.expectedUuidForName(packet.name()).orElseThrow();
		this.requestedUsername = packet.name();
		this.hearthside$guestUuid = guestUuid;
		startClientVerification(new GameProfile(guestUuid, packet.name()));
		ci.cancel();
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
