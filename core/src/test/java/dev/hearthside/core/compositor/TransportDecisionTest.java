package dev.hearthside.core.compositor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Who shares GPU memory and who copies, with the reason (Spec C sections 3.6, 3.7 and 5). */
class TransportDecisionTest {
	static final UUID GPU = new UUID(1, 1), OTHER = new UUID(2, 2), DRV = new UUID(3, 3), DRV2 = new UUID(4, 4);
	static final SharingFacts HERE = new SharingFacts(true, true, GPU, DRV);

	static TransportDecision decide(SharingFacts exporter, SharingFacts importer) {
		return TransportChoice.decide(exporter, importer, false);
	}

	@Test
	void sharedWhenBothSidesMatchOnWindows() {
		TransportDecision d = decide(HERE, HERE);
		assertThat(d).isEqualTo(TransportDecision.shared());
		assertThat(d.transport()).isEqualTo(Transport.GPU_SHARED);
		assertThat(d.reason()).isEmpty();
	}

	@Test
	void forcedCopyWinsOverEverything() {
		assertThat(TransportChoice.decide(HERE, HERE, true)).isEqualTo(TransportDecision.copy(CopyReason.FORCED));
		assertThat(TransportChoice.decide(HERE, new SharingFacts(false, false, OTHER, DRV2), true))
				.isEqualTo(TransportDecision.copy(CopyReason.FORCED));
	}

	@Test
	void notWindowsCopiesOnEitherSide() {
		SharingFacts mac = new SharingFacts(false, true, GPU, DRV);
		assertThat(decide(mac, HERE)).isEqualTo(TransportDecision.copy(CopyReason.NOT_WINDOWS));
		assertThat(decide(HERE, mac)).isEqualTo(TransportDecision.copy(CopyReason.NOT_WINDOWS));
	}

	@Test
	void aSideThatCantImportCopies() {
		SharingFacts intelGl = new SharingFacts(true, false, GPU, DRV);
		assertThat(decide(HERE, intelGl)).isEqualTo(TransportDecision.copy(CopyReason.NO_IMPORT));
		assertThat(decide(intelGl, HERE)).isEqualTo(TransportDecision.copy(CopyReason.NO_IMPORT));
	}

	@Test
	void differentGpusCopy() {
		assertThat(decide(HERE, new SharingFacts(true, true, OTHER, DRV)))
				.isEqualTo(TransportDecision.copy(CopyReason.DEVICE_MISMATCH));
	}

	@Test
	void sameGpuDifferentDriverCopies() {
		assertThat(decide(HERE, new SharingFacts(true, true, GPU, DRV2)))
				.isEqualTo(TransportDecision.copy(CopyReason.DRIVER_MISMATCH));
	}

	@Test
	void reasonsAreCheckedInOrder() {
		assertThat(decide(HERE, new SharingFacts(false, false, OTHER, DRV2)).reason()).contains(CopyReason.NOT_WINDOWS);
		assertThat(decide(HERE, new SharingFacts(true, false, OTHER, DRV2)).reason()).contains(CopyReason.NO_IMPORT);
		assertThat(decide(HERE, new SharingFacts(true, true, OTHER, DRV2)).reason()).contains(CopyReason.DEVICE_MISMATCH);
	}

	@Test
	void anUnknownGpuIsNeverAssumedTheSame() {
		SharingFacts unknown = new SharingFacts(true, true, null, null);
		assertThat(decide(unknown, unknown).reason()).contains(CopyReason.DEVICE_MISMATCH);
		assertThat(decide(HERE, new SharingFacts(true, true, GPU, null)).reason()).contains(CopyReason.DRIVER_MISMATCH);
		assertThat(decide(new SharingFacts(true, false, null, null), HERE).reason()).contains(CopyReason.NO_IMPORT);
	}

	@Test
	void mixedBackendsOnOneGpuShare() {
		// The facts carry no backend: an OpenGL exporter and a Vulkan importer on one GPU and driver share memory.
		SharingFacts openGlGuest = new SharingFacts(true, true, GPU, DRV);
		SharingFacts vulkanHost = new SharingFacts(true, true, GPU, DRV);
		assertThat(decide(openGlGuest, vulkanHost).transport()).isEqualTo(Transport.GPU_SHARED);
	}

	@Test
	void decidingForOneselfComparesWithItself() {
		assertThat(TransportChoice.decide(HERE, false)).isEqualTo(TransportDecision.shared());
		assertThat(TransportChoice.decide(new SharingFacts(true, false, GPU, DRV), false).reason())
				.contains(CopyReason.NO_IMPORT);
		assertThat(TransportChoice.decide(HERE, true).reason()).contains(CopyReason.FORCED);
	}

	@Test
	void aCopyDecisionCopiesAndSaysWhy() {
		TransportDecision d = TransportDecision.copy(CopyReason.DEVICE_MISMATCH);
		assertThat(d.transport()).isEqualTo(Transport.COPY);
		assertThat(d.reason()).contains(CopyReason.DEVICE_MISMATCH);
	}
}
