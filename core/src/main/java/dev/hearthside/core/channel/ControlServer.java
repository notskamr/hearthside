package dev.hearthside.core.channel;

import dev.hearthside.core.protocol.ControlMessage;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The host's control-channel listener: loopback only, OS-assigned port. Every connection must
 * send a {@link ControlMessage.Hello} within {@link #HANDSHAKE_TIMEOUT} or it is dropped.
 */
public final class ControlServer implements AutoCloseable {
	public static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(5);

	private static final System.Logger LOG = System.getLogger(ControlServer.class.getName());

	private final ServerSocket serverSocket;
	private final Function<ControlMessage.Hello, HelloVerdict> verifier;
	private final Set<ControlConnection> live = ConcurrentHashMap.newKeySet();

	private ControlServer(ServerSocket serverSocket, Function<ControlMessage.Hello, HelloVerdict> verifier) {
		this.serverSocket = serverSocket;
		this.verifier = verifier;
	}

	public static ControlServer start(Function<ControlMessage.Hello, HelloVerdict> verifier) throws IOException {
		var server = new ControlServer(new ServerSocket(0, 50, InetAddress.getLoopbackAddress()), verifier);
		Thread.ofPlatform().daemon().name("hearthside-control-accept").start(server::acceptLoop);
		return server;
	}

	public int port() {
		return serverSocket.getLocalPort();
	}

	InetAddress boundAddress() {
		return serverSocket.getInetAddress();
	}

	@Override
	public void close() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			LOG.log(Level.DEBUG, "Error closing control server", e);
		}
		for (ControlConnection connection : live) {
			connection.close();
		}
		live.clear();
	}

	private void acceptLoop() {
		while (!serverSocket.isClosed()) {
			try {
				Socket socket = serverSocket.accept();
				Thread.ofVirtual().name("hearthside-control-handshake").start(() -> handshake(socket));
			} catch (IOException e) {
				if (!serverSocket.isClosed()) {
					LOG.log(Level.WARNING, "Control server accept failed", e);
				}
			}
		}
	}

	private void handshake(Socket socket) {
		ControlConnection connection;
		try {
			connection = new ControlConnection(socket);
		} catch (IOException e) {
			closeQuietly(socket);
			return;
		}
		try {
			connection.setReadTimeout((int) HANDSHAKE_TIMEOUT.toMillis());
			if (!(connection.readOne() instanceof ControlMessage.Hello hello)) {
				connection.close();
				return;
			}
			HelloVerdict verdict = verifier.apply(hello);
			connection.send(verdict.ack());
			if (!verdict.ack().accepted()) {
				connection.close();
				return;
			}
			connection.setReadTimeout(0);
			live.add(connection);
			if (serverSocket.isClosed()) {
				connection.close();
				return;
			}
			verdict.onAccepted().accept(connection);
			connection.startReading();
		} catch (IOException | RuntimeException e) {
			LOG.log(Level.DEBUG, "Control handshake failed: " + e);
			connection.close();
		}
	}

	private static void closeQuietly(Socket socket) {
		try {
			socket.close();
		} catch (IOException ignored) {
			// nothing to do
		}
	}
}
