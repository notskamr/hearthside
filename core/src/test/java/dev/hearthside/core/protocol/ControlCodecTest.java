package dev.hearthside.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hearthside.core.compositor.Transport;
import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.ControlSchemeJson;
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
		ControlCodec.write(new ControlMessage.SetViewport(new Rect(0, 0, 960, 540), 60), new DataOutputStream(bytes));
		byte[] cut = Arrays.copyOf(bytes.toByteArray(), bytes.size() - 3);
		assertThatThrownBy(() -> ControlCodec.read(input(cut))).isInstanceOf(EOFException.class);
	}

	@Test
	void disconnectIsAppendedToLifecycleCommands() throws IOException {
		// Append-only: the ordinal is the wire format.
		assertThat(LifecycleCommand.DISCONNECT.ordinal()).isEqualTo(3);
		var bytes = new ByteArrayOutputStream();
		ControlCodec.write(new ControlMessage.Lifecycle(LifecycleCommand.DISCONNECT), new DataOutputStream(bytes));
		assertThat(ControlCodec.read(input(bytes.toByteArray())))
				.isEqualTo(new ControlMessage.Lifecycle(LifecycleCommand.DISCONNECT));
	}

	@Test
	void badLifecycleOrdinalIsRejected() {
		assertThatThrownBy(() -> ControlCodec.read(input(frame(3, 5, (byte) 200))))
				.isInstanceOf(ProtocolException.class);
	}

	@Test
	void shortPayloadIsRejected() {
		// SetViewport needs 20 payload bytes; give it 4.
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

	private static ControlMessage roundTrip(ControlMessage message) throws IOException {
		var bytes = new ByteArrayOutputStream();
		ControlCodec.write(message, new DataOutputStream(bytes));
		return ControlCodec.read(input(bytes.toByteArray()));
	}

	@Test
	void setViewportRoundTripsRefresh() throws IOException {
		var message = new ControlMessage.SetViewport(new Rect(960, 0, 960, 1080), 144);
		assertThat(roundTrip(message)).isEqualTo(message);
	}

	@Test
	void setViewportRejectsRefreshOutOfRange() {
		assertThatThrownBy(() -> new ControlMessage.SetViewport(new Rect(0, 0, 1, 1), 0))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ControlMessage.SetViewport(new Rect(0, 0, 1, 1), 1001))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void frameSlotsReadyRoundTrips() throws IOException {
		var message = new ControlMessage.FrameSlotsReady(Transport.GPU_SHARED, 960, 1080, 3,
				"C:\\Users\\me\\.minecraft\\hearthside\\satellite-1\\frames.slots");
		assertThat(roundTrip(message)).isEqualTo(message);
	}

	@Test
	void frameSlotsReadyRejectsNonPositiveSize() {
		assertThatThrownBy(() -> new ControlMessage.FrameSlotsReady(Transport.COPY, 0, 1080, 1, "f"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void useCopyTransportRoundTrips() throws IOException {
		assertThat(roundTrip(new ControlMessage.UseCopyTransport())).isEqualTo(new ControlMessage.UseCopyTransport());
	}

	@Test
	void protocolVersionIsFour() {
		assertThat(ControlCodec.PROTOCOL_VERSION).isEqualTo(4);
	}

	@Test
	void inWorldAndLeftWorldRoundTrip() throws IOException {
		assertThat(roundTrip(new ControlMessage.InWorld())).isEqualTo(new ControlMessage.InWorld());
		assertThat(roundTrip(new ControlMessage.LeftWorld())).isEqualTo(new ControlMessage.LeftWorld());
	}

	@Test
	void joinWorldCarriesTokenAndName() throws IOException {
		var message = new ControlMessage.JoinWorld(51234, "token-abc", "Alex");
		assertThat(roundTrip(message)).isEqualTo(message);
	}

	@Test
	void joinWorldRejectsInvalidName() {
		assertThatThrownBy(() -> new ControlMessage.JoinWorld(51234, "t", "not a name!"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void joinWorldWithBadPortIsRejected() {
		assertThatThrownBy(() -> ControlCodec.read(input(frame(2 + 4, 6, ByteBuffer.allocate(4).putInt(0).array()))))
				.isInstanceOf(ProtocolException.class);
	}

	@Test
	void setControlsCarriesBedrock() throws IOException {
		var bytes = new ByteArrayOutputStream();
		ControlCodec.write(new ControlMessage.SetControls(ControlSchemeJson.toJson(ControlScheme.BEDROCK)), new DataOutputStream(bytes));
		var decoded = (ControlMessage.SetControls) ControlCodec.read(input(bytes.toByteArray()));
		assertThat(ControlSchemeJson.fromJson(decoded.schemeJson())).isEqualTo(ControlScheme.BEDROCK);
	}

	@Test
	void badPadStyleIsRejected() {
		assertThatThrownBy(() -> ControlCodec.read(input(frame(3, 8, (byte) 200)))).isInstanceOf(ProtocolException.class);
	}

	@Test
	void malformedStringIsProtocolError() {
		byte[] payload = ByteBuffer.allocate(12).putInt(1).putInt(1)
				.putShort((short) 2).put((byte) 0xC0).put((byte) 0x00).array();
		assertThatThrownBy(() -> ControlCodec.read(input(frame(2 + payload.length, 1, payload))))
				.isInstanceOf(ProtocolException.class);
	}
}
