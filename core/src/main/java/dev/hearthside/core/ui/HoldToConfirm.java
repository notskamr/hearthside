package dev.hearthside.core.ui;

import java.time.Duration;

/** Hold a button for a while to confirm (Spec A section 5.5: deleting a profile). Releasing early cancels. */
public final class HoldToConfirm {
	private final long lengthNanos;
	private long startNanos = -1;
	private boolean done;

	public HoldToConfirm(Duration length) {
		this.lengthNanos = length.toNanos();
	}

	/** @return progress, 0 to 1 */
	public double step(boolean held, long nanos) {
		if (!held) {
			startNanos = -1;
			done = false;
			return 0;
		}
		if (startNanos < 0) {
			startNanos = nanos;
		}
		double progress = Math.min(1.0, (nanos - startNanos) / (double) lengthNanos);
		if (progress >= 1.0) {
			done = true;
		}
		return progress;
	}

	public boolean done() {
		return done;
	}
}
