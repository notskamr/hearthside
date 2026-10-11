package dev.hearthside.core.map;

import java.util.List;

/**
 * Which square of the world the party map shows, and where each player's marker sits on it (Spec A section 6): north
 * up, centred on the party and just big enough for everyone plus a margin. When the party is spread too far for that,
 * the map stays on player 1 (the first player) and the others are arrows on its edge, pointing toward them. Centre and
 * radius move in whole steps, so small moves don't redraw the map.
 */
public final class PartyMapView {
	public static final int MIN_RADIUS = 48, MAX_RADIUS = 256, MARGIN = 16, STEP = 16; // blocks

	/** A player's position and facing (Minecraft's yaw: 0 = south, 90 = west, 180 = north, -90 = east). */
	public record Player(String name, int colour, double x, double z, float yaw) {}

	/**
	 * Where a marker goes: {@code u}, {@code v} from 0 to 1 across and down the map; its angle clockwise from up
	 * (north); {@code atEdge} when the player is off the map (the angle then points toward them).
	 */
	public record Marker(String name, int colour, float u, float v, float angleDegrees, boolean atEdge) {}

	private final int centreX, centreZ, radius;

	private PartyMapView(int centreX, int centreZ, int radius) {
		this.centreX = centreX;
		this.centreZ = centreZ;
		this.radius = radius;
	}

	public static PartyMapView frame(List<Player> players) {
		if (players.isEmpty()) {
			return new PartyMapView(0, 0, MIN_RADIUS);
		}
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (Player p : players) {
			minX = Math.min(minX, p.x());
			maxX = Math.max(maxX, p.x());
			minZ = Math.min(minZ, p.z());
			maxZ = Math.max(maxZ, p.z());
		}
		double needed = Math.max(maxX - minX, maxZ - minZ) / 2 + MARGIN;
		if (needed > MAX_RADIUS) {
			Player first = players.getFirst();
			return new PartyMapView(snap(first.x()), snap(first.z()), MAX_RADIUS);
		}
		int radius = Math.clamp((int) Math.ceil(needed / STEP) * STEP, MIN_RADIUS, MAX_RADIUS);
		return new PartyMapView(snap((minX + maxX) / 2), snap((minZ + maxZ) / 2), radius);
	}

	private static int snap(double coordinate) {
		return (int) Math.round(coordinate / STEP) * STEP;
	}

	public int centreX() {
		return centreX;
	}

	public int centreZ() {
		return centreZ;
	}

	public int radius() {
		return radius;
	}

	public Marker marker(Player player) {
		double u = (player.x() - (centreX - radius)) / (2.0 * radius);
		double v = (player.z() - (centreZ - radius)) / (2.0 * radius);
		boolean atEdge = u < 0 || u > 1 || v < 0 || v > 1;
		float angle;
		if (atEdge) {
			// Toward the player, from the map's centre: clockwise from north.
			angle = (float) Math.toDegrees(Math.atan2(player.x() - centreX, -(player.z() - centreZ)));
		} else {
			angle = player.yaw() + 180;
		}
		angle = ((angle % 360) + 360) % 360;
		return new Marker(player.name(), player.colour(), (float) Math.clamp(u, 0, 1), (float) Math.clamp(v, 0, 1), angle,
				atEdge);
	}

	/** Whether the map shows the same square (so it needn't be drawn again). */
	public boolean sameArea(PartyMapView other) {
		return other != null && other.centreX == centreX && other.centreZ == centreZ && other.radius == radius;
	}
}
