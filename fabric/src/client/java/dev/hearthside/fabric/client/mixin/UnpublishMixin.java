package dev.hearthside.fabric.client.mixin;

import dev.hearthside.fabric.server.HostServerBridge;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's Stop Sharing closes every TCP listener and disconnects everyone but the owner. Local
 * guests are part of the owner's couch, not the LAN game: keep them, and let the session reopen its
 * loopback listener afterwards.
 */
@Mixin(IntegratedServer.class)
abstract class UnpublishMixin {
	@Redirect(method = "unpublishServer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;disconnect(Lnet/minecraft/network/chat/Component;)V"))
	private void hearthside$keepGuests(ServerGamePacketListenerImpl connection, Component reason) {
		var active = HostServerBridge.active();
		if (active.isPresent() && active.get().roster().isGuest(connection.player.getUUID())) {
			return;
		}
		connection.disconnect(reason);
	}

	@Inject(method = "unpublishServer", at = @At("RETURN"))
	private void hearthside$reopenGuestListener(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			HostServerBridge.active().ifPresent(a -> a.tcpListenersClosed().run());
		}
	}
}
