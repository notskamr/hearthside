package dev.hearthside.core.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** How the world picker uses the co-op worlds record (Spec A section 3.1; the user's "last co-op world first"). */
public final class CoopWorlds {
	private CoopWorlds() {}

	/**
	 * The worlds to offer, in order: the most recent co-op world that still exists first, then the rest as given.
	 *
	 * @param levelIds the worlds that exist, in the order to show the rest
	 * @param coop the co-op record, newest first
	 */
	public static List<String> order(List<String> levelIds, List<CoopWorld> coop) {
		List<String> ordered = new ArrayList<>(levelIds);
		for (CoopWorld world : coop) {
			if (ordered.remove(world.levelId())) {
				ordered.addFirst(world.levelId());
				break;
			}
		}
		return ordered;
	}

	/** "Last played with Ember and Birch" (player 1 left out), or empty when nobody else played. */
	public static String playedWith(CoopWorld world, String hostName) {
		List<String> others = world.players().stream()
				.filter(name -> hostName == null || !name.toLowerCase(Locale.ROOT).equals(hostName.toLowerCase(Locale.ROOT)))
				.toList();
		if (others.isEmpty()) {
			return "";
		}
		String names = others.size() == 1 ? others.getFirst()
				: String.join(", ", others.subList(0, others.size() - 1)) + " and " + others.getLast();
		return "Last played with " + names;
	}
}
