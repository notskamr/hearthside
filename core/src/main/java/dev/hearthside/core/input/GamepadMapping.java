package dev.hearthside.core.input;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Turns controller state into intents using a player's {@link ControlScheme}. Pure and
 * frame-rate independent: rates are per second and {@code dt} is clamped, so a stall never makes the
 * camera spin.
 */
public final class GamepadMapping {
	public static final float YAW_DEG_PER_SEC = 300f;
	public static final float PITCH_DEG_PER_SEC = 200f;
	public static final float CURSOR_PX_PER_SEC = 1200f;
	public static final float SCROLL_PER_SEC = 8f;
	public static final float MAX_DT = 0.05f;

	private GamepadMapping() {}

	public static GameplayIntent gameplay(ControlScheme scheme, InputFrame previous, InputFrame current, float dtSeconds) {
		float dt = clampDt(dtSeconds);
		boolean swap = scheme.swapSticks();
		float[] move = swap ? deadZone(current.rightX(), current.rightY(), scheme.deadZone())
				: deadZone(current.leftX(), current.leftY(), scheme.deadZone());
		float[] look = swap ? deadZone(current.leftX(), current.leftY(), scheme.deadZone())
				: deadZone(current.rightX(), current.rightY(), scheme.deadZone());
		float yaw = curve(look[0]) * YAW_DEG_PER_SEC * scheme.lookSensitivity() * dt;
		float pitch = curve(look[1]) * PITCH_DEG_PER_SEC * scheme.lookSensitivity() * dt;
		if (scheme.invertY()) {
			pitch = -pitch;
		}

		Set<Action> held = EnumSet.noneOf(Action.class);
		Set<Action> pressed = EnumSet.noneOf(Action.class);
		for (Map.Entry<Action, Control> binding : scheme.gameplay().entrySet()) {
			Control control = binding.getValue();
			if (control.isDown(current)) {
				held.add(binding.getKey());
				if (!control.isDown(previous)) {
					pressed.add(binding.getKey());
				}
			}
		}
		int hotbar = (pressed.contains(Action.HOTBAR_NEXT) ? 1 : 0) - (pressed.contains(Action.HOTBAR_PREV) ? 1 : 0);
		return new GameplayIntent(-move[0], -move[1], yaw, pitch, held, pressed, hotbar);
	}

	public static MenuIntent menu(ControlScheme scheme, InputFrame previous, InputFrame current, float dtSeconds) {
		float dt = clampDt(dtSeconds);
		boolean swap = scheme.swapSticks();
		float[] cursor = swap ? deadZone(current.rightX(), current.rightY(), scheme.deadZone())
				: deadZone(current.leftX(), current.leftY(), scheme.deadZone());
		float[] scroll = swap ? deadZone(current.leftX(), current.leftY(), scheme.deadZone())
				: deadZone(current.rightX(), current.rightY(), scheme.deadZone());

		Set<MenuAction> down = EnumSet.noneOf(MenuAction.class);
		Set<MenuAction> up = EnumSet.noneOf(MenuAction.class);
		for (Map.Entry<MenuAction, Control> binding : scheme.menu().entrySet()) {
			Control control = binding.getValue();
			boolean now = control.isDown(current);
			boolean before = control.isDown(previous);
			if (now && !before) {
				down.add(binding.getKey());
			} else if (!now && before) {
				up.add(binding.getKey());
			}
		}
		if (edge(previous, current, PadButton.START)) {
			down.add(MenuAction.CLOSE);
		}
		int hopX = (edge(previous, current, PadButton.DPAD_RIGHT) ? 1 : 0) - (edge(previous, current, PadButton.DPAD_LEFT) ? 1 : 0);
		int hopY = (edge(previous, current, PadButton.DPAD_DOWN) ? 1 : 0) - (edge(previous, current, PadButton.DPAD_UP) ? 1 : 0);
		return new MenuIntent(cursor[0] * CURSOR_PX_PER_SEC * dt, cursor[1] * CURSOR_PX_PER_SEC * dt, down, up, hopX, hopY,
				-scroll[1] * SCROLL_PER_SEC * dt);
	}

	/** Radial dead zone: inside {@code zone} reads as centred, the rest is rescaled to 0–1. */
	public static float[] deadZone(float x, float y, float zone) {
		float magnitude = (float) Math.hypot(x, y);
		if (magnitude < zone || magnitude == 0f) {
			return new float[] {0f, 0f};
		}
		float scaled = Math.min(1f, (magnitude - zone) / (1f - zone));
		return new float[] {x / magnitude * scaled, y / magnitude * scaled};
	}

	private static float curve(float v) {
		return Math.signum(v) * v * v;
	}

	private static float clampDt(float dt) {
		return Float.isFinite(dt) ? Math.clamp(dt, 0f, MAX_DT) : 0f;
	}

	private static boolean edge(InputFrame previous, InputFrame current, PadButton button) {
		return current.pressed(button) && !previous.pressed(button);
	}
}
