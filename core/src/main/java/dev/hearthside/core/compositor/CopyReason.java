package dev.hearthside.core.compositor;

/** Why a guest's frames are copied rather than shared, in the order the reasons are checked. */
public enum CopyReason {
	/** The user (or a test) asked for the copy transport. */
	FORCED,
	/** One side isn't on Windows. */
	NOT_WINDOWS,
	/** One side's graphics API can't share GPU memory. */
	NO_IMPORT,
	/** The two sides run on different GPUs, or a GPU is unknown. */
	DEVICE_MISMATCH,
	/** One GPU, but different drivers, or a driver is unknown. */
	DRIVER_MISMATCH
}
