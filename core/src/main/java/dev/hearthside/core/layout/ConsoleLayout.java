package dev.hearthside.core.layout;

import java.util.List;
import java.util.Objects;

/**
 * Console-style split-screen: 1 = full screen, 2 = halves, 3 = wide top plus two bottom quarters,
 * 4 = quadrants. On odd dimensions the left/top piece gets the floor and the right/bottom piece the
 * remainder.
 */
public final class ConsoleLayout implements LayoutStrategy {
	private final TwoPlayerSplit twoPlayerSplit;

	public ConsoleLayout(TwoPlayerSplit twoPlayerSplit) {
		this.twoPlayerSplit = Objects.requireNonNull(twoPlayerSplit);
	}

	@Override
	public List<Rect> layout(int playerCount, Rect screen) {
		if (playerCount < 1 || playerCount > 4) {
			throw new IllegalArgumentException("Player count must be 1-4, was " + playerCount);
		}
		if (screen.width() < 2 || screen.height() < 2) {
			throw new IllegalArgumentException("Screen too small to split: " + screen);
		}
		int x = screen.x(), y = screen.y();
		int left = screen.width() / 2, right = screen.width() - left;
		int top = screen.height() / 2, bottom = screen.height() - top;

		Rect topLeft = new Rect(x, y, left, top);
		Rect topRight = new Rect(x + left, y, right, top);
		Rect bottomLeft = new Rect(x, y + top, left, bottom);
		Rect bottomRight = new Rect(x + left, y + top, right, bottom);

		return switch (playerCount) {
			case 1 -> List.of(screen);
			case 2 -> twoPlayerSplit == TwoPlayerSplit.VERTICAL
					? List.of(new Rect(x, y, left, screen.height()), new Rect(x + left, y, right, screen.height()))
					: List.of(new Rect(x, y, screen.width(), top), new Rect(x, y + top, screen.width(), bottom));
			case 3 -> List.of(new Rect(x, y, screen.width(), top), bottomLeft, bottomRight);
			default -> List.of(topLeft, topRight, bottomLeft, bottomRight);
		};
	}
}
