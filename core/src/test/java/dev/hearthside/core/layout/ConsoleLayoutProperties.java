package dev.hearthside.core.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class ConsoleLayoutProperties {
	@Property
	void tilesScreenExactly(@ForAll @IntRange(min = 1, max = 4) int players,
			@ForAll @IntRange(min = 2, max = 8192) int w,
			@ForAll @IntRange(min = 2, max = 8192) int h,
			@ForAll @IntRange(min = -4000, max = 4000) int x,
			@ForAll @IntRange(min = -4000, max = 4000) int y,
			@ForAll TwoPlayerSplit split) {
		Rect screen = new Rect(x, y, w, h);
		List<Rect> rects = new ConsoleLayout(split).layout(players, screen);
		assertThat(rects).hasSize(players).allSatisfy(r -> assertThat(screen.contains(r)).isTrue());
		assertThat(rects.stream().mapToLong(Rect::area).sum()).isEqualTo(screen.area());
		for (int i = 0; i < rects.size(); i++) {
			for (int j = i + 1; j < rects.size(); j++) {
				assertThat(rects.get(i).intersects(rects.get(j))).isFalse();
			}
		}
	}
}
