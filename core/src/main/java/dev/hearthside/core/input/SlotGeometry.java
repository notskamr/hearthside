package dev.hearthside.core.input;

import java.util.List;
import java.util.Optional;

/**
 * Bedrock-style inventory cursor help: a resting cursor glides onto a nearby slot, and a D-pad press
 * hops to the nearest slot in that direction. Coordinates are GUI pixels.
 */
public final class SlotGeometry {
	public record Point(double x, double y) {}

	/** A hop only considers slots within this angle of the D-pad direction. */
	private static final double MAX_HOP_COS = Math.cos(Math.toRadians(45)) - 1e-9;
	private static final double CENTRED = 0.5;

	private SlotGeometry() {}

	/** Half of the way to the nearest slot centre within {@code radius}; empty when there is none or already centred. */
	public static Optional<Point> snap(List<Point> centres, double x, double y, double radius) {
		Point nearest = null;
		double best = radius;
		for (Point c : centres) {
			double d = Math.hypot(c.x() - x, c.y() - y);
			if (d <= best) {
				best = d;
				nearest = c;
			}
		}
		if (nearest == null || best < CENTRED) {
			return Optional.empty();
		}
		return Optional.of(new Point((x + nearest.x()) / 2, (y + nearest.y()) / 2));
	}

	/** The nearest slot centre within 45° of direction ({@code dx}, {@code dy}); empty at an edge. */
	public static Optional<Point> hop(List<Point> centres, double x, double y, int dx, int dy) {
		double length = Math.hypot(dx, dy);
		if (length == 0) {
			return Optional.empty();
		}
		double ux = dx / length, uy = dy / length;
		Point best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Point c : centres) {
			double vx = c.x() - x, vy = c.y() - y;
			double d = Math.hypot(vx, vy);
			if (d < 1 || (vx * ux + vy * uy) / d < MAX_HOP_COS) {
				continue;
			}
			if (d < bestDistance) {
				bestDistance = d;
				best = c;
			}
		}
		return Optional.ofNullable(best);
	}
}
