package dev.hearthside.fabric.client.input;

import dev.hearthside.core.input.ButtonGuide;
import dev.hearthside.core.input.ControlLabels;
import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.GuideContext;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.fabric.HearthsideMod;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.HitResult;

/**
 * Draws Bedrock-style button hints for a controller-driven client: bottom-right in the world,
 * bottom-centre on screens. Glyphs are plain text chips drawn here; no textures are borrowed.
 */
public final class ButtonGuideRenderer {
	private static final int MARGIN = 6, GAP = 4, PAD = 3, ROW = 13;
	private static final int CHIP = 0xC0202020, CHIP_EDGE = 0xFFE0E0E0, TEXT = 0xFFFFFFFF;

	private static volatile Supplier<ControlScheme> schemeSource;
	private static volatile PadStyle style = PadStyle.XBOX;
	private static boolean registered;

	private ButtonGuideRenderer() {}

	/** Starts drawing hints using the given scheme (re-read every frame, so rebinding shows at once). */
	public static void enable(Supplier<ControlScheme> scheme) {
		schemeSource = scheme;
		if (registered) {
			return;
		}
		registered = true;
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
				Identifier.fromNamespaceAndPath(HearthsideMod.MOD_ID, "button_guide"),
				(graphics, delta) -> drawWorld(graphics));
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) ->
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> drawScreen(s, graphics)));
	}

	public static void disable() {
		schemeSource = null;
	}

	public static void setStyle(PadStyle padStyle) {
		style = padStyle;
	}

	private static void drawWorld(GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		Supplier<ControlScheme> scheme = schemeSource;
		if (scheme == null || player == null || mc.gui.screen() != null) {
			return;
		}
		HitResult hit = mc.hitResult;
		boolean block = hit != null && hit.getType() == HitResult.Type.BLOCK;
		boolean entity = hit != null && hit.getType() == HitResult.Type.ENTITY;
		var context = new GuideContext(false, false, block, entity, usable(player.getMainHandItem()), false,
				player.getAbilities().flying);
		List<ButtonGuide.Prompt> prompts = ButtonGuide.prompts(context, scheme.get());
		int y = graphics.guiHeight() - MARGIN - ROW * prompts.size();
		for (ButtonGuide.Prompt prompt : prompts) {
			int width = promptWidth(mc.font, prompt);
			drawPrompt(graphics, mc.font, prompt, graphics.guiWidth() - MARGIN - width, y);
			y += ROW;
		}
	}

	private static void drawScreen(Screen screen, GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		Supplier<ControlScheme> scheme = schemeSource;
		if (scheme == null) {
			return;
		}
		boolean container = screen instanceof AbstractContainerScreen<?>;
		boolean holding = container && !((AbstractContainerScreen<?>) screen).getMenu().getCarried().isEmpty();
		var context = new GuideContext(container, !container, false, false, false, holding, false);
		List<ButtonGuide.Prompt> prompts = ButtonGuide.prompts(context, scheme.get());
		int total = prompts.stream().mapToInt(p -> promptWidth(mc.font, p) + GAP * 2).sum();
		int x = (graphics.guiWidth() - total) / 2;
		int y = graphics.guiHeight() - MARGIN - ROW;
		for (ButtonGuide.Prompt prompt : prompts) {
			drawPrompt(graphics, mc.font, prompt, x, y);
			x += promptWidth(mc.font, prompt) + GAP * 2;
		}
	}

	private static int promptWidth(Font font, ButtonGuide.Prompt prompt) {
		String glyph = ControlLabels.glyph(prompt.control(), style);
		return font.width(glyph) + PAD * 2 + GAP + font.width(Component.translatable(prompt.labelKey()));
	}

	private static void drawPrompt(GuiGraphicsExtractor graphics, Font font, ButtonGuide.Prompt prompt, int x, int y) {
		String glyph = ControlLabels.glyph(prompt.control(), style);
		int chipWidth = font.width(glyph) + PAD * 2;
		graphics.fill(x - 1, y - 1, x + chipWidth + 1, y + 10, CHIP_EDGE);
		graphics.fill(x, y, x + chipWidth, y + 9, CHIP);
		graphics.text(font, glyph, x + PAD, y + 1, TEXT, false);
		graphics.text(font, Component.translatable(prompt.labelKey()), x + chipWidth + GAP, y + 1, TEXT, true);
	}

	private static boolean usable(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof BlockItem || stack.getUseAnimation() != ItemUseAnimation.NONE);
	}
}
