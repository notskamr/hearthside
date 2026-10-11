package dev.hearthside.fabric.client.mixin.backend;

import com.mojang.renderpearl.backend.opengl.FrameBufferCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The OpenGL device's framebuffer cache, which every {@code GlTexture} needs; the class isn't public. */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlDevice")
public interface GlDeviceAccessor {
	@Accessor("frameBufferCache")
	FrameBufferCache hearthside$frameBufferCache();
}
