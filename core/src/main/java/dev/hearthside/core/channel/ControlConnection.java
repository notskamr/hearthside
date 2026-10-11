package dev.hearthside.core.channel;

import dev.hearthside.core.protocol.ControlCodec;
import dev.hearthside.core.protocol.ControlMessage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * One framed control-channel connection. Sends are serialised so concurrent senders never
 * interleave frames; reading happens on a single daemon thread once {@link #startReading()} is
 * called. The close handler runs exactly once, whichever side closes.
 */
public final class ControlConnection implements AutoCloseable {
	private static final System.Logger LOG = System.getLogger(ControlConnection.class.getName());

	private final Socket socket;
	private final DataInputStream in;
	private final DataOutputStream out;
	private final AtomicBoolean closed = new AtomicBoolean();
	private volatile Consumer<ControlMessage> messageHandler = m -> {};
	private volatile Runnable closeHandler = () -> {};

	ControlConnection(Socket socket) throws IOException {
		this.socket = socket;
		this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
		this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
	}

	public synchronized void send(ControlMessage message) throws IOException {
		ControlCodec.write(message, out);
	}

	public void onMessage(Consumer<ControlMessage> handler) {
		this.messageHandler = handler;
	}

	public void onClose(Runnable handler) {
		this.closeHandler = handler;
	}

	public void startReading() {
		Thread.ofPlatform().daemon().name("hearthside-control-reader").start(this::readLoop);
	}

	public boolean isOpen() {
		return !closed.get();
	}

	@Override
	public void close() {
		if (closed.compareAndSet(false, true)) {
			try {
				socket.close();
			} catch (IOException e) {
				LOG.log(Level.DEBUG, "Error closing control socket", e);
			}
			closeHandler.run();
		}
	}

	/** Reads one message synchronously; used for the handshake before the reader thread starts. */
	ControlMessage readOne() throws IOException {
		return ControlCodec.read(in);
	}

	void setReadTimeout(int millis) throws IOException {
		socket.setSoTimeout(millis);
	}

	private void readLoop() {
		try {
			while (!closed.get()) {
				ControlMessage message = ControlCodec.read(in);
				if (message instanceof ControlMessage.Unknown) {
					continue;
				}
				try {
					messageHandler.accept(message);
				} catch (RuntimeException e) {
					LOG.log(Level.WARNING, "Control message handler failed for " + message, e);
				}
			}
		} catch (IOException e) {
			if (!closed.get()) {
				LOG.log(Level.DEBUG, "Control connection ended: " + e);
			}
		} finally {
			close();
		}
	}
}
