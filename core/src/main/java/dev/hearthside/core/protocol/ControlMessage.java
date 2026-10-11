package dev.hearthside.core.protocol;

import dev.hearthside.core.compositor.Transport;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.profile.GuestNames;
import java.util.Objects;

/** Messages on the host↔satellite control channel. Type ids are listed on each record. */
public sealed interface ControlMessage {
	/** Type 1, satellite → host: first message after connecting. */
	record Hello(int protocolVersion, int playerIndex, String launchSecret) implements ControlMessage {
		public Hello {
			Objects.requireNonNull(launchSecret, "launchSecret");
		}
	}

	/** Type 2, host → satellite. {@code reason} and {@code loginToken} are empty when not applicable. */
	record HelloAck(int protocolVersion, boolean accepted, String reason, String loginToken) implements ControlMessage {
		public HelloAck {
			Objects.requireNonNull(reason, "reason");
			Objects.requireNonNull(loginToken, "loginToken");
		}
	}

	/** Type 3, host → satellite: the latest controller state for this satellite's player. */
	record Input(InputFrame frame) implements ControlMessage {
		public Input {
			Objects.requireNonNull(frame, "frame");
		}
	}

	/**
	 * Type 4, host → satellite: this satellite's view, in the host window's framebuffer pixels, and the host display's
	 * refresh rate (the satellite paces its frames to it).
	 */
	record SetViewport(Rect rect, int refreshHz) implements ControlMessage {
		public SetViewport {
			Objects.requireNonNull(rect, "rect");
			if (refreshHz < 1 || refreshHz > 1000) {
				throw new IllegalArgumentException("Refresh rate out of range: " + refreshHz);
			}
		}
	}

	/** Type 5, host → satellite. */
	record Lifecycle(LifecycleCommand command) implements ControlMessage {
		public Lifecycle {
			Objects.requireNonNull(command, "command");
		}
	}

	/**
	 * Type 6, host → satellite: join the host's world at {@code 127.0.0.1:<port>} as {@code playerName}, proving it
	 * with {@code loginToken}. Satellites start idle; this is what sends them in.
	 */
	record JoinWorld(int port, String loginToken, String playerName) implements ControlMessage {
		public JoinWorld {
			if (port < 1 || port > 65535) {
				throw new IllegalArgumentException("Port out of range: " + port);
			}
			Objects.requireNonNull(loginToken, "loginToken");
			GuestNames.requireValid(playerName);
		}
	}

	/** Type 7, host → satellite: this player's controller scheme, as {@code ControlSchemeJson}. */
	record SetControls(String schemeJson) implements ControlMessage {
		public SetControls {
			Objects.requireNonNull(schemeJson, "schemeJson");
		}
	}

	/** Type 8, host → satellite: which button labels this player's controller uses. */
	record SetPadStyle(PadStyle style) implements ControlMessage {
		public SetPadStyle {
			Objects.requireNonNull(style, "style");
		}
	}

	/**
	 * Type 9, satellite → host: this satellite's frame slots are ready at {@code width}×{@code height}. The slot table is
	 * the file {@code controlFile}; GPU slot memory is named from the session, the player and {@code generation}.
	 */
	record FrameSlotsReady(Transport transport, int width, int height, int generation, String controlFile)
			implements ControlMessage {
		public FrameSlotsReady {
			Objects.requireNonNull(transport, "transport");
			Objects.requireNonNull(controlFile, "controlFile");
			if (width < 1 || height < 1) {
				throw new IllegalArgumentException("Frame size must be positive: " + width + "x" + height);
			}
		}
	}

	/**
	 * Type 10, host → satellite: the host could not use this satellite's shared GPU frames; reallocate them with the
	 * copy transport and keep using it.
	 */
	record UseCopyTransport() implements ControlMessage {}

	/** Type 11, satellite → host: its game is in the world (the first frame with no loading screen after JoinWorld). */
	record InWorld() implements ControlMessage {}

	/** Type 12, satellite → host: its game left the world on its own (quit from its pause menu, or disconnected). */
	record LeftWorld() implements ControlMessage {}

	/** A message whose type this version does not know; produced only by decoding, never written. */
	record Unknown(int typeId) implements ControlMessage {}
}
