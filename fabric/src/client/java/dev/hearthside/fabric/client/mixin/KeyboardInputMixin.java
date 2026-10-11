package dev.hearthside.fabric.client.mixin;

import dev.hearthside.fabric.client.input.PadMovement;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Analog stick movement: when the keyboard is not moving the player, use the controller's move
 * vector, and set the direction flags so sprinting and other vanilla checks still work.
 */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin extends ClientInput {
	private static final float DIRECTION_THRESHOLD = 0.3f;

	@Inject(method = "tick", at = @At("RETURN"))
	private void hearthside$analogMovement(CallbackInfo ci) {
		if (this.moveVector.lengthSquared() > 1.0E-6F) {
			return;
		}
		PadMovement.current().ifPresent(move -> {
			this.moveVector = new Vec2(move.x, move.y);
			Input keys = this.keyPresses;
			this.keyPresses = new Input(move.y > DIRECTION_THRESHOLD, move.y < -DIRECTION_THRESHOLD,
					move.x > DIRECTION_THRESHOLD, move.x < -DIRECTION_THRESHOLD, keys.jump(), keys.shift(), keys.sprint());
		});
	}
}
