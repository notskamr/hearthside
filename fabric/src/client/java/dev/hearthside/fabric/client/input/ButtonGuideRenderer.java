package dev.hearthside.fabric.client.input;

import dev.hearthside.core.input.ButtonGuide;
import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.GuideContext;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.layout.HintPlacement;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.ui.Tween;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.host.LobbyScreen;
import dev.hearthside.fabric.client.menu.PadScreen;
import dev.hearthside.fabric.client.mixin.ContainerScreenAccessor;
import dev.hearthside.fabric.client.ui.HearthDraw;
import dev.hearthside.fabric.client.ui.HearthSprites;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.components.ItemDisplayWidget;
import net.minecraft.client.gui.components.LoadingDotsWidget;
import net.minecraft.client.gui.components.PlayerFaceWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.HitResult;

/**
 * Draws Bedrock-style button hints for a controller-driven client (Spec A section 4.3): bottom-right in the world; on
 * Minecraft's screens, bottom-centre, or the first corner that covers nothing. Each hint is a pixel-art glyph for the
 * player's controller family on an Ember-glass chip; a hint pulses briefly when its button is pressed.
 */
public final class ButtonGuideRenderer {
	private static final int MARGIN = 6, GAP = 4, PAD = 2, ROW = 15;
	/** How far a pressed chip swells on each side. */
	private static final int GROW = 1;
	private static final String SELECT = "hearthside.guide.select", BACK = "hearthside.guide.back";
	/** Creative's tabs stand this far above and below its panel. */
	private static final int CREATIVE_TAB_HEIGHT = 32;
	/** The recipe book's offset left of centre when it sits beside the panel, and its tab column's width. */
	private static final int RECIPE_BOOK_OFFSET = 86, RECIPE_TABS_WIDTH = 30;
	/** The hotbar: half its width, its height with the selection frame, and the off-hand slot's or indicator's width. */
	private static final int HOTBAR_HALF_WIDTH = 91, HOTBAR_HEIGHT = 24, HOTBAR_SIDE_SLOT = 29;
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
	private static volatile List<Hint> lastLayout = List.of();
	/** Mods' screens the hints stayed off, each logged once. */
	private static final Set<String> skippedScreens = ConcurrentHashMap.newKeySet();
	private static volatile boolean screenHintsHidden;

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
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> drawScreen(s, graphics, mouseX, mouseY)));
	}

	public static void disable() {
		schemeSource = null;
	}

	/** Player 1's mouse is driving their screens: no controller hints on them until the controller takes over. */
	public static void setScreenHintsHidden(boolean hidden) {
		screenHintsHidden = hidden;
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
		// The HUD is drawn under any screen first: a screen that draws no hints this frame leaves none recorded.
		lastLayout = List.of();
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

	/**
	 * Hints on one of Minecraft's own screens: only what works there (Back where Esc would close it, Select while the
	 * pointer is on something to click), in the first spot that covers nothing the screen shows; icons alone if the
	 * full row fits nowhere, and nothing if those don't fit either.
	 */
	private static void drawScreen(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		Supplier<ControlScheme> scheme = schemeSource;
		if (scheme == null || screenHintsHidden || screen instanceof LobbyScreen || screen instanceof PadScreen
				|| screen instanceof ChatScreen) {
			// Hearthside's screens draw their own hint chips (a second row would sit on top of them); chat is all
			// text box and messages, and the keyboard's.
			lastLayout = List.of();
			return;
		}
		if (!screen.getClass().getName().startsWith("net.minecraft.")) {
			// A mod's screen (Sodium's video settings, for one) may draw its own widgets, which the placement can't
			// see to keep clear of: no hints rather than hints over its buttons.
			if (skippedScreens.add(screen.getClass().getName())) {
				HearthsideMod.LOGGER.info("Hearthside hints skipped on a mod's screen: {}", screen.getClass().getName());
			}
			lastLayout = List.of();
			return;
		}
		boolean container = screen instanceof AbstractContainerScreen<?>;
		boolean holding = container && !((AbstractContainerScreen<?>) screen).getMenu().getCarried().isEmpty();
		var context = new GuideContext(container, !container, false, false, false, holding, false);
		List<ButtonGuide.Prompt> prompts = new ArrayList<>(ButtonGuide.prompts(context, scheme.get()));
		if (!container) {
			boolean select = overClickable(screen.children(), mouseX, mouseY);
			prompts.removeIf(p -> p.labelKey().equals(SELECT) && !select
					|| p.labelKey().equals(BACK) && !screen.shouldCloseOnEsc());
		}
		List<Rect> obstacles = obstacles(screen);
		Rect bounds = new Rect(0, 0, graphics.guiWidth(), graphics.guiHeight());
		boolean labels = true;
		List<Hint> layout = arrange(mc.font, prompts, true, bounds, obstacles);
		if (layout.isEmpty()) {
			labels = false;
			layout = arrange(mc.font, prompts, false, bounds, obstacles);
		}
		lastLayout = layout;
		if (layout.isEmpty()) {
			return;
		}
		drewOn = screen;
		for (int i = 0; i < prompts.size(); i++) {
			Rect chip = layout.get(i).rect();
			drawPrompt(graphics, mc.font, prompts.get(i), chip.x() + GROW + PAD, chip.y() + GROW + 2, labels);
		}
	}

	/** One drawn hint chip, in GUI pixels (with the pixel it swells by when pressed), and its label. */
	public record Hint(Rect rect, String label) {}

	/** The hints drawn on the last screen frame, for the E2E; empty when none were drawn. */
	public static List<Hint> lastLayout() {
		return lastLayout;
	}

	/** The row's chips placed clear of {@code obstacles}, or empty when the row fits nowhere. */
	private static List<Hint> arrange(Font font, List<ButtonGuide.Prompt> prompts, boolean labels, Rect bounds,
			List<Rect> obstacles) {
		if (prompts.isEmpty()) {
			return List.of();
		}
		// Chips GAP * 2 apart, with room around the row for a pressed chip to swell into.
		int width = GROW * 2 - GAP * 2;
		for (ButtonGuide.Prompt prompt : prompts) {
			width += chipWidth(font, prompt, labels) + GAP * 2;
		}
		int height = HearthDraw.GLYPH_HEIGHT + 4 + GROW * 2;
		Optional<Rect> row = HintPlacement.place(bounds, obstacles, width, height, MARGIN - GROW);
		if (row.isEmpty()) {
			return List.of();
		}
		List<Hint> hints = new ArrayList<>();
		int x = row.get().x() + GROW;
		for (ButtonGuide.Prompt prompt : prompts) {
			int chip = chipWidth(font, prompt, labels);
			hints.add(new Hint(new Rect(x - GROW, row.get().y(), chip + GROW * 2, height),
					Component.translatable(prompt.labelKey()).getString()));
			x += chip + GAP * 2;
		}
		return hints;
	}

	/**
	 * What a hint row must not cover: every widget the screen shows (inside lists and their entries too, clipped to
	 * what the list shows), a container's panel with its tabs, an open recipe book and the hotbar. Each grown by a pixel
	 * so the row never quite touches them.
	 */
	private static List<Rect> obstacles(Screen screen) {
		List<Rect> obstacles = new ArrayList<>();
		collect(screen.children(), new Rect(0, 0, screen.width, screen.height), obstacles);
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player != null && !player.isSpectator() && !mc.gui.hud.isHidden()) {
			// The hotbar shows under in-world screens (the death screen, the inventory): hints sit beside it, as they do
			// in play. With it, the off-hand slot (on the off hand's side) and the hotbar attack indicator (the other).
			int left = screen.width / 2 - HOTBAR_HALF_WIDTH, right = screen.width / 2 + HOTBAR_HALF_WIDTH;
			boolean offhandLeft = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
			if (!player.getOffhandItem().isEmpty()) {
				left -= offhandLeft ? HOTBAR_SIDE_SLOT : 0;
				right += offhandLeft ? 0 : HOTBAR_SIDE_SLOT;
			}
			if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
				left -= offhandLeft ? 0 : HOTBAR_SIDE_SLOT;
				right += offhandLeft ? HOTBAR_SIDE_SLOT : 0;
			}
			obstacles.add(new Rect(left, screen.height - HOTBAR_HEIGHT, right - left, HOTBAR_HEIGHT));
		}
		if (screen instanceof AbstractContainerScreen<?> container) {
			ContainerScreenAccessor panel = (ContainerScreenAccessor) container;
			// Creative's tabs stand a tab's height above and below its panel.
			int tabs = screen instanceof CreativeModeInventoryScreen ? CREATIVE_TAB_HEIGHT : 0;
			obstacles.add(new Rect(panel.hearthside$leftPos(), panel.hearthside$topPos() - tabs,
					panel.hearthside$imageWidth(), panel.hearthside$imageHeight() + tabs * 2));
			for (GuiEventListener child : screen.children()) {
				if (child instanceof RecipeBookComponent<?> book && book.isVisible()) {
					// Beside the panel when there's room (the panel moves right), else over it; its tabs on its left.
					boolean beside = panel.hearthside$leftPos() != (screen.width - panel.hearthside$imageWidth()) / 2;
					int x = (screen.width - RecipeBookComponent.IMAGE_WIDTH) / 2 - (beside ? RECIPE_BOOK_OFFSET : 0);
					obstacles.add(new Rect(x - RECIPE_TABS_WIDTH, (screen.height - RecipeBookComponent.IMAGE_HEIGHT) / 2,
							RecipeBookComponent.IMAGE_WIDTH + RECIPE_TABS_WIDTH, RecipeBookComponent.IMAGE_HEIGHT));
				}
			}
		}
		return obstacles.stream().map(r -> new Rect(r.x() - 1, r.y() - 1, r.width() + 2, r.height() + 2)).toList();
	}

	private static void collect(List<? extends GuiEventListener> children, Rect clip, List<Rect> into) {
		for (GuiEventListener child : children) {
			if (child instanceof AbstractWidget widget && !widget.visible) {
				continue;
			}
			Rect inner = clip;
			if (child instanceof LayoutElement element) {
				Rect rect = clip(element.getRectangle(), clip);
				if (rect == null) {
					continue; // scrolled out of its list
				}
				into.add(rect);
				inner = rect;
			}
			if (child instanceof ContainerEventHandler container) {
				collect(container.children(), inner, into);
			}
		}
	}

	private static Rect clip(ScreenRectangle r, Rect to) {
		int x0 = Math.max(r.left(), to.x()), y0 = Math.max(r.top(), to.y());
		int x1 = Math.min(r.right(), to.x() + to.width()), y1 = Math.min(r.bottom(), to.y() + to.height());
		return x1 > x0 && y1 > y0 ? new Rect(x0, y0, x1 - x0, y1 - y0) : null;
	}

	/** Whether A would click something here: an active button, slider, field or list entry under the pointer. */
	private static boolean overClickable(List<? extends GuiEventListener> children, int mouseX, int mouseY) {
		for (GuiEventListener child : children) {
			if (!child.isMouseOver(mouseX, mouseY)) {
				continue;
			}
			if (child instanceof ContainerEventHandler container) {
				// A list, or an entry holding buttons: what's under the pointer in it.
				if (overClickable(container.children(), mouseX, mouseY)) {
					return true;
				}
			} else if (child instanceof AbstractWidget widget) {
				if (!(widget instanceof AbstractStringWidget || widget instanceof ImageWidget
						|| widget instanceof ItemDisplayWidget || widget instanceof LoadingDotsWidget
						|| widget instanceof PlayerFaceWidget)) {
					return true;
				}
			} else if (child instanceof LayoutElement) {
				// A plain list entry (a world, a server, a language) is picked by clicking it.
				return true;
			}
		}
		return false;
	}

	/** A hint chip's width: its glyph, and its label unless {@code labels} is false. */
	private static int chipWidth(Font font, ButtonGuide.Prompt prompt, boolean labels) {
		return labels ? promptWidth(font, prompt) : HearthDraw.glyphWidth(prompt.control()) + PAD * 2;
	}

	private static int promptWidth(Font font, ButtonGuide.Prompt prompt) {
		Component label = Component.translatable(prompt.labelKey());
		return HearthDraw.glyphWidth(prompt.control()) + GAP + font.width(label) + PAD * 2;
	}

	private static void drawPrompt(GuiGraphicsExtractor graphics, Font font, ButtonGuide.Prompt prompt, int x, int y) {
		drawPrompt(graphics, font, prompt, x, y, true);
	}

	private static void drawPrompt(GuiGraphicsExtractor graphics, Font font, ButtonGuide.Prompt prompt, int x, int y,
			boolean withLabel) {
		Control control = prompt.control();
		Component label = Component.translatable(prompt.labelKey());
		int width = withLabel ? promptWidth(font, prompt) : chipWidth(font, prompt, false);
		Tween pulse = pulses.get(control);
		double p = pulse == null ? 1 : pulse.progress(System.nanoTime(), reduceMotion);
		// A pressed hint swells by a pixel and brightens, settling back over the pulse.
		int grow = p < 1 ? GROW : 0;
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
		if (withLabel) {
			graphics.text(font, label, x + HearthDraw.glyphWidth(control) + GAP, y + 2, TEXT, true);
		}
	}

	private static boolean usable(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof BlockItem || stack.getUseAnimation() != ItemUseAnimation.NONE);
	}
}
