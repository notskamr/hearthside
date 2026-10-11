package dev.hearthside.fabric.client.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The size fields Minecraft renders and lays out GUIs from, for rendering at a viewport's size. */
@Mixin(Window.class)
public interface WindowAccessor {
	@Accessor("framebufferWidth")
	void hearthside$setFramebufferWidth(int width);

	@Accessor("framebufferHeight")
	void hearthside$setFramebufferHeight(int height);

	@Accessor("width")
	void hearthside$setScreenWidth(int width);

	@Accessor("height")
	void hearthside$setScreenHeight(int height);

	@Invoker("refreshFramebufferSize")
	void hearthside$refreshFramebufferSize();
}
