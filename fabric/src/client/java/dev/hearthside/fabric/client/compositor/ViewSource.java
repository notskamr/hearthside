package dev.hearthside.fabric.client.compositor;

import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.layout.ViewFrame;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Where each player's view is drawn, frame by frame, while the layout glides. Render thread. */
public interface ViewSource {
	/** Every view as drawn at {@code nanos}, in window pixels from the top-left: player 1 (seat 0) first. */
	List<ViewFrame> frames(long nanos);

	/** Where a seat's view will be once motion ends: the size its game should render at. */
	Optional<Rect> target(int seat);

	/** Every seat with a view, including one still fading out. */
	Set<Integer> seats();
}
