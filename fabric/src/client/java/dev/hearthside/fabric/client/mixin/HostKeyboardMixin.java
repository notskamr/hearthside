package dev.hearthside.fabric.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.host.Party;
import dev.hearthside.fabric.client.input.KeyboardFocus;
import java.util.Optional;
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
 * keyboard is free: its keys type into that keyboard instead and never reach player 1's key mappings, except the
 * function keys (ruling P-12). On keyboard and mouse, or with a screen open, keys are player 1's as ever. Releases always
 * go through, so a key held down before the keyboard took over is let go; each one also ends the repeats of a key an
 * on-screen keyboard took (on any screen). If anything here fails, the key goes to Minecraft as if Hearthside weren't
 * here.
 */
@Mixin(KeyboardHandler.class)
abstract class HostKeyboardMixin {
	private static boolean hearthside$failed;

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void hearthside$keyToNewcomer(long handle, int action, KeyEvent event, CallbackInfo ci) {
		if (handle != Minecraft.getInstance().getWindow().handle()) {
			return;
		}
		try {
			if (action == InputConstants.RELEASE) {
				KeyboardFocus.keyReleased(event.key());
			} else if (KeyboardFocus.keyPressed(hearthside$newcomerKeyboard(), event.key())) {
				ci.cancel();
			}
		} catch (RuntimeException e) {
			hearthside$failed(e);
		}
	}

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
	private void hearthside$charToNewcomer(long handle, CharacterEvent event, CallbackInfo ci) {
		if (handle != Minecraft.getInstance().getWindow().handle()) {
			return;
		}
		try {
			Optional<KeyboardFocus.Target> keyboard = hearthside$newcomerKeyboard();
			if (keyboard.isPresent()) {
				KeyboardFocus.charTyped(keyboard.get(), event.codepoint());
				ci.cancel();
			}
		} catch (RuntimeException e) {
			hearthside$failed(e);
		}
	}

	private static Optional<KeyboardFocus.Target> hearthside$newcomerKeyboard() {
		Party party = Party.current();
		return party == null ? Optional.empty() : party.newcomerKeyboard();
	}

	/** The key goes on to Minecraft; logged the first time only, as it would fail again on every key. */
	private static void hearthside$failed(RuntimeException e) {
		if (!hearthside$failed) {
			hearthside$failed = true;
			HearthsideMod.LOGGER.error("Hearthside couldn't pass player 1's key to an on-screen keyboard; Minecraft gets it",
					e);
		}
	}
}
