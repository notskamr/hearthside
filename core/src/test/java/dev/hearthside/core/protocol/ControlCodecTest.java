package dev.hearthside.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hearthside.core.layout.Rect;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ControlCodecTest {
	private static DataInputStream input(byte[] bytes) {
		return new DataInputStream(new ByteArrayInputStream(bytes));
	}

	private static byte[] frame(int length, int type, byte... payload) {
		return ByteBuffer.allocate(6 + payload.length).putInt(length).putShort((short) type).put(payload).array();
	}

	@Test
	void unknownTypeIsSkippedAndNextMessageStillDecodes() throws IOException {
		var bytes = new ByteArrayOutputStream();
		var out = new DataOutputStream(bytes);
		out.write(frame(2 + 3, 999, (byte) 1, (byte) 2, (byte) 3));
		ControlCodec.write(new ControlMessage.Lifecycle(LifecycleCommand.PAUSE), out);
		var in = input(bytes.toByteArray());
		assertThat(ControlCodec.read(in)).isEqualTo(new ControlMessage.Unknown(999));
		assertThat(ControlCodec.read(in)).isEqualTo(new ControlMessage.Lifecycle(LifecycleCommand.PAUSE));
	}

	@Test
	void oversizedFrameIsRejectedBeforeAllocation() {
		var in = input(ByteBuffer.allocate(4).putInt(Integer.MAX_VALUE).array());
		assertThatThrownBy(() -> ControlCodec.read(in)).isInstanceOf(ProtocolException.class);
	}

	@Test
	void negativeLengthIsRejected() {
		var in = input(ByteBuffer.allocate(4).putInt(-5).array());
		assertThatThrownBy(() -> ControlCodec.read(in)).isInstanceOf(ProtocolException.class);
	}

	@Test
	void tooShortLengthIsRejected() {
		var in = input(ByteBuffer.allocate(6).putInt(1).putShort((short) 5).array());
		assertThatThrownBy(() -> ControlCodec.read(in)).isInstanceOf(ProtocolException.class);
	}

	@Test
	void truncatedFrameThrowsEof() throws IOException {
		var bytes = new ByteArrayOutputStream();
		ControlCodec.write(new ControlMessage.SetViewport(new Rect(0, 0, 960, 540)), new DataOutputStream(bytes));
		byte[] cut = Arrays.copyOf(bytes.toByteArray(), bytes.size() - 3);
		assertThatThrownBy(() -> ControlCodec.read(input(cut))).isInstanceOf(EOFException.class);
	}

	@Test
	void badLifecycleOrdinalIsRejected() {
		assertThatThrownBy(() -> ControlCodec.read(input(frame(3, 5, (byte) 200))))
				.isInstanceOf(ProtocolException.class);
	}

	@Test
	void shortPayloadIsRejected() {
		// SetViewport needs 16 payload bytes; give it 4.
		assertThatThrownBy(() -> ControlCodec.read(input(frame(2 + 4, 4, new byte[4]))))
				.isInstanceOf(ProtocolException.class);
	}

	@Test
	void trailingPayloadBytesAreRejected() {
		assertThatThrownBy(() -> ControlCodec.read(input(frame(4, 5, (byte) 0, (byte) 0))))
				.isInstanceOf(ProtocolException.class);
	}

	@Test
	void writingUnknownIsAnError() {
		assertThatThrownBy(() -> ControlCodec.write(new ControlMessage.Unknown(7),
				new DataOutputStream(OutputStream.nullOutputStream())))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
