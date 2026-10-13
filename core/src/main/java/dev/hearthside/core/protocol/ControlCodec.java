package dev.hearthside.core.protocol;

import dev.hearthside.core.compositor.Transport;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.layout.Rect;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.util.ArrayList;
import java.util.List;

/**
 * Frames control messages as {@code int length, unsigned short typeId, payload}, where
 * {@code length} counts the type id and payload. Frames are bounded by {@link #MAX_FRAME_BYTES},
 * checked before anything is allocated. Unknown types are skipped so newer hosts and satellites can
 * add messages.
 */
public final class ControlCodec {
	public static final int PROTOCOL_VERSION = 6;
	public static final int MAX_FRAME_BYTES = 65_536;

	private static final int HELLO = 1;
	private static final int HELLO_ACK = 2;
	private static final int INPUT = 3;
	private static final int SET_VIEWPORT = 4;
	private static final int LIFECYCLE = 5;
	private static final int JOIN_WORLD = 6;
	private static final int SET_CONTROLS = 7;
	private static final int SET_PAD_STYLE = 8;
	private static final int FRAME_SLOTS_READY = 9;
	private static final int USE_COPY_TRANSPORT = 10;
	private static final int IN_WORLD = 11;
	private static final int LEFT_WORLD = 12;
	private static final int PAUSE_STATE = 13;
	private static final int PAUSE_STATUS = 14;

	private ControlCodec() {}

	public static void write(ControlMessage message, DataOutputStream out) throws IOException {
		var payload = new ByteArrayOutputStream();
		var p = new DataOutputStream(payload);
		int type = switch (message) {
			case ControlMessage.Hello m -> {
				p.writeInt(m.protocolVersion());
				p.writeInt(m.playerIndex());
				p.writeUTF(m.launchSecret());
				yield HELLO;
			}
			case ControlMessage.HelloAck m -> {
				p.writeInt(m.protocolVersion());
				p.writeBoolean(m.accepted());
				p.writeUTF(m.reason());
				p.writeUTF(m.loginToken());
				yield HELLO_ACK;
			}
			case ControlMessage.Input m -> {
				InputFrame f = m.frame();
				p.writeLong(f.sequence());
				p.writeInt(f.buttons());
				p.writeFloat(f.leftX());
				p.writeFloat(f.leftY());
				p.writeFloat(f.rightX());
				p.writeFloat(f.rightY());
				p.writeFloat(f.leftTrigger());
				p.writeFloat(f.rightTrigger());
				yield INPUT;
			}
			case ControlMessage.SetViewport m -> {
				Rect r = m.rect();
				p.writeInt(r.x());
				p.writeInt(r.y());
				p.writeInt(r.width());
				p.writeInt(r.height());
				p.writeShort(m.refreshHz());
				p.writeInt(m.guiScale());
				p.writeInt(m.frameWidth());
				p.writeInt(m.frameHeight());
				yield SET_VIEWPORT;
			}
			case ControlMessage.FrameSlotsReady m -> {
				p.writeByte(m.transport().ordinal());
				p.writeInt(m.width());
				p.writeInt(m.height());
				p.writeInt(m.generation());
				p.writeUTF(m.controlFile());
				yield FRAME_SLOTS_READY;
			}
			case ControlMessage.UseCopyTransport m -> USE_COPY_TRANSPORT;
			case ControlMessage.InWorld m -> IN_WORLD;
			case ControlMessage.LeftWorld m -> LEFT_WORLD;
			case ControlMessage.PauseState m -> {
				p.writeBoolean(m.paused());
				yield PAUSE_STATE;
			}
			case ControlMessage.PauseStatus m -> {
				p.writeBoolean(m.frozen());
				p.writeShort(m.stillPlaying().size());
				for (String name : m.stillPlaying()) {
					p.writeUTF(name);
				}
				yield PAUSE_STATUS;
			}
			case ControlMessage.Lifecycle m -> {
				p.writeByte(m.command().ordinal());
				yield LIFECYCLE;
			}
			case ControlMessage.JoinWorld m -> {
				p.writeInt(m.port());
				p.writeUTF(m.loginToken());
				p.writeUTF(m.playerName());
				yield JOIN_WORLD;
			}
			case ControlMessage.SetControls m -> {
				p.writeUTF(m.schemeJson());
				yield SET_CONTROLS;
			}
			case ControlMessage.SetPadStyle m -> {
				p.writeByte(m.style().ordinal());
				yield SET_PAD_STYLE;
			}
			case ControlMessage.Unknown m ->
					throw new IllegalArgumentException("Cannot write an Unknown message (type " + m.typeId() + ")");
		};
		int length = 2 + payload.size();
		if (length > MAX_FRAME_BYTES) {
			throw new ProtocolException("Message too large: " + length + " bytes");
		}
		out.writeInt(length);
		out.writeShort(type);
		payload.writeTo(out);
		out.flush();
	}

