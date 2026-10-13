package dev.hearthside.fabric.client.ui;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.layout.Ease;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.compositor.OverlayContext;
import dev.hearthside.fabric.client.compositor.OverlayLayer;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * The drop-in prompt (Spec A section 3.2): "New controller — Press A to join", in Ember glass at the top of the
 * window, naming the button the way that controller labels it, with a bar that runs out with the confirm window. It
 * slides down in and back up out (appearing and going at once with reduce motion). Over the composited views it is an
 * overlay layer; when player 1 plays alone it draws on their HUD. Render thread.
 *
 * <p>Player 1's Add player (Spec E section 3.4) shows the same toast: "Add player — Press A on a new controller to
 * join", or "Connect a controller, then press A" while no free controller is connected, switching as one is plugged in.
 */
public final class JoinToast implements OverlayLayer {
	public static final Duration IN = Duration.ofMillis(180), OUT = Duration.ofMillis(150);
	private static final int HEIGHT = 38, ORANGE = 0xFFFF9A4A, CREAM = 0xFFF3DFB6;
	/** The Add player prompt's controller: it isn't any one controller's. */
	private static final String ADD_PLAYER = "";

	private record Prompt(String controller, PadStyle style, long since, long until) {
		boolean addPlayer() {
			return controller.equals(ADD_PLAYER);
		}
	}

	private final Set<PadStyle> logged = EnumSet.noneOf(PadStyle.class);
	private Prompt shown;
	private Prompt leaving;
	private long leavingSince;
	private boolean reduceMotion;
	/** For the Add player prompt: the free controllers' family, or null while none is connected. */
	private Supplier<PadStyle> freeStyle = () -> null;

	/** Over every view and everything on them (cards, name tags, borders, the party panel). */
	@Override
	public int order() {
		return 100;
	}

	/** A new controller pressed A once: show the prompt for {@code window}, in that controller's family. */
	public void show(String controller, PadStyle style, long now, Duration window, boolean reduce) {
		expire(now);
		if (shown == null || !shown.controller().equals(controller)) {
			HearthSounds.play(HearthSounds.Ui.PROMPT_IN);
		}
		shown = new Prompt(controller, style, now, now + window.toNanos());
		leaving = null;
		reduceMotion = reduce;
		if (logged.add(style)) {
			HearthsideMod.LOGGER.info("Hearthside toast glyph {}", style.name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	/**
	 * Player 1 chose Add player: show its prompt for {@code window}, in place of any controller's. {@code freeStyle}
	 * gives the free controllers' family, or null while none is connected; the prompt follows it as it draws.
	 */
	public void showAddPlayer(long now, Duration window, boolean reduce, Supplier<PadStyle> freeStyle) {
		expire(now);
		if (shown == null || !shown.addPlayer()) {
			HearthSounds.play(HearthSounds.Ui.PROMPT_IN);
		}
		this.freeStyle = freeStyle;
		shown = new Prompt(ADD_PLAYER, PadStyle.GENERIC, now, now + window.toNanos());
		leaving = null;
		reduceMotion = reduce;
	}

	/** The Add player prompt closed: someone joined, it expired or player 1 cancelled it. */
	public void hideAddPlayer(long now) {
		hide(ADD_PLAYER, now);
	}

	/** That controller confirmed, cancelled or let the prompt expire. */
	public void hide(String controller, long now) {
		if (shown != null && shown.controller().equals(controller)) {
			HearthSounds.play(HearthSounds.Ui.PROMPT_OUT);
			leaving = shown;
			leavingSince = now;
			shown = null;
		}
	}

	public boolean visible() {
		expire(System.nanoTime());
		return shown != null;
	}

	/** A prompt whose window ran out leaves by itself. */
	private void expire(long now) {
		if (shown != null && now >= shown.until()) {
			HearthSounds.play(HearthSounds.Ui.PROMPT_OUT);
			leaving = shown;
			leavingSince = shown.until();
			shown = null;
		}
	}

	/** When the prompt showing now appeared, or -1. For the E2E. */
	public long shownSince() {
		return shown == null ? -1 : shown.since();
	}

	/** How far above its place the toast is at {@code now}: -height when out of sight, 0 in place. */
	public float offset(long now) {
		expire(now);
		if (shown != null) {
			if (reduceMotion) {
				return 0;
			}
			double t = (now - shown.since()) / (double) IN.toNanos();
			return (float) (-HEIGHT * (1 - Ease.outCubic(t)));
		}
		if (leaving != null && !reduceMotion) {
			double t = (now - leavingSince) / (double) OUT.toNanos();
			return t >= 1 ? -HEIGHT - 8 : (float) (-HEIGHT * Ease.outCubic(t));
		}
		return -HEIGHT - 8;
	}

	@Override
	public void extract(GuiGraphicsExtractor g, OverlayContext context) {
		// Over split play: centred on the window, below the first row of name tags and Minecraft's own toasts.
		draw(g, context.guiWidth(), context.nanos(), 26);
	}

	/** On player 1's own screen, alone: centred at the top, clear of Minecraft's toasts (the right 160 GUI px). */
	public void draw(GuiGraphicsExtractor g, int width, long now) {
		draw(g, width, now, -1);
	}

	/** Draws the toast at the top; {@code top} >= 0 places it there, centred, else it is clear of the toast column. */
	private void draw(GuiGraphicsExtractor g, int width, long now, int top) {
		Prompt prompt = shown != null ? shown : leaving;
		float offset = offset(now);
		if (prompt == null || offset <= -HEIGHT - 6) {
			if (shown == null) {
				leaving = null;
			}
			return;
		}
		Font font = Minecraft.getInstance().font;
		// The line is the words before the button, the button, then the words after it (either may be empty).
		Component heading, before, after;
		PadStyle style = prompt.style();
		if (prompt.addPlayer()) {
			heading = Component.literal("Add player");
			PadStyle free = freeStyle.get();
			if (free != null) {
				before = Component.literal("Press");
				after = Component.literal("on a new controller to join");
				style = free;
			} else {
				before = Component.literal("Connect a controller, then press");
				after = Component.empty();
			}
		} else {
			heading = Component.literal("New controller");
			before = Component.literal("Press");
			after = Component.literal("to join");
		}
		int glyph = HearthDraw.glyphWidth(Control.A);
		int afterW = after.getString().isEmpty() ? 0 : 4 + font.width(after);
		int line = font.width(before) + 4 + glyph + afterW;
		int w = Math.max(130, Math.max(font.width(heading), line) + 24);
		int x = top >= 0 ? (width - w) / 2 : Math.max(6, Math.min((width - w) / 2, width - 160 - 8 - w));
		int y = (top >= 0 ? top : 6) + Math.round(offset);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.GLASS_PANEL, x, y, w, HEIGHT);
		HearthDraw.label(g, font, heading, x + 10, y + 6, ORANGE);
		int lx = x + 10;
		HearthDraw.label(g, font, before, lx, y + 19, CREAM);
		lx += font.width(before) + 4;
		HearthDraw.glyph(g, Control.A, style, lx, y + 17);
		if (afterW > 0) {
			HearthDraw.label(g, font, after, lx + glyph + 4, y + 19, CREAM);
		}
		// The confirm window running out.
		float remaining = Math.clamp((prompt.until() - now) / (float) (prompt.until() - prompt.since()), 0f, 1f);
		int barX = x + 8, barW = w - 16, barY = y + HEIGHT - 6;
		g.fill(barX, barY, barX + barW, barY + 2, 0x40FFFFFF);
		g.fill(barX, barY, barX + Math.round(barW * remaining), barY + 2, ORANGE);
	}
}
