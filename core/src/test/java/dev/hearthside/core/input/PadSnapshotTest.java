package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PadSnapshotTest {
	@Test
	void rejectsMissingGuid() {
		assertThatThrownBy(() -> new PadSnapshot(1, null, "Pad", PadStyle.XBOX, InputFrame.NEUTRAL))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void rejectsNonPositiveInstanceId() {
		assertThatThrownBy(() -> new PadSnapshot(0, "g", "Pad", PadStyle.XBOX, InputFrame.NEUTRAL))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
