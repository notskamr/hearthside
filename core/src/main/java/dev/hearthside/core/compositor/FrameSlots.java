package dev.hearthside.core.compositor;

import java.util.OptionalInt;

/**
 * Lock-free hand-over of frames from one producer (a guest's game) to one consumer (the host's compositor) over a
 * {@link SlotTable}. The producer writes only FREE slots (or reclaims the oldest READY one); the consumer takes the
 * newest READY slot and never has it overwritten while it holds it. Neither side ever waits for the other.
 *
 * <p>Each game keeps two frames in flight on its GPU, so each side may hold slots for three frames. The producer holds
 * the slot it is drawing plus up to two whose fences haven't signalled (claimed, not yet published); the consumer holds
 * this frame's copy plus up to two earlier copies whose fences haven't signalled (acquired, not yet released). When the
 * producer claims its next slot it therefore holds at most two and the consumer at most three, and
 * {@link #DEFAULT_SLOTS} still leaves it one.
 */
public final class FrameSlots {
	/** Up to two in flight on the producer's side, up to three on the consumer's, and one to draw into. */
	public static final int DEFAULT_SLOTS = 6;

	private final SlotTable table;

	public FrameSlots(SlotTable table) {
		this.table = table;
	}

	public SlotTable table() {
		return table;
	}

	/** Claims a slot to draw into: a FREE one, else the oldest READY one. Empty if every slot is held. */
	public OptionalInt beginWrite() {
		for (int attempt = 0; attempt < 8; attempt++) {
			for (int i = 0; i < table.slots(); i++) {
				if (table.state(i) == SlotState.FREE && table.compareAndSet(i, SlotState.FREE, SlotState.WRITING)) {
					return OptionalInt.of(i);
				}
			}
			int oldest = -1;
			long oldestFrame = Long.MAX_VALUE;
			for (int i = 0; i < table.slots(); i++) {
				if (table.state(i) == SlotState.READY && table.frame(i) < oldestFrame) {
					oldest = i;
					oldestFrame = table.frame(i);
				}
			}
			if (oldest < 0) {
				return OptionalInt.empty();
			}
			if (table.compareAndSet(oldest, SlotState.READY, SlotState.WRITING)) {
				return OptionalInt.of(oldest);
			}
			// The consumer took it between our scan and our claim; look again.
		}
		return OptionalInt.empty();
	}

	/** Marks a written slot as holding {@code frame}. The frame number is written before the state flips. */
	public void publish(int slot, long frame) {
		table.setFrame(slot, frame);
		if (!table.compareAndSet(slot, SlotState.WRITING, SlotState.READY)) {
			throw new IllegalStateException("Slot " + slot + " was not being written");
		}
	}

	/** Gives back a slot without publishing it (a dropped frame). */
	public void abandon(int slot) {
		table.compareAndSet(slot, SlotState.WRITING, SlotState.FREE);
	}

	/**
	 * Takes the newest READY slot whose frame is newer than {@code afterFrame}, and frees older READY slots. Read the
	 * frame number from the table after this returns: it cannot change while the slot is held.
	 */
	public OptionalInt acquireNewest(long afterFrame) {
		for (int attempt = 0; attempt < 8; attempt++) {
			int newest = -1;
			long newestFrame = afterFrame;
			for (int i = 0; i < table.slots(); i++) {
				if (table.state(i) == SlotState.READY && table.frame(i) > newestFrame) {
					newest = i;
					newestFrame = table.frame(i);
				}
			}
			if (newest < 0) {
				return OptionalInt.empty();
			}
			if (!table.compareAndSet(newest, SlotState.READY, SlotState.READING)) {
				continue; // the producer reclaimed it; look again
			}
			if (table.frame(newest) <= afterFrame) {
				// Reclaimed and republished between scan and claim with an older frame cannot happen (frames only
				// grow), but stay safe: hand it back.
				table.compareAndSet(newest, SlotState.READING, SlotState.READY);
				return OptionalInt.empty();
			}
			long taken = table.frame(newest);
			for (int i = 0; i < table.slots(); i++) {
				if (i != newest && table.state(i) == SlotState.READY && table.frame(i) < taken) {
					table.compareAndSet(i, SlotState.READY, SlotState.FREE);
				}
			}
			return OptionalInt.of(newest);
		}
		return OptionalInt.empty();
	}

	/** Returns a slot the consumer has finished with. */
	public void release(int slot) {
		table.compareAndSet(slot, SlotState.READING, SlotState.FREE);
	}
}
