package dev.hearthside.core.compositor;

import java.util.Objects;

/** Picks a guest's frame transport from what each side's GPU, driver and graphics API can do (Spec C section 3.7). */
public final class TransportChoice {
	private TransportChoice() {}

	/**
	 * Whether {@code exporter}'s frames can be shared with {@code importer}. The first reason to copy wins, in
	 * {@link CopyReason} order. An unknown device or driver UUID is never assumed to match anything. The graphics
	 * backend isn't a factor: OpenGL and Vulkan on one GPU and driver share memory.
	 */
	public static TransportDecision decide(SharingFacts exporter, SharingFacts importer, boolean forceCopy) {
		if (forceCopy) {
			return TransportDecision.copy(CopyReason.FORCED);
		}
		if (!exporter.windows() || !importer.windows()) {
			return TransportDecision.copy(CopyReason.NOT_WINDOWS);
		}
		if (!exporter.canImport() || !importer.canImport()) {
			return TransportDecision.copy(CopyReason.NO_IMPORT);
		}
		if (exporter.deviceUuid() == null || !Objects.equals(exporter.deviceUuid(), importer.deviceUuid())) {
			return TransportDecision.copy(CopyReason.DEVICE_MISMATCH);
		}
		if (exporter.driverUuid() == null || !Objects.equals(exporter.driverUuid(), importer.driverUuid())) {
			return TransportDecision.copy(CopyReason.DRIVER_MISMATCH);
		}
		return TransportDecision.shared();
	}

	/** Whether this process can share frames with itself: its facts checked as both exporter and importer. */
	public static TransportDecision decide(SharingFacts self, boolean forceCopy) {
		return decide(self, self, forceCopy);
	}
}
