package dev.hearthside.fabric.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hearthside.fabric.client.host.Party;
import dev.hearthside.fabric.client.host.SeatCard;
import dev.hearthside.fabric.client.input.KeyboardFocus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A drop-in newcomer's name keyboard is drawn over their view, not on a screen, so player 1's PC keyboard would only
 * play player 1. While player 1 plays on a controller with no screen open (ruling R8, Spec E section 3.6), their PC
 * keyboard is free: its keys type into that keyboard instead and never reach player 1's key mappings. On keyboard and
 * mouse, or with a screen open, keys are player 1's as ever. Releases always go through, so a key held down before the
 * keyboard took over is let go.
 */
@Mixin(KeyboardHandler.class)
abstract class HostKeyboardMixin {
	private static final int SEATS = 4;

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void hearthside$keyToNewcomer(long handle, int action, KeyEvent event, CallbackInfo ci) {
		if (action == InputConstants.RELEASE || handle != Minecraft.getInstance().getWindow().handle()) {
			return;
		}
		KeyboardFocus.Target keyboard = hearthside$newcomerKeyboard();
		if (keyboard != null) {
			KeyboardFocus.keyPressed(keyboard, event.key());
			ci.cancel();
		}
	}

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
	private void hearthside$charToNewcomer(long handle, CharacterEvent event, CallbackInfo ci) {
		if (handle != Minecraft.getInstance().getWindow().handle()) {
			return;
		}
		KeyboardFocus.Target keyboard = hearthside$newcomerKeyboard();
		if (keyboard != null) {
			KeyboardFocus.charTyped(keyboard, event.codepoint());
			ci.cancel();
		}
	}

	/** The open in-world name keyboard player 1's PC keyboard types into now (the one used last), or null. */
	private static KeyboardFocus.Target hearthside$newcomerKeyboard() {
		Party party = Party.current();
		if (party == null || !party.inWorld() || !party.hostHasController()
				|| Minecraft.getInstance().gui.screen() != null) {
			return null;
		}
		List<SeatCard> open = new ArrayList<>();
		for (int seat = 1; seat < SEATS; seat++) {
			SeatCard card = party.seatCard(seat);
			if (card != null && card.keyboardOpen()) {
				open.add(card);
			}
		}
		return KeyboardFocus.among(open).orElse(null);
	}
}
