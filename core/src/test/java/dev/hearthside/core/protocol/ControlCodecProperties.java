package dev.hearthside.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.layout.Rect;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ControlCodecProperties {
	@Property
	void roundTrips(@ForAll("messages") ControlMessage m) throws IOException {
		var bytes = new ByteArrayOutputStream();
		ControlCodec.write(m, new DataOutputStream(bytes));
		var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
		assertThat(ControlCodec.read(in)).isEqualTo(m);
		assertThat(in.available()).isZero();
	}

	@Provide
	Arbitrary<ControlMessage> messages() {
		Arbitrary<String> text = Arbitraries.strings().all().ofMaxLength(200);
		Arbitrary<Integer> ints = Arbitraries.integers();
		Arbitrary<Float> stick = Arbitraries.floats().between(-1f, 1f);
		Arbitrary<Float> trigger = Arbitraries.floats().between(0f, 1f);
		Arbitrary<Integer> size = Arbitraries.integers().greaterOrEqual(0);

		Arbitrary<ControlMessage> hello = Combinators.combine(ints, ints, text).as(ControlMessage.Hello::new);
		Arbitrary<ControlMessage> ack = Combinators.combine(ints, Arbitraries.of(true, false), text, text)
				.as(ControlMessage.HelloAck::new);
		Arbitrary<ControlMessage> input = Combinators.combine(
						Arbitraries.longs(), ints, stick, stick, stick, stick,
						Combinators.combine(trigger, trigger).as((l, r) -> new float[] {l, r}))
				.as((seq, buttons, lx, ly, rx, ry, t) ->
						new ControlMessage.Input(new InputFrame(seq, buttons, lx, ly, rx, ry, t[0], t[1])));
		Arbitrary<ControlMessage> viewport = Combinators.combine(ints, ints, size, size)
				.as((x, y, w, h) -> new ControlMessage.SetViewport(new Rect(x, y, w, h)));
		Arbitrary<ControlMessage> lifecycle = Arbitraries.of(LifecycleCommand.class).map(ControlMessage.Lifecycle::new);

		return Arbitraries.oneOf(hello, ack, input, viewport, lifecycle);
	}
}
