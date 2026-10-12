package dev.hearthside.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hearthside.core.layout.Rect;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

/**
 * Protocol v5 (Spec D section 3.4): SetViewport also carries the party's GUI scale for this guest and the frame size it
 * renders at (smaller than its view under a reduced render resolution). A v4 viewport can't be read: both games run
 * the same jar, and a mismatch is refused at Hello.
 */
class SetViewportV5Test {
	private static ControlMessage roundTrip(ControlMessage message) throws IOException {
		var bytes = new ByteArrayOutputStream();
		ControlCodec.write(message, new DataOutputStream(bytes));
		return ControlCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
	}

	@Test
	void protocolVersionIsFive() {
		assertThat(ControlCodec.PROTOCOL_VERSION).isEqualTo(5);
	}

	@Test
	void setViewportRoundTripsTheScaleAndFrame() throws IOException {
		var message = new ControlMessage.SetViewport(new Rect(960, 0, 960, 1080), 60, 2, 640, 720);
		assertThat(roundTrip(message)).isEqualTo(message);
	}

	@Test
	void theShortFormIsUnforcedAtFullSize() {
		var message = new ControlMessage.SetViewport(new Rect(960, 0, 960, 1080), 144);
		assertThat(message.guiScale()).isZero();
		assertThat(message.frameWidth()).isEqualTo(960);
		assertThat(message.frameHeight()).isEqualTo(1080);
	}

	@Test
	void aZeroSizedViewStillHasAOnePixelFrame() {
		var message = new ControlMessage.SetViewport(new Rect(0, 0, 0, 0), 60);
		assertThat(message.frameWidth()).isEqualTo(1);
		assertThat(message.frameHeight()).isEqualTo(1);
	}

	@Test
	void aFrameLargerThanTheViewIsRejected() {
		assertThatThrownBy(() -> new ControlMessage.SetViewport(new Rect(0, 0, 960, 540), 60, 2, 961, 540))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ControlMessage.SetViewport(new Rect(0, 0, 960, 540), 60, 2, 960, 541))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ControlMessage.SetViewport(new Rect(0, 0, 960, 540), 60, 2, 0, 540))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void theGuiScaleIsInRange() {
		assertThatThrownBy(() -> new ControlMessage.SetViewport(new Rect(0, 0, 960, 540), 60, -1, 960, 540))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ControlMessage.SetViewport(new Rect(0, 0, 960, 540), 60, 33, 960, 540))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(new ControlMessage.SetViewport(new Rect(0, 0, 960, 540), 60, 32, 960, 540).guiScale()).isEqualTo(32);
	}

	@Test
	void aV4ViewportIsUnreadable() {
		// v4's SetViewport: four ints and a short, nothing after.
		byte[] payload = ByteBuffer.allocate(18).putInt(0).putInt(0).putInt(960).putInt(540).putShort((short) 60).array();
		byte[] frame = ByteBuffer.allocate(6 + payload.length).putInt(2 + payload.length).putShort((short) 4).put(payload).array();
		assertThatThrownBy(() -> ControlCodec.read(new DataInputStream(new ByteArrayInputStream(frame))))
				.isInstanceOf(ProtocolException.class);
	}
}
