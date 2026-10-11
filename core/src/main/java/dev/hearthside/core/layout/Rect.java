package dev.hearthside.core.layout;

/** An axis-aligned pixel rectangle; edges are half-open, so neighbours that share an edge do not overlap. */
public record Rect(int x, int y, int width, int height) {
	public Rect {
		if (width < 0 || height < 0) {
			throw new IllegalArgumentException("Negative size: " + width + "x" + height);
		}
	}

	public long area() {
		return (long) width * height;
	}

	public boolean intersects(Rect o) {
		return x < o.x + o.width && o.x < x + width && y < o.y + o.height && o.y < y + height;
	}

	public boolean contains(Rect o) {
		return o.x >= x && o.y >= y && o.x + o.width <= x + width && o.y + o.height <= y + height;
	}
}
