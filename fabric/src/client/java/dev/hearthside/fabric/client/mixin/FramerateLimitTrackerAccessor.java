package dev.hearthside.fabric.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The frame-rate limit Minecraft paces to, before any throttling (a menu, an idle player): a session's cap is read back
 * from here, as the option's slider can't hold it (it only holds tens).
 */
@Mixin(FramerateLimitTracker.class)
public interface FramerateLimitTrackerAccessor {
	@Accessor("framerateLimit")
	int hearthside$framerateLimit();
}
