# How Controlify behaves (reference notes)

Studied 2026-10-11 from Controlify `main` at c0ea63c (2026-10-08, targets MC 26.2/26.3). Controlify is LGPL: these
notes describe its **behaviour** in our own words so Hearthside can match it. No code is copied. Paths are relative
to `src/main/java/dev/isxander/controlify/` unless they start with `resources/`.

## Gameplay actions

- **Attack, use, sprint and chat** are emulated key mappings (`bindings/output/KeyMappingEmulationOutput`). A press
  sets the key down **and** adds one click; a release sets it up. Holding is left to vanilla: holding attack keeps
  mining, and held use repeats through vanilla's `rightClickDelay`. It only applies with no screen open.
- **Called directly** (`ingame/InGameInputHandler`): pick block (invoker), drop (with hold-repeat), inventory, swap
  hands, hotbar (with repeat), perspective, hide HUD, pause.
- **Movement** comes from its own `ClientInput`, combined with the keyboard (`ingame/DualInput`). The analog vector is
  capped at length 1. **Jump only counts if pressed this tick**, so leaving a menu with the button held never jumps.
- Bindings update at the start of the client tick (before vanilla's `handleKeybinds`). Look updates every frame,
  before `handleAccumulatedMovement`, through `player.turn`.

## Focus and the grabbed mouse

- Vanilla only keeps mining while `mouseHandler.isMouseGrabbed()`, and only grabs when the window is focused.
  Controlify bypasses the focus check in `MouseHandler.grabMouse` when its "out of focus input" setting is on
  (default off), and also stops pausing on lost focus.
- It always sets `SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS=1`.
- **Hearthside differs:** a guest window is normally unfocused, and the host can be played by controller while a
  guest window has focus. We make only `continueAttack`'s grabbed check pass while a controller is driving, so we
  never capture the OS cursor from an unfocused window.

## Thresholds

- Button activation threshold 0.5, also used for the analog triggers. No hysteresis.
- Stick deadzone 0.12 per axis, rescaled. The look curve is applied to the vector length; the virtual mouse uses a
  cubic curve.

## Controller identification

- SDL hints on: `JOYSTICK_GAMEINPUT`, `HIDAPI`, `ENHANCED_REPORTS`, `HIDAPI_STEAM`. Loads its own
  `gamecontrollerdb-sdl3.txt`.
- **No duplicate-device filtering** (Steam Input or XInput+HID). It relies on SDL; a pad's identity is a hash of
  VID/PID and serial, falling back to the GUID.
- Controller type comes from its own VID/PID table (xbox_one, dualshock3/4, dualsense, switch, steam_deck, …), not
  `SDL_GetGamepadType`.
- Button names are generic ("South Face", "Start"); only the **glyphs** change per controller type.

## Rumble and haptics

- `rumble/RumbleManager` mixes active effects by taking the strongest each tick, with a strength per source (player,
  world, interaction, GUI). It is silent when paused, unfocused or using keyboard and mouse.

| Event | Rumble |
|---|---|
| Block break | Continuous, about 0.02 + up to 0.25, scaled by hardness |
| Taking damage | 0.8/0.5 for 5 ticks |
| Dealing damage | Short pulse |
| Explosion | Scaled by distance, then decays |
| Lightning | Burst |
| Fishing bite | Continuous while biting |
| Item break | 10 ticks |
| Anvil | Short pulse |
| Using an item | Per animation: bow, crossbow, eat/drink, horn, spear, … |

- DualSense adaptive triggers are data-driven (`resources/.../contextual/trigger_effect/in_game.json`, e.g. bow
  tension).
- DualSense HD haptics stream audio to the pad's 4-channel audio device, used only for menu navigation ticks.

## Button guide (HUD prompts)

- A HUD layer with two columns at the top corners (or the bottom, as an option), inside a safe area. Each line is a
  glyph and a label on a translucent background. Container screens add their own guide.
- **Context-sensitive, data-driven** (`resources/.../contextual/guide/in_game.json`, `container.json`, plus `facts/`):
  - Attack shows Attack (entity), Mine (block with a tool) or Break.
  - Use has about 51 rules: place, eat, shoot, open, trade, sleep, …
  - Jump shows swim up, fly up or climb.
  - Containers show take, place all/one, swap, drop outside, quick move.
- Glyphs are **bitmap fonts per controller type** (`resources/.../font/controller/<type>.json`), with inputs mapped to
  low code points (U+0001 to U+001E) and rendered as styled text components.

## Virtual mouse and container screens

- The cursor eases toward its target and is fed in through `MouseHandler.onMove`.
- It snaps to the nearest slot when the stick is released (slot centre, range 17). D-pad snapping moves in a direction
  and penalises sideways movement ×4.
- In container screens, actions call the screen's `slotClicked` directly instead of faking mouse clicks:
  - select = PICKUP button 0
  - quick move = QUICK_MOVE
  - take half = PICKUP button 1
  - drop = PICKUP on slot −999
  - bundles get special handling
- Elsewhere, clicks go through `MouseHandler.onButton`.

## Robustness worth copying

- Key emulation does nothing while a screen is open.
- After a screen closes, a held button only acts again after it is released and pressed.
- A menu "press" fires on release, and is cancelled if the screen changed while the button was held.
- Every binding is limited to the contexts where it applies.
- Switching input mode clears the controller state and resets toggle keys.
- A pad that keeps switching the input mode more than 100 times in a row is disabled, with a toast.
- When a screen opens it moves the OS cursor off-screen so nothing shows as hovered.
- Only one "current" controller drives each game; another pad takes over only when the current one is idle.
