# Hearthside — installing and playing (test build)

Hearthside turns Minecraft Java into couch co-op: up to four players on one PC, each with their own controller and view,
in one window. Every player is a real Minecraft game running side by side, so mods and shaders behave as they do alone.

This is a test build. Please read **Known limitations**, and send the logs (see **When something goes wrong**) for
anything odd.

## What you need

| | |
|---|---|
| System | Windows 10 or 11 (macOS is coming) |
| Minecraft | Java Edition **26.3** |
| Mod loader | Fabric Loader **0.19.5** or newer, with **Fabric API** for 26.3 |
| Controllers | Any mix of Xbox, PlayStation, Switch Pro or generic controllers (wired or Bluetooth) |
| Graphics | Any GPU that runs Minecraft 26.3, on OpenGL or Vulkan |
| Memory | Player 1's game uses about 2 GB, and each extra player about 1.5 GB (measured). With Windows and a browser open: **8 GB** RAM for 2 players, **16 GB** for 3–4 |

Optional: Sodium (tested with 0.9.2) and Iris. Iris works on OpenGL only — it can't start on Minecraft's Vulkan
renderer.

## Install

1. Install Fabric Loader for Minecraft 26.3 (https://fabricmc.net/use/installer/) and pick the Fabric profile in your
   launcher.
2. Put these in your `.minecraft/mods` folder:
   - `hearthside-0.1.0.jar`
   - Fabric API for 26.3
3. Start the game. The title screen now has a **Co-op** button next to Singleplayer.

Hearthside starts the other players' games itself, hidden, when they join — there is nothing else to install.

## Playing

**Gather the party.** Press **Co-op** (with the mouse, or move to it on any controller and press A).
- Player 1 is already seated. To play on a controller instead of keyboard and mouse, press **+ Controller** on your card,
  then press a button on your controller.
- Everyone else: press **A** on your controller to take a seat. Pick your profile, or make one with A, A, A (a suggested
  name, a skin and a colour). **Y** switches profile; **X** opens your controls (Bedrock, Java-like or Southpaw, or set
  any button yourself).
- Press **Start** (Menu) to choose a world, or create one.

**In the world.** Each player sees their own view; the layout makes room as players come and go.
- To join mid-game, press **A twice** on a new controller.
- A player leaves from their pause menu (**Start**, then **Disconnect**).
- When player 1 leaves the world, everyone's game closes.

**Controls on a controller** (Bedrock layout by default): A jump, B sneak, triggers attack and use, bumpers switch items,
X crafting, Y inventory, D-pad down drops one item (hold it to drop the whole stack), Start pauses. In menus a pointer
follows the left stick; A picks, X takes half a stack, Y moves a stack, B closes.

## Settings

`.minecraft/config/hearthside.json` is created the first time you open Co-op. Edit it with Minecraft closed (or before
starting a party); it applies from the next party.

| Setting | Default | What it does |
|---|---|---|
| `guestMemoryMb` | 1536 | Memory for each extra player's game, in MB (768–8192). Lower it on a tight machine; raise it for big modpacks. |
| `guestRenderDistance` | 8 | The most chunks an extra player's game renders (2–32). `0` lets each player use their own setting. Player 1 keeps theirs. |
| `twoPlayerLayout` | `side_by_side` | `side_by_side` or `stacked` (top and bottom). |
| `threePlayerLayout` | `grid_with_panel` | `grid_with_panel` (a party map in the fourth corner), `wide_top` or `tall_left`. |
| `partyPanel` | `true` | Shows the party map in the free corner with three players. |

Graphics: Minecraft's own **Options → Video Settings → Graphics API** picks OpenGL or Vulkan; the other players follow
player 1. Each extra player can change their own video settings (including Sodium's) from their pause menu; they stay
theirs. Fullscreen is player 1's alone.

## Known limitations

- Each player's pause menu is Minecraft's own for now. Changing controls mid-game, adding a player from the pause menu
  and "pause only when everyone has paused" come in the next update.
- If a controller disconnects mid-game, reconnect it to carry on; a proper "Reconnect your controller" screen comes next.
- Skins: Minecraft's built-in skins only. Your own skin file or a player's skin by name comes soon.
- On the lobby's name keyboard, type with the controller; the PC keyboard can't type into it yet.
- Windows only.

## When something goes wrong

Send these files (and roughly when it happened):
- `.minecraft/logs/latest.log` — player 1's game
- `.minecraft/hearthside/satellite-1/logs/latest.log` (and `-2`, `-3`) — the other players' games

To start fresh, close Minecraft and delete `.minecraft/hearthside` (profiles and the extra players' game folders) and
`.minecraft/config/hearthside.json`.

## Uninstall

Remove `hearthside-0.1.0.jar` from `mods`. Optionally delete `.minecraft/hearthside` and
`.minecraft/config/hearthside.json`.
