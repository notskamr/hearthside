package dev.hearthside.fabric.client.ui;

import dev.hearthside.core.input.Control;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.map.PartyMapView;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The party panel in the free quarter with three players (Spec A section 6): a live map of the party's area, north up,
 * with an arrow per player in their colour pointing the way they face; the day, time and weather; who is playing or
 * still joining; and the join prompt when a controller is free. It always fits its quarter: equal margins, the map as
 * tall as the panel, the details beside it, and lines that don't fit are dropped (weather first, then the day) or
 * shortened, never cut off. Render thread.
 */
public final class PartyPanel {
	private static final int ORANGE = 0xFFFF9A4A, CREAM = 0xFFF3DFB6, MUTED = 0xFFB8A48C;

	public enum Weather { CLEAR, RAIN, THUNDER }

	/** A player on the panel: their name, colour (0xRRGGBB), and what they're doing (null while playing). */
	public record Seat(String name, int colour, String status) {}

	public record State(Identifier map, List<PartyMapView.Marker> markers, List<Seat> seats, long dayTime,
			Weather weather, boolean joinPrompt, PadStyle joinStyle) {}

	/** Where it last drew, and the area it was given. For the E2E. */
	public static volatile Rect lastPanel, lastBounds;

	private PartyPanel() {}

	/** Draws the panel at {@code alpha} (it fades in and out with the layout). */
	public static void draw(GuiGraphicsExtractor g, Font font, Rect panel, State state, float alpha) {
		int white = fade(0xFFFFFFFF, alpha);
		CardPainter.board(g, panel, alpha);
		int margin = Math.max(4, Math.round(Math.min(panel.width(), panel.height()) * 0.07f));
		Rect box = new Rect(panel.x() + margin, panel.y() + margin, panel.width() - 2 * margin, panel.height() - 2 * margin);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.GLASS_PANEL, box.x(), box.y(), box.width(), box.height(), white);
		int pad = 6;
		int map = Math.min(box.height() - 2 * pad, Math.round(box.width() * 0.55f));
		int mx = box.x() + pad, my = box.y() + (box.height() - map) / 2;
		// Parchment where nothing is loaded yet, the ground's colours over it.
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.PARCHMENT_CARD, mx - 1, my - 1, map + 2, map + 2, white);
		if (state.map() != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, state.map(), mx, my, 0, 0, map, map, 128, 128, 128, 128, white);
		}
		int arrow = Math.clamp(map / 13, 5, 9);
		for (PartyMapView.Marker marker : state.markers()) {
			drawArrow(g, mx + marker.u() * map, my + marker.v() * map, marker.angleDegrees(), arrow, marker.colour(), alpha);
		}
		int right = box.x() + box.width() - pad;
		int dx = mx + map + 8;
		int width = right - dx;
		int y = box.y() + pad;
		HearthDraw.label(g, font, Component.literal("Party"), dx, y, fade(ORANGE, alpha));
		y += 12;
		// The day: a sun or moon, the day and time, and the weather when it fits.
		long time = Math.floorMod(state.dayTime(), 24000L);
		long day = state.dayTime() / 24000L + 1;
		boolean night = time >= 13500 && time < 23000;
		Identifier sky = night ? HearthSprites.MOON : HearthSprites.SUN;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sky, dx, y, 8, 8, white);
		String dayLine = "Day " + day + " · " + timeOfDay(time);
		String weather = switch (state.weather()) {
			case CLEAR -> "";
			case RAIN -> " · Rain";
			case THUNDER -> " · Storm";
		};
		int textX = dx + 11, textWidth = width - 11;
		if (font.width(dayLine + weather) <= textWidth) {
			dayLine += weather;
		} else if (font.width(dayLine) > textWidth) {
			dayLine = "Day " + day;
		}
		if (font.width(dayLine) <= textWidth) {
			HearthDraw.label(g, font, Component.literal(dayLine), textX, y, fade(MUTED, alpha));
			y += 12;
		}
		int joinY = box.y() + box.height() - pad - 11;
		for (Seat seat : state.seats()) {
			if (y + 9 > (state.joinPrompt() ? joinY - 2 : box.y() + box.height() - pad)) {
				break;
			}
			g.fill(dx, y + 2, dx + 5, y + 7, fade(0xFF000000 | seat.colour(), alpha));
			String status = seat.status() == null ? "" : " " + seat.status();
			int nameWidth = width - 8 - font.width(status);
			String name = font.plainSubstrByWidth(seat.name(), Math.max(12, nameWidth));
			HearthDraw.label(g, font, Component.literal(name), dx + 8, y, fade(CREAM, alpha));
			if (!status.isEmpty() && font.width(name + status) <= width - 8) {
				HearthDraw.label(g, font, Component.literal(status), dx + 8 + font.width(name), y, fade(MUTED, alpha));
			}
			y += 11;
		}
		if (state.joinPrompt()) {
			int gw = HearthDraw.glyph(g, Control.A, state.joinStyle(), dx, joinY, white);
			HearthDraw.label(g, font, Component.literal("to join"), dx + gw + 3, joinY + 2, fade(MUTED, alpha));
		}
		lastPanel = panel;
		lastBounds = new Rect(box.x(), box.y(), box.width(), box.height());
	}

	/** {@code argb} with its alpha scaled by {@code alpha}. */
	private static int fade(int argb, float alpha) {
		return Math.round((argb >>> 24) * alpha) << 24 | (argb & 0xFFFFFF);
	}

	/** A player's arrow: a dark outline with a cream rim, filled with their colour, turned to face their way. */
	private static void drawArrow(GuiGraphicsExtractor g, float x, float y, float degrees, int size, int colour, float alpha) {
		int height = size + size / 4;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().rotate((float) Math.toRadians(degrees));
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.MAP_ARROW_RIM, -size / 2, -height / 2, size, height,
				fade(0xFFFFFFFF, alpha));
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HearthSprites.MAP_ARROW_FILL, -size / 2, -height / 2, size, height,
				fade(0xFF000000 | colour, alpha));
		g.pose().popMatrix();
	}

	private static String timeOfDay(long time) {
		if (time >= 23000 || time < 1000) {
			return "Dawn";
		}
		if (time < 5500) {
			return "Morning";
		}
		if (time < 6500) {
			return "Noon";
		}
		if (time < 11500) {
			return "Afternoon";
		}
		if (time < 13500) {
			return "Evening";
		}
		return "Night";
	}
}
