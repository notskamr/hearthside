package dev.hearthside.fabric.client.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Notes, as Minecraft starts, whether a hook that another mod may take first applied. The freeze hook
 * ({@code WorldFreezeMixin}, on the {@code Gui.isPausing()} call in {@code Minecraft.runTick}) is optional: if another
 * mod redirects that call the hook has nothing to attach to, Minecraft starts regardless, and pausing together falls
 * back to vanilla's pause (player 1's pause freezes everyone). Applies every mixin as listed.
 */
public final class HearthsideMixinPlugin implements IMixinConfigPlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger("hearthside");
	private static final String FREEZE_MIXIN = "WorldFreezeMixin";
	private static final String FREEZE_HANDLER = "freezeOnlyWhenEveryoneIsPaused";
	/** Assumed applied until Minecraft's class shows otherwise. */
	private static volatile boolean freezeHookApplied = true;

	/** Whether player 1's pause can leave the world running while others play (the freeze hook applied). */
	public static boolean freezeHookApplied() {
		return freezeHookApplied;
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		if (!mixinClassName.endsWith("." + FREEZE_MIXIN)) {
			return;
		}
		try {
			if (!callsHandler(targetClass, "runTick")) {
				freezeHookApplied = false;
				LOGGER.warn("Hearthside's pausing together is off: another mod changes how Minecraft decides to pause, so "
						+ "player 1's pause freezes everyone, as in vanilla");
			}
		} catch (RuntimeException e) {
			// Can't tell: the hook is assumed applied, as it is when nothing else touches that call.
			LOGGER.warn("Hearthside couldn't check its pause hook", e);
		}
	}

	/** Whether {@code method} calls the freeze hook's handler (Mixin renames it, keeping its name in the new one). */
	private static boolean callsHandler(ClassNode target, String method) {
		for (MethodNode node : target.methods) {
			if (!node.name.equals(method)) {
				continue;
			}
			for (AbstractInsnNode insn : node.instructions) {
				if (insn instanceof MethodInsnNode call && call.name.contains(FREEZE_HANDLER)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void onLoad(String mixinPackage) {}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
