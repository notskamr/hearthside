package dev.hearthside.core.compositor;

/** Ownership of one shared frame slot. Stored as the ordinal in shared memory: append-only. */
public enum SlotState {
	/** Nobody holds it; the producer may claim it. */
	FREE,
	/** The producer (a guest) is drawing into it. */
	WRITING,
	/** Holds a finished frame the consumer has not taken; the producer may reclaim it if it is stale. */
	READY,
	/** The consumer (the host) is drawing from it; nobody else touches it. */
	READING;

	private static final SlotState[] VALUES = values();

	static SlotState ofOrdinal(int ordinal) {
		return VALUES[ordinal];
	}
}
