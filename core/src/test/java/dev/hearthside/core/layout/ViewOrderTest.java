package dev.hearthside.core.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Swap views (Spec E section 3.5): moving a player earlier or later in the order the layout fills. */
class ViewOrderTest {
	@Test
	void laterSwapsWithTheNextSeat() {
		assertThat(ViewOrder.later(List.of(0, 1, 2), 0)).containsExactly(1, 0, 2);
	}

	@Test
	void earlierSwapsWithThePreviousSeat() {
		assertThat(ViewOrder.earlier(List.of(0, 1, 2), 2)).containsExactly(0, 2, 1);
	}

	@Test
	void theEndsStayPut() {
		assertThat(ViewOrder.earlier(List.of(0, 1, 2), 0)).containsExactly(0, 1, 2);
		assertThat(ViewOrder.later(List.of(0, 1, 2), 2)).containsExactly(0, 1, 2);
	}

	@Test
	void anUnknownSeatChangesNothing() {
		assertThat(ViewOrder.later(List.of(0, 1), 5)).containsExactly(0, 1);
	}

	@Test
	void theInputIsNotChanged() {
		List<Integer> order = new java.util.ArrayList<>(List.of(0, 1));
		ViewOrder.later(order, 0);
		assertThat(order).containsExactly(0, 1);
	}
}
