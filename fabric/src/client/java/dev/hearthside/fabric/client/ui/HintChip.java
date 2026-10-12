package dev.hearthside.fabric.client.ui;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.PadStyle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;

/**
 * A button hint on an Ember-glass chip: the controller's glyph and what it does (Spec A section 4.3). Clicking it with
 * the mouse does the same thing, so screens can use hints as their only buttons, as the lobby draft does.
 */
public final class HintChip extends Button {
	private final Control control;
	private final Supplier<PadStyle> style;

	/**
	 * Never reached with arrow keys or Tab: a hint is pressed with its button (or clicked), and focus landing on one
	 * would sit in the way of the screen's own items.
	 */
	@Override
	public ComponentPath nextFocusPath(FocusNavigationEvent event) {
		return null;
	}

	public HintChip(int x, int y, Control control, Supplier<PadStyle> style, Component label, OnPress onPress) {
		super(x, y, width(control, label), HEIGHT, label, onPress, DEFAULT_NARRATION);
		this.control = control;
		this.style = style;
	}

	/** Hearthside's knock, not vanilla's click. */
	@Override
	public void playDownSound(SoundManager soundManager) {
		HearthSounds.play(HearthSounds.Ui.KNOCK);
	}

	public static final int HEIGHT = 15;
	/** Every glyph sprite a chip has drawn since this was last cleared. For the E2E. */
	public static final Set<String> drawn = ConcurrentHashMap.newKeySet();

	public static int width(Control control, Component label) {
		return 4 + HearthDraw.glyphWidth(control) + 4 + Minecraft.getInstance().font.width(label) + 5;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		HearthDraw.chip(g, getX(), getY(), getWidth(), getHeight());
		if (isActive() && isHoveredOrFocused()) {
			g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0x30FFB35A);
		}
		PadStyle family = style.get();
		drawn.add(HearthSprites.glyph(control, family).toString());
		int glyph = HearthDraw.glyph(g, control, family, getX() + 4, getY() + 2);
		HearthDraw.label(g, Minecraft.getInstance().font, getMessage(), getX() + 4 + glyph + 4, getY() + 4,
				HearthDraw.CREAM);
	}
}
