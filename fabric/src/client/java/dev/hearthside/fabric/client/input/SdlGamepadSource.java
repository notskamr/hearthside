package dev.hearthside.fabric.client.input;

import static org.lwjgl.sdl.SDLGamepad.*;

import dev.hearthside.core.input.GamepadSource;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.PadButton;
import dev.hearthside.core.input.PadSnapshot;
import dev.hearthside.core.input.PadFamilies;
import dev.hearthside.core.input.PadStyle;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLGUID;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLStdinc;
import org.lwjgl.sdl.SDL_GUID;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Reads every connected controller through SDL3, which Minecraft 26.x already ships and initialises
 * (video only). Host only; must run on the render thread. Minecraft's per-frame
 * {@code SDL_PumpEvents} keeps gamepad state current even though it flushes the events themselves.
 */
public final class SdlGamepadSource implements GamepadSource, AutoCloseable {
	private static final int[][] BUTTONS = {
			{SDL_GAMEPAD_BUTTON_SOUTH, PadButton.A.ordinal()},
			{SDL_GAMEPAD_BUTTON_EAST, PadButton.B.ordinal()},
			{SDL_GAMEPAD_BUTTON_WEST, PadButton.X.ordinal()},
			{SDL_GAMEPAD_BUTTON_NORTH, PadButton.Y.ordinal()},
			{SDL_GAMEPAD_BUTTON_LEFT_SHOULDER, PadButton.LEFT_BUMPER.ordinal()},
			{SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER, PadButton.RIGHT_BUMPER.ordinal()},
			{SDL_GAMEPAD_BUTTON_BACK, PadButton.BACK.ordinal()},
			{SDL_GAMEPAD_BUTTON_START, PadButton.START.ordinal()},
			{SDL_GAMEPAD_BUTTON_GUIDE, PadButton.GUIDE.ordinal()},
			{SDL_GAMEPAD_BUTTON_LEFT_STICK, PadButton.LEFT_STICK.ordinal()},
			{SDL_GAMEPAD_BUTTON_RIGHT_STICK, PadButton.RIGHT_STICK.ordinal()},
			{SDL_GAMEPAD_BUTTON_DPAD_UP, PadButton.DPAD_UP.ordinal()},
			{SDL_GAMEPAD_BUTTON_DPAD_DOWN, PadButton.DPAD_DOWN.ordinal()},
			{SDL_GAMEPAD_BUTTON_DPAD_LEFT, PadButton.DPAD_LEFT.ordinal()},
			{SDL_GAMEPAD_BUTTON_DPAD_RIGHT, PadButton.DPAD_RIGHT.ordinal()},
	};

	private record Open(long handle, String guid, String name, PadStyle style) {}

	private final Map<Integer, Open> open = new HashMap<>();
	private long sequence;

	private SdlGamepadSource() {}

	public static SdlGamepadSource open() {
		// Keep reading controllers while another game window (a satellite) has focus.
		SDLHints.SDL_SetHint(SDLHints.SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "1");
		if (!SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_GAMEPAD)) {
			throw new IllegalStateException("Could not start SDL gamepad support: " + SDLError.SDL_GetError());
		}
		return new SdlGamepadSource();
	}

	@Override
	public List<PadSnapshot> poll() {
		Map<Integer, Boolean> seen = new HashMap<>();
		IntBuffer ids = SDL_GetGamepads();
		if (ids != null) {
			try {
				for (int i = 0; i < ids.remaining(); i++) {
					int id = ids.get(i);
					seen.put(id, true);
					if (!open.containsKey(id)) {
						long handle = SDL_OpenGamepad(id);
						if (handle != 0L) {
							String name = SDL_GetGamepadNameForID(id);
							open.put(id, new Open(handle, guidOf(id), name == null ? "Controller" : name, PadFamilies.fromSdl(SDL_GetGamepadTypeForID(id), SDL_GetGamepadVendorForID(id))));
						}
					}
				}
			} finally {
				SDLStdinc.SDL_free(ids);
			}
		}
		open.entrySet().removeIf(entry -> {
			if (seen.containsKey(entry.getKey())) {
				return false;
			}
			SDL_CloseGamepad(entry.getValue().handle());
			return true;
		});

		sequence++;
		List<PadSnapshot> pads = new ArrayList<>(open.size());
		open.forEach((id, pad) -> pads.add(new PadSnapshot(id, pad.guid(), pad.name(), pad.style(), read(pad.handle()))));
		return pads;
	}

	@Override
	public void close() {
		open.values().forEach(pad -> SDL_CloseGamepad(pad.handle()));
		open.clear();
		SDLInit.SDL_QuitSubSystem(SDLInit.SDL_INIT_GAMEPAD);
	}

	private InputFrame read(long handle) {
		int buttons = 0;
		for (int[] mapping : BUTTONS) {
			if (SDL_GetGamepadButton(handle, mapping[0])) {
				buttons |= 1 << mapping[1];
			}
		}
		return new InputFrame(sequence, buttons,
				axis(handle, SDL_GAMEPAD_AXIS_LEFTX), axis(handle, SDL_GAMEPAD_AXIS_LEFTY),
				axis(handle, SDL_GAMEPAD_AXIS_RIGHTX), axis(handle, SDL_GAMEPAD_AXIS_RIGHTY),
				axis(handle, SDL_GAMEPAD_AXIS_LEFT_TRIGGER), axis(handle, SDL_GAMEPAD_AXIS_RIGHT_TRIGGER));
	}

	private static float axis(long handle, int axis) {
		return SDL_GetGamepadAxis(handle, axis) / 32767f;
	}

	private static String guidOf(int id) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			SDL_GUID guid = SDL_GetGamepadGUIDForID(id, SDL_GUID.malloc(stack));
			ByteBuffer text = stack.malloc(33);
			SDLGUID.SDL_GUIDToString(guid, text);
			return MemoryUtil.memASCII(text, MemoryUtil.memLengthNT1(text));
		}
	}

}
