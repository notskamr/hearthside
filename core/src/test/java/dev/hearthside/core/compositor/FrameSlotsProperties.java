package dev.hearthside.core.compositor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.OptionalInt;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class FrameSlotsProperties {
	enum Op { BEGIN_WRITE, PUBLISH, ABANDON, ACQUIRE, RELEASE_OLDEST }

	@Property
	void interleavings(@ForAll("ops") List<Op> ops) {
		int n = FrameSlots.DEFAULT_SLOTS;
		ArraySlotTable table = new ArraySlotTable(n, 1);
		FrameSlots slots = new FrameSlots(table);
		int writing = -1;
		long nextFrame = 1, lastSeen = 0;
		Deque<Integer> reading = new ArrayDeque<>();
		for (Op op : ops) {
			switch (op) {
				case BEGIN_WRITE -> {
					if (writing < 0) {
						OptionalInt slot = slots.beginWrite();
						// The consumer holds at most 2 slots, so with DEFAULT_SLOTS slots the producer always finds one.
						if (reading.size() <= 2) {
							assertThat(slot).isPresent();
						}
						if (slot.isPresent()) {
							assertThat(reading).doesNotContain(slot.getAsInt());
							writing = slot.getAsInt();
						}
					}
				}
				case PUBLISH -> {
					if (writing >= 0) {
						slots.publish(writing, nextFrame++);
						writing = -1;
					}
				}
				case ABANDON -> {
					if (writing >= 0) {
						slots.abandon(writing);
						writing = -1;
					}
				}
				case ACQUIRE -> {
					if (reading.size() < 2) {
						OptionalInt slot = slots.acquireNewest(lastSeen);
						if (slot.isPresent()) {
							long frame = table.frame(slot.getAsInt());
							assertThat(frame).isGreaterThan(lastSeen);
							assertThat(slot.getAsInt()).isNotEqualTo(writing);
							lastSeen = frame;
							reading.addLast(slot.getAsInt());
						}
					}
				}
				case RELEASE_OLDEST -> {
					if (!reading.isEmpty()) {
						slots.release(reading.removeFirst());
					}
				}
			}
			long readingCount = 0, writingCount = 0;
			for (int i = 0; i < n; i++) {
				if (table.state(i) == SlotState.READING) readingCount++;
				if (table.state(i) == SlotState.WRITING) writingCount++;
			}
			assertThat(readingCount).isEqualTo(reading.size());
			assertThat(writingCount).isEqualTo(writing >= 0 ? 1 : 0);
		}
	}

	@Provide
	Arbitrary<List<Op>> ops() {
		return Arbitraries.of(Op.class).list().ofMaxSize(200);
	}
}
