package dev.hearthside.fabric.client.input;

import dev.hearthside.core.input.ButtonGuide;
import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.GuideContext;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.ui.Tween;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.host.LobbyScreen;
import dev.hearthside.fabric.client.menu.PadScreen;
import dev.hearthside.fabric.client.ui.HearthDraw;
import dev.hearthside.fabric.client.ui.HearthSprites;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * Draws Bedrock-style button hints for a controller-driven client (Spec A section 4.3): bottom-right in the world,
 * bottom-centre on screens. Each hint is a pixel-art glyph for the player's controller family on an Ember-glass chip;
 * a hint pulses briefly when its button is pressed.
 */
public final class ButtonGuideRenderer {
	private static final int MARGIN = 6, GAP = 4, PAD = 2, ROW = 15;
	private static final int TEXT = 0xFFFFFFFF;
	private static final Duration PULSE = Duration.ofMillis(150);

	private static volatile Supplier<ControlScheme> schemeSource;
	private static volatile PadStyle style = PadStyle.XBOX;
	private static volatile boolean reduceMotion;
	private static boolean registered;
	/** Render thread: the last frame's input, and a pulse per control pressed since. */
	private static InputFrame lastFrame = InputFrame.NEUTRAL;
	private static final Map<Control, Tween> pulses = new EnumMap<>(Control.class);
	/** Glyph sprites drawn so far, each logged once (the E2E reads the log). */
	private static final Set<Identifier> logged = ConcurrentHashMap.newKeySet();
	/** The screen the menu hints last drew on (the E2E checks screens with their own chips never get them). */
	public static volatile Screen drewOn;

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

	public static void setReduceMotion(boolean reduce) {
		reduceMotion = reduce;
	}

	/** The controller's input this frame: a newly pressed control's hint pulses. Render thread. */
	public static void onFrame(InputFrame frame) {
		long now = System.nanoTime();
		for (Control control : Control.values()) {
			if (control.isDown(frame) && !control.isDown(lastFrame)) {
				pulses.computeIfAbsent(control, c -> new Tween(PULSE)).start(now);
			}
		}
		lastFrame = frame;
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
		int y = graphics.guiHeight() - MARGIN - ROW * prompts.size() + 2;
		for (ButtonGuide.Prompt prompt : prompts) {
			int width = promptWidth(mc.font, prompt);
			drawPrompt(graphics, mc.font, prompt, graphics.guiWidth() - MARGIN - width, y);
			y += ROW;
		}
	}

	private static void drawScreen(Screen screen, GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		Supplier<ControlScheme> scheme = schemeSource;
		if (scheme == null || screen instanceof LobbyScreen || screen instanceof PadScreen) {
			// Those screens draw their own hint chips; a second row would sit on top of them.
			return;
		}
		boolean container = screen instanceof AbstractContainerScreen<?>;
		boolean holding = container && !((AbstractContainerScreen<?>) screen).getMenu().getCarried().isEmpty();
		var context = new GuideContext(container, !container, false, false, false, holding, false);
		List<ButtonGuide.Prompt> prompts = ButtonGuide.prompts(context, scheme.get());
		drewOn = screen;
		int total =prompts.stream().mapToInt(p -> promptWidth(mc.font, p) + GAP * 2).sum();
		int x = (graphics.guiWidth() - total) / 2;
		int y = graphics.guiHeight() - MARGIN - ROW + 2;
		for (ButtonGuide.Prompt prompt : prompts) {
			drawPrompt(graphics, mc.font, prompt, x, y);
			x += promptWidth(mc.font, prompt) + GAP * 2;
		}
	}

	private static int promptWidth(Font font, ButtonGuide.Prompt prompt) {
		Component label = Component.translatable(prompt.labelKey());
		return HearthDraw.glyphWidth(prompt.control()) + GAP + font.width(label) + PAD * 2;
	}

	private static void drawPrompt(GuiGraphicsExtractor graphics, Font font, ButtonGuide.Prompt prompt, int x, int y) {
		Control control = prompt.control();
		Component label = Component.translatable(prompt.labelKey());
		int width = promptWidth(font, prompt);
		Tween pulse = pulses.get(control);
		double p = pulse == null ? 1 : pulse.progress(System.nanoTime(), reduceMotion);
		// A pressed hint swells by a pixel and brightens, settling back over the pulse.
		int grow = p < 1 ? 1 : 0;
		HearthDraw.chip(graphics, x - PAD - grow, y - 2 - grow, width + grow * 2, HearthDraw.GLYPH_HEIGHT + 4 + grow * 2);
		if (p < 1) {
			int alpha = (int) ((1 - p) * 0x70);
			graphics.fill(x - PAD, y - 2, x - PAD + width, y + HearthDraw.GLYPH_HEIGHT + 2, alpha << 24 | 0xFFB35A);
		}
		HearthDraw.glyph(graphics, control, style, x, y);
		Identifier sprite = HearthSprites.glyph(control, style);
		if (logged.add(sprite)) {
			HearthsideMod.LOGGER.info("Hearthside hint glyph {}", sprite);
		}
		graphics.text(font, label, x + HearthDraw.glyphWidth(control) + GAP, y + 2, TEXT, true);
	}

	private static boolean usable(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof BlockItem || stack.getUseAnimation() != ItemUseAnimation.NONE);
	}
}
