package dev.hearthside.core.protocol;

import dev.hearthside.core.compositor.Transport;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.PadStyle;
import dev.hearthside.core.layout.Rect;
import dev.hearthside.core.profile.GuestNames;
import java.util.List;
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
	 * refresh rate (the satellite paces its frames to it). {@code guiScale} is the party's GUI scale for this view, 0
	 * when not forced (tiled mode); the satellite renders a {@code frameWidth}×{@code frameHeight} frame, at most the
	 * view's size (smaller under a reduced render resolution), which the host stretches over the view.
	 */
	record SetViewport(Rect rect, int refreshHz, int guiScale, int frameWidth, int frameHeight) implements ControlMessage {
		public SetViewport {
			Objects.requireNonNull(rect, "rect");
			if (refreshHz < 1 || refreshHz > 1000) {
				throw new IllegalArgumentException("Refresh rate out of range: " + refreshHz);
			}
			if (guiScale < 0 || guiScale > 32) {
				throw new IllegalArgumentException("GUI scale out of range: " + guiScale);
			}
			if (frameWidth < 1 || frameWidth > Math.max(1, rect.width()) || frameHeight < 1
					|| frameHeight > Math.max(1, rect.height())) {
				throw new IllegalArgumentException("Frame " + frameWidth + "x" + frameHeight + " doesn't fit the view "
						+ rect.width() + "x" + rect.height());
			}
		}

		/** An unforced GUI scale and a frame the view's own size (at least 1×1). */
		public SetViewport(Rect rect, int refreshHz) {
			this(rect, refreshHz, 0, Math.max(1, Objects.requireNonNull(rect, "rect").width()), Math.max(1, rect.height()));
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

	/**
	 * Type 13, satellite → host: whether this guest's game is paused, i.e. a pausing screen is open in it (Hearthside's
	 * pause menu, vanilla's, or a mod's). Sent when it changes.
	 */
	record PauseState(boolean paused) implements ControlMessage {}

	/**
	 * Type 14, host → satellite: whether the world is frozen (every local player paused) and who is still playing, for
	 * the pause menu's status line. At most {@link #MAX_STILL_PLAYING} names.
	 */
	record PauseStatus(boolean frozen, List<String> stillPlaying) implements ControlMessage {
		/** Never more than the other local players; a garbled count is refused rather than allocated. */
		public static final int MAX_STILL_PLAYING = 8;

		public PauseStatus {
			stillPlaying = List.copyOf(stillPlaying);
			if (stillPlaying.size() > MAX_STILL_PLAYING) {
				throw new IllegalArgumentException("Too many names still playing: " + stillPlaying.size());
			}
		}
	}

	/** A message whose type this version does not know; produced only by decoding, never written. */
	record Unknown(int typeId) implements ControlMessage {}
}
