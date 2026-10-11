package dev.hearthside.core.channel;

import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.core.protocol.ProtocolException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.function.Consumer;

/** The satellite side of the control channel. */
public final class ControlClient {
	private ControlClient() {}

	/**
	 * Connects to the host, sends {@code hello} and waits for the answer. The returned connection
	 * has not started reading: set its handlers, then call {@link ControlConnection#startReading()}.
	 * When the host rejects the Hello, the returned connection is already closed.
	 */
	public static ControlConnection connect(int port, ControlMessage.Hello hello,
			Consumer<ControlMessage.HelloAck> onAck) throws IOException {
		var connection = new ControlConnection(new Socket(InetAddress.getLoopbackAddress(), port));
		try {
			connection.send(hello);
			connection.setReadTimeout((int) ControlServer.HANDSHAKE_TIMEOUT.toMillis());
			if (!(connection.readOne() instanceof ControlMessage.HelloAck ack)) {
				throw new ProtocolException("Expected HelloAck from host");
			}
			connection.setReadTimeout(0);
			onAck.accept(ack);
			if (!ack.accepted()) {
				connection.close();
			}
			return connection;
		} catch (IOException | RuntimeException e) {
			connection.close();
			throw e;
		}
	}
}
