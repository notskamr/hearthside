package dev.hearthside.core.compositor;

/** How a guest's frames reach the host. Sent on the wire as the ordinal: append-only. */
public enum Transport {
	/** Zero-copy: both processes use the same GPU memory. */
	GPU_SHARED,
	/** Fallback: the guest reads pixels back into shared memory and the host uploads them. */
	COPY
}
