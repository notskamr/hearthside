package dev.hearthside.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.hearthside.fabric.client.ui.HearthDraw;
import dev.hearthside.fabric.client.ui.HearthTextField;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link HearthTextField}s sit on parchment; every other text field keeps vanilla's look. */
@Mixin(EditBox.class)
public abstract class TextFieldStyleMixin {
	@WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void hearthside$parchment(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y,
			int width, int height, Operation<Void> original) {
		if ((Object) this instanceof HearthTextField) {
			HearthDraw.parchment(g, x, y, width, height);
			HearthTextField.drewParchment = true;
		} else {
			original.call(g, pipeline, sprite, x, y, width, height);
		}
	}
}
