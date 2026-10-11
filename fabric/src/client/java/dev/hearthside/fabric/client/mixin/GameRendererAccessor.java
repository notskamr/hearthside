package dev.hearthside.fabric.client.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The GUI renderer, for Hearthside's overlay pass. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Accessor("guiRenderer")
	GuiRenderer hearthside$guiRenderer();
}
