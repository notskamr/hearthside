package dev.hearthside.core.layout;

import java.util.List;

/** Divides a screen into one viewport per player. Index 0 is player 1. */
public interface LayoutStrategy {
	List<Rect> layout(int playerCount, Rect screen);
}
