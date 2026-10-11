package dev.hearthside.core.compositor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FrameSlotsTest {
	private final ArraySlotTable table = new ArraySlotTable(3, 1);
	private final FrameSlots slots = new FrameSlots(table);

	private int publish(long frame) {
		int slot = slots.beginWrite().orElseThrow();
		slots.publish(slot, frame);
		return slot;
	}

	@Test
	void producerNeverWritesTheSlotTheConsumerReads() {
		publish(1);
		int reading = slots.acquireNewest(0).orElseThrow();
		for (int i = 0; i < 3; i++) {
			int slot = slots.beginWrite().orElseThrow();
			assertThat(slot).isNotEqualTo(reading);
			slots.publish(slot, 2 + i);
		}
	}

	@Test
	void consumerGetsTheNewestReadyFrame() {
		int first = publish(1);
		int second = publish(2);
		int got = slots.acquireNewest(0).orElseThrow();
		assertThat(got).isEqualTo(second);
		assertThat(table.frame(got)).isEqualTo(2);
		assertThat(table.state(first)).isEqualTo(SlotState.FREE);
	}

	@Test
	void nothingNewMeansEmpty() {
		publish(5);
		int got = slots.acquireNewest(0).orElseThrow();
		slots.release(got);
		assertThat(slots.acquireNewest(5)).isEmpty();
	}

	@Test
	void producerReusesStaleReadySlotWhenAllBusy() {
		publish(1);
		int reading = slots.acquireNewest(0).orElseThrow();
		int older = publish(2);
		int newer = publish(3);
		assertThat(Set.of(reading, older, newer)).hasSize(3);
		OptionalInt claimed = slots.beginWrite();
		assertThat(claimed).hasValue(older);
		assertThat(table.state(newer)).isEqualTo(SlotState.READY);
	}

	@Test
	void abandonFreesTheSlot() {
		int slot = slots.beginWrite().orElseThrow();
		slots.abandon(slot);
		assertThat(table.state(slot)).isEqualTo(SlotState.FREE);
	}

	@Test
	void consumerMayHoldTwoSlotsWhileAFenceIsPending() {
		// The host keeps the previous frame's slot until its GPU fence signals; the producer must still find a slot.
		FrameSlots four = new FrameSlots(new ArraySlotTable(FrameSlots.DEFAULT_SLOTS, 1));
		int a = four.beginWrite().orElseThrow();
		four.publish(a, 1);
		int held1 = four.acquireNewest(0).orElseThrow();
		int b = four.beginWrite().orElseThrow();
		four.publish(b, 2);
		int held2 = four.acquireNewest(1).orElseThrow();
		Set<Integer> held = new HashSet<>(Set.of(held1, held2));
		int w = four.beginWrite().orElseThrow();
		assertThat(held).doesNotContain(w);
	}
}