	public static ControlMessage read(DataInputStream in) throws IOException {
		int length = in.readInt();
		if (length < 2 || length > MAX_FRAME_BYTES) {
			throw new ProtocolException("Bad frame length " + length);
		}
		int type = in.readUnsignedShort();
		byte[] payload = new byte[length - 2];
		in.readFully(payload);

		var p = new DataInputStream(new ByteArrayInputStream(payload));
		ControlMessage message;
		try {
			message = switch (type) {
				case HELLO -> new ControlMessage.Hello(p.readInt(), p.readInt(), p.readUTF());
				case HELLO_ACK -> new ControlMessage.HelloAck(p.readInt(), p.readBoolean(), p.readUTF(), p.readUTF());
				case INPUT -> new ControlMessage.Input(new InputFrame(p.readLong(), p.readInt(),
						p.readFloat(), p.readFloat(), p.readFloat(), p.readFloat(), p.readFloat(), p.readFloat()));
				case SET_VIEWPORT -> new ControlMessage.SetViewport(new Rect(p.readInt(), p.readInt(), p.readInt(), p.readInt()),
						p.readUnsignedShort(), p.readInt(), p.readInt(), p.readInt());
				case USE_COPY_TRANSPORT -> new ControlMessage.UseCopyTransport();
				case IN_WORLD -> new ControlMessage.InWorld();
				case LEFT_WORLD -> new ControlMessage.LeftWorld();
				case PAUSE_STATE -> new ControlMessage.PauseState(p.readBoolean());
				case PAUSE_STATUS -> pauseStatus(p);
				case FRAME_SLOTS_READY -> new ControlMessage.FrameSlotsReady(transport(p.readUnsignedByte()), p.readInt(),
						p.readInt(), p.readInt(), p.readUTF());
				case LIFECYCLE -> new ControlMessage.Lifecycle(lifecycle(p.readUnsignedByte()));
				case JOIN_WORLD -> new ControlMessage.JoinWorld(p.readInt(), p.readUTF(), p.readUTF());
				case SET_CONTROLS -> new ControlMessage.SetControls(p.readUTF());
				case SET_PAD_STYLE -> new ControlMessage.SetPadStyle(padStyle(p.readUnsignedByte()));
				default -> {
					p.skipNBytes(payload.length);
					yield new ControlMessage.Unknown(type);
				}
			};
		} catch (EOFException e) {
			throw new ProtocolException("Payload too short for message type " + type);
		} catch (UTFDataFormatException e) {
			throw new ProtocolException("Malformed string in message type " + type);
		} catch (IllegalArgumentException e) {
			throw new ProtocolException("Invalid payload for message type " + type + ": " + e.getMessage());
		}
		if (p.available() > 0) {
			throw new ProtocolException(p.available() + " unexpected trailing bytes in message type " + type);
		}
		return message;
	}

	/** A boolean, an unsigned short count, then that many names; a count over the cap is refused before any is read. */
	private static ControlMessage.PauseStatus pauseStatus(DataInputStream p) throws IOException {
		boolean frozen = p.readBoolean();
		int count = p.readUnsignedShort();
		if (count > ControlMessage.PauseStatus.MAX_STILL_PLAYING) {
			throw new ProtocolException("Too many names still playing: " + count);
		}
		List<String> names = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			names.add(p.readUTF());
		}
		return new ControlMessage.PauseStatus(frozen, names);
	}

	private static PadStyle padStyle(int ordinal) throws ProtocolException {
		PadStyle[] values = PadStyle.values();
		if (ordinal >= values.length) {
			throw new ProtocolException("Unknown pad style " + ordinal);
		}
		return values[ordinal];
	}

	private static Transport transport(int ordinal) throws ProtocolException {
		Transport[] values = Transport.values();
		if (ordinal >= values.length) {
			throw new ProtocolException("Unknown frame transport " + ordinal);
		}
		return values[ordinal];
	}

	private static LifecycleCommand lifecycle(int ordinal) throws ProtocolException {
		LifecycleCommand[] values = LifecycleCommand.values();
		if (ordinal >= values.length) {
			throw new ProtocolException("Unknown lifecycle command " + ordinal);
		}
		return values[ordinal];
	}
}
