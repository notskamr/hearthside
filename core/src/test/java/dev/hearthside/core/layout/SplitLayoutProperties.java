package dev.hearthside.core.layout;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.layout.LayoutSettings.ThreePlayer;
import dev.hearthside.core.layout.LayoutSettings.TwoPlayer;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class SplitLayoutProperties {
	@Property
	void viewsNeverOverlapOrLeaveTheScreen(@ForAll @IntRange(min = 2, max = 4000) int width,
			@ForAll @IntRange(min = 2, max = 4000) int height, @ForAll @IntRange(min = 1, max = 4) int players,
			@ForAll TwoPlayer two, @ForAll ThreePlayer three, @ForAll boolean panel, @ForAll @IntRange(min = 0, max = 8) int gap) {
		Rect screen = new Rect(0, 0, width, height);
		LayoutPlan plan = SplitLayout.plan(players, screen, new LayoutSettings(two, three, panel, gap));
		assertThat(plan.players()).hasSize(players);
		List<Rect> all = new ArrayList<>(plan.players());
		plan.panel().ifPresent(all::add);
		long area = 0;
		for (int i = 0; i < all.size(); i++) {
			Rect r = all.get(i);
			assertThat(r.width()).isNotNegative();
			assertThat(r.height()).isNotNegative();
			assertThat(screen.contains(r)).as("%s inside %s", r, screen).isTrue();
			for (int j = i + 1; j < all.size(); j++) {
				assertThat(r.intersects(all.get(j))).as("%s overlaps %s", r, all.get(j)).isFalse();
			}
			area += r.area();
		}
		// Apart from divider gaps (and the empty quarter when three players have no panel), the views fill the screen.
		long gaps = (long) gap * height + (long) gap * width;
		long emptyQuarter = players == 3 && three == ThreePlayer.GRID_WITH_PANEL && !panel ? screen.area() / 4 + width + height : 0;
		assertThat(area).isGreaterThanOrEqualTo(screen.area() - gaps - emptyQuarter);
	}
}
