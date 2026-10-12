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

/**
 * The real shape of the hand-over (final review I2). Minecraft keeps two frames in flight in each game, so the guest
 * can hold up to three slots it hasn't published (the one it is drawing plus two whose fences haven't signalled), and
 * the host up to three it hasn't released (this frame's copy plus two earlier copies whose fences haven't signalled).
 * With {@link FrameSlots#DEFAULT_SLOTS} slots the guest must still always find one to draw into.
 */
class FrameSlotsInFlightProperties {
	static final int GUEST_IN_FLIGHT = 3, HOST_IN_FLIGHT = 3;

	enum Op { DRAW, FENCE_SIGNALS, HOST_TAKES_NEWEST, HOST_COPY_SIGNALS }

	@Property(tries = 2000)
	void theGuestAlwaysFindsASlot(@ForAll("ops") List<Op> ops) {
		ArraySlotTable table = new ArraySlotTable(FrameSlots.DEFAULT_SLOTS, 1);
		FrameSlots slots = new FrameSlots(table);
		Deque<Integer> guest = new ArrayDeque<>(); // claimed, not yet published (oldest first)
		Deque<Integer> host = new ArrayDeque<>(); // acquired, not yet released (oldest first)
		long nextFrame = 1, lastSeen = 0;
		for (Op op : ops) {
			switch (op) {
				case DRAW -> {
					if (guest.size() < GUEST_IN_FLIGHT) {
						OptionalInt slot = slots.beginWrite();
						assertThat(slot).as("guest holds %d, host holds %d", guest.size(), host.size()).isPresent();
						guest.addLast(slot.getAsInt());
					}
				}
				case FENCE_SIGNALS -> {
					if (!guest.isEmpty()) {
						slots.publish(guest.removeFirst(), nextFrame++);
					}
				}
				case HOST_TAKES_NEWEST -> {
					if (host.size() < HOST_IN_FLIGHT) {
						OptionalInt slot = slots.acquireNewest(lastSeen);
						if (slot.isPresent()) {
							lastSeen = table.frame(slot.getAsInt());
							host.addLast(slot.getAsInt());
						}
					}
				}
				case HOST_COPY_SIGNALS -> {
					if (!host.isEmpty()) {
						slots.release(host.removeFirst());
					}
				}
			}
		}
	}

	@Provide
	Arbitrary<List<Op>> ops() {
		return Arbitraries.of(Op.class).list().ofMinSize(1).ofMaxSize(200);
	}
}
