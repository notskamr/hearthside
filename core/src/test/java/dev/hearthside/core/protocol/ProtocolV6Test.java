package dev.hearthside.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Protocol v6 (Spec E section 3.2): a guest tells player 1's game whether it's paused, and player 1's game tells each
 * guest whether the world is frozen and who is still playing.
 */
class ProtocolV6Test {
	private static ControlMessage roundTrip(ControlMessage message) throws IOException {
		var bytes = new ByteArrayOutputStream();
		ControlCodec.write(message, new DataOutputStream(bytes));
		return ControlCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
	}

	@Test
	void protocolVersionIsSix() {
		assertThat(ControlCodec.PROTOCOL_VERSION).isEqualTo(6);
	}

	@Test
	void pauseStateRoundTrips() throws IOException {
		assertThat(roundTrip(new ControlMessage.PauseState(true))).isEqualTo(new ControlMessage.PauseState(true));
		assertThat(roundTrip(new ControlMessage.PauseState(false))).isEqualTo(new ControlMessage.PauseState(false));
	}

	@Test
	void pauseStatusRoundTrips() throws IOException {
		var running = new ControlMessage.PauseStatus(false, List.of("Alex", "Sam"));
		assertThat(roundTrip(running)).isEqualTo(running);
		var frozen = new ControlMessage.PauseStatus(true, List.of());
		assertThat(roundTrip(frozen)).isEqualTo(frozen);
	}

	@Test
	void pauseStatusCopiesItsNames() {
		var names = new java.util.ArrayList<>(List.of("Alex"));
		var status = new ControlMessage.PauseStatus(false, names);
		names.add("Sam");
		assertThat(status.stillPlaying()).containsExactly("Alex");
		assertThatThrownBy(() -> status.stillPlaying().add("Kai")).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void pauseStatusRefusesAbsurdlyManyNames() {
		// Never more than the 3 other local players; a garbled count must not allocate wildly.
		assertThatThrownBy(() -> new ControlMessage.PauseStatus(false, List.of("a", "b", "c", "d", "e", "f", "g", "h", "i")))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
