package dev.hearthside.fabric.client.ui;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.layout.BelowToasts;
import dev.hearthside.core.layout.Ease;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.ViewFrame;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.compositor.OverlayContext;
import dev.hearthside.fabric.client.compositor.OverlayLayer;
import dev.hearthside.fabric.client.input.ToastRects;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
	/** Kept between the toast and one of Minecraft's above it. */
	private static final int GAP = 4;
	/** How fast it moves to a new place below Minecraft's toasts, in GUI pixels a second (a toast's height in ~0.15 s). */
	private static final float SETTLE_SPEED = 220f;
	/** How far it drops as it fades in under one of Minecraft's toasts: from that toast's edge, never over it. */
	private static final int FADE_DROP = GAP;
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
	/** Where the toast's top sits now, gliding to its place clear of Minecraft's toasts; NaN while out of sight. */
	private float placed = Float.NaN;
	private long placedAt;
	/** The slide being drawn (a showing's start, or minus a leaving's), and whether it fades rather than slides. */
	private long slideKey = Long.MIN_VALUE;
	private boolean fadeSlide;
	/**
	 * For the E2E: where the toast was last drawn, Minecraft's toasts it kept clear of then, player 1's view it mapped
	 * them from (null on player 1's HUD), and its line ("A" for the button).
	 */
	private Rect e2eDrawn;
	private List<Rect> e2eToasts = List.of();
	private Rect e2eView;
	private String e2eLine = "";

	/** E2E: where the toast was last drawn (null before it ever was), in the units it was drawn in. */
	public Rect e2eDrawn() {
		return e2eDrawn;
	}

	/** E2E: Minecraft's toasts as the toast last saw them, in the same units. */
	public List<Rect> e2eToasts() {
		return e2eToasts;
	}

	/** E2E: player 1's view as the toast last mapped their toasts from it, in the same units; null on their HUD. */
	public Rect e2eView() {
		return e2eView;
	}

	/** E2E: the toast's line as last drawn, with "A" for the button. */
	public String e2eLine() {
		return e2eLine;
	}

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
		// Over split play: centred on the window, below the first row of name tags, and below any of Minecraft's toasts
		// in player 1's view it would meet.
		draw(g, context.guiWidth(), context.nanos(), 26, hostToasts(context));
	}

	/** On player 1's own screen, alone: centred at the top, clear of Minecraft's toasts (the right 160 GUI px). */
	public void draw(GuiGraphicsExtractor g, int width, long now) {
		e2eView = null;
		draw(g, width, now, -1, ToastRects.visible(Minecraft.getInstance()));
	}

	/** Minecraft's toasts in player 1's view (their GUI pixels), in the overlay's units over that view. */
	private List<Rect> hostToasts(OverlayContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ViewFrame own = context.views().stream().filter(v -> v.seat() == 0).findFirst().orElse(null);
		int guiWidth = minecraft.getWindow().getGuiScaledWidth(), guiHeight = minecraft.getWindow().getGuiScaledHeight();
		if (own == null || guiWidth <= 0 || guiHeight <= 0) {
			return List.of();
		}
		Rect view = own.rect();
		e2eView = view;
		float sx = view.width() / (float) guiWidth, sy = view.height() / (float) guiHeight;
		List<Rect> toasts = new ArrayList<>();
		for (Rect r : ToastRects.visible(minecraft)) {
			int x = view.x() + (int) Math.floor(r.x() * sx), y = view.y() + (int) Math.floor(r.y() * sy);
			int right = view.x() + (int) Math.ceil((r.x() + r.width()) * sx);
			int bottom = view.y() + (int) Math.ceil((r.y() + r.height()) * sy);
			toasts.add(new Rect(x, y, right - x, bottom - y));
		}
		return toasts;
	}

	/**
	 * Draws the toast at the top; {@code top} >= 0 places it there, centred, else it is clear of the toast column. Where
	 * it would meet one of Minecraft's {@code toasts} it sits just below the lowest, gliding there and back as they come
	 * and go; it fades in there instead of sliding down across that toast.
	 */
	private void draw(GuiGraphicsExtractor g, int width, long now, int top, List<Rect> toasts) {
		Prompt prompt = shown != null ? shown : leaving;
		float offset = offset(now);
		if (prompt == null || offset <= -HEIGHT - 6) {
			if (shown == null) {
				leaving = null;
			}
			placed = Float.NaN;
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
		int own = top >= 0 ? top : 6;
		int wanted = BelowToasts.top(own, x, w, HEIGHT, GAP, toasts);
		if (Float.isNaN(placed) || reduceMotion) {
			placed = wanted; // it appears where it belongs
		} else {
			// Glides, so its own slide never jumps when a toast comes or goes.
			float step = SETTLE_SPEED * Math.max(0, now - placedAt) / 1e9f;
			placed = placed < wanted ? Math.min(wanted, placed + step) : Math.max(wanted, placed - step);
		}
		placedAt = now;
		int base = Math.round(placed);
		// Each slide, in or out, keeps the way it started: a full slide from the top, or, under one of Minecraft's toasts
		// (which a full slide would cross), a short drop as it fades in, or rise as it fades out.
		long slide = shown != null ? prompt.since() : -leavingSince;
		if (slide != slideKey) {
			slideKey = slide;
			fadeSlide = base > own;
		}
		float progress = 1 + offset / HEIGHT; // 0 out of sight, 1 in place
		int y = base + (fadeSlide ? -Math.round(FADE_DROP * (1 - progress)) : Math.round(offset));
		float alpha = fadeSlide ? Math.clamp(progress, 0f, 1f) : 1f;
		e2eDrawn = new Rect(x, y, w, HEIGHT);
		e2eToasts = toasts;
		e2eLine = before.getString() + " A" + (afterW > 0 ? " " + after.getString() : "");
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.GLASS_PANEL, x, y, w, HEIGHT, faded(0xFFFFFFFF, alpha));
		HearthDraw.label(g, font, heading, x + 10, y + 6, faded(ORANGE, alpha));
		int lx = x + 10;
		HearthDraw.label(g, font, before, lx, y + 19, faded(CREAM, alpha));
		lx += font.width(before) + 4;
		HearthDraw.glyph(g, Control.A, style, lx, y + 17, faded(0xFFFFFFFF, alpha));
		if (afterW > 0) {
			HearthDraw.label(g, font, after, lx + glyph + 4, y + 19, faded(CREAM, alpha));
		}
		// The confirm window running out.
		float remaining = Math.clamp((prompt.until() - now) / (float) (prompt.until() - prompt.since()), 0f, 1f);
		int barX = x + 8, barW = w - 16, barY = y + HEIGHT - 6;
		g.fill(barX, barY, barX + barW, barY + 2, faded(0x40FFFFFF, alpha));
		g.fill(barX, barY, barX + Math.round(barW * remaining), barY + 2, faded(ORANGE, alpha));
	}

	/** {@code argb} with its alpha scaled by {@code alpha}. */
	private static int faded(int argb, float alpha) {
		return Math.round((argb >>> 24) * alpha) << 24 | argb & 0xFFFFFF;
	}
}
