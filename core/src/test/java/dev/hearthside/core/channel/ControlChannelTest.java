package dev.hearthside.core.channel;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.protocol.ControlCodec;
import dev.hearthside.core.protocol.ControlMessage;
import dev.hearthside.core.protocol.LifecycleCommand;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ControlChannelTest {
	static final ControlMessage.Hello HELLO = new ControlMessage.Hello(ControlCodec.PROTOCOL_VERSION, 1, "secret");
	static final ControlMessage.HelloAck ACCEPT = new ControlMessage.HelloAck(ControlCodec.PROTOCOL_VERSION, true, "", "tok");

	final List<AutoCloseable> cleanup = new ArrayList<>();

	@AfterEach
	void tearDown() throws Exception {
		for (AutoCloseable c : cleanup) {
			c.close();
		}
	}

	private ControlServer server(java.util.function.Function<ControlMessage.Hello, HelloVerdict> verifier) throws IOException {
		ControlServer s = ControlServer.start(verifier);
		cleanup.add(s);
		return s;
	}

	private Socket rawSocket(int port) throws IOException {
		Socket s = new Socket(InetAddress.getLoopbackAddress(), port);
		cleanup.add(s);
		return s;
	}

	@Test
	void helloIsAcceptedAndMessagesFlowBothWays() throws Exception {
		var serverReceived = new CompletableFuture<ControlMessage>();
		var viewport = new ControlMessage.SetViewport(new Rect(960, 0, 960, 1080), 60);
		var server = server(h -> new HelloVerdict(ACCEPT, conn -> {
			conn.onMessage(serverReceived::complete);
			try {
				conn.send(viewport);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}));

		var ack = new CompletableFuture<ControlMessage.HelloAck>();
		ControlConnection client = ControlClient.connect(server.port(), HELLO, ack::complete);
		cleanup.add(client);
		var clientReceived = new CompletableFuture<ControlMessage>();
		client.onMessage(clientReceived::complete);
		client.startReading();

		assertThat(ack.get(2, SECONDS).loginToken()).isEqualTo("tok");
		assertThat(clientReceived.get(2, SECONDS)).isEqualTo(viewport);
		client.send(new ControlMessage.Lifecycle(LifecycleCommand.PAUSE));
		assertThat(serverReceived.get(2, SECONDS)).isEqualTo(new ControlMessage.Lifecycle(LifecycleCommand.PAUSE));
	}

	@Test
	void rejectedHelloClosesConnection() throws Exception {
		var server = server(h -> new HelloVerdict(
				new ControlMessage.HelloAck(ControlCodec.PROTOCOL_VERSION, false, "bad secret", ""), null));
		var ack = new CompletableFuture<ControlMessage.HelloAck>();
		ControlConnection client = ControlClient.connect(server.port(), HELLO, ack::complete);
		cleanup.add(client);
		assertThat(ack.get(2, SECONDS).accepted()).isFalse();
		assertThat(ack.get().reason()).isEqualTo("bad secret");
		assertThat(client.isOpen()).isFalse();
	}

	@Test
	void nonHelloFirstMessageIsDropped() throws Exception {
		var verifierCalled = new AtomicBoolean();
		var server = server(h -> {
			verifierCalled.set(true);
			return new HelloVerdict(ACCEPT, c -> {});
		});
		Socket raw = rawSocket(server.port());
		raw.setSoTimeout(3000);
		ControlCodec.write(new ControlMessage.Lifecycle(LifecycleCommand.QUIT), new DataOutputStream(raw.getOutputStream()));
		assertThat(raw.getInputStream().read()).isEqualTo(-1);
		assertThat(verifierCalled).isFalse();
	}

	@Test
	void silentPeerIsDroppedAfterHandshakeTimeout() throws Exception {
		var server = server(h -> new HelloVerdict(ACCEPT, c -> {}));
		Socket raw = rawSocket(server.port());
		raw.setSoTimeout((int) ControlServer.HANDSHAKE_TIMEOUT.toMillis() + 2000);
		long start = System.nanoTime();
		assertThat(raw.getInputStream().read()).isEqualTo(-1);
		assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(ControlServer.HANDSHAKE_TIMEOUT.toMillis() + 2000);
	}

	@Test
	void closingServerClosesLiveConnections() throws Exception {
		var accepted = new CountDownLatch(1);
		var server = server(h -> new HelloVerdict(ACCEPT, c -> accepted.countDown()));
		ControlConnection client = ControlClient.connect(server.port(), HELLO, a -> {});
		cleanup.add(client);
		var closed = new CompletableFuture<Void>();
		client.onClose(() -> closed.complete(null));
		client.startReading();
		assertThat(accepted.await(2, SECONDS)).isTrue();

		server.close();
		closed.get(2, SECONDS);
		assertThat(client.isOpen()).isFalse();
	}

	@Test
	void peerDisconnectRunsCloseHandlerOnce() throws Exception {
		var closes = new AtomicInteger();
		var closedOnce = new CountDownLatch(1);
		var server = server(h -> new HelloVerdict(ACCEPT, c -> c.onClose(() -> {
			closes.incrementAndGet();
			closedOnce.countDown();
		})));
		ControlConnection client = ControlClient.connect(server.port(), HELLO, a -> {});
		client.close();
		assertThat(closedOnce.await(2, SECONDS)).isTrue();
		Thread.sleep(200); // negative check: no second close callback
		assertThat(closes).hasValue(1);
	}

	@Test
	void concurrentSendsDoNotInterleave() throws Exception {
		int threads = 4, perThread = 500;
		var received = new AtomicInteger();
		var all = new CompletableFuture<Void>();
		var server = server(h -> new HelloVerdict(ACCEPT, c -> c.onMessage(m -> {
			if (m instanceof ControlMessage.Input && received.incrementAndGet() == threads * perThread) {
				all.complete(null);
			}
		})));
		ControlConnection client = ControlClient.connect(server.port(), HELLO, a -> {});
		cleanup.add(client);

		List<Thread> senders = new ArrayList<>();
		for (int t = 0; t < threads; t++) {
			senders.add(Thread.ofPlatform().start(() -> {
				for (int i = 0; i < perThread; i++) {
					try {
						client.send(new ControlMessage.Input(new InputFrame(i, 1, 0.5f, -0.5f, 0, 0, 0, 1)));
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			}));
		}
		for (Thread s : senders) {
			s.join();
		}
		all.get(10, SECONDS);
		assertThat(received).hasValue(threads * perThread);
	}

	@Test
	void serverBindsLoopbackOnly() throws Exception {
		var server = server(h -> new HelloVerdict(ACCEPT, c -> {}));
		assertThat(server.boundAddress().isLoopbackAddress()).isTrue();
	}
}
