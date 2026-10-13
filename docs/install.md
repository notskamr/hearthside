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
- To join mid-game, press **A twice** on a new controller, or let player 1 choose **Add player** from their pause menu:
  then one **A** on any free controller joins within 15 seconds (B on player 1's controller or Esc cancels it). A player
  joining while everyone in the world is paused waits on their card until someone resumes.
- **Start** (Esc for player 1 on the keyboard) opens your own pause menu, in your own view: **Resume**, **Options…**,
  **Game menu…** (Minecraft's own pause menu, with any other mod's buttons) and **Leave game**. Player 1's also has
  **Add player** and **Split-screen…**. Move with the D-pad or the stick, A picks, B resumes. Playing alone, the pause
  key opens Minecraft's own pause menu.
- The world keeps going while anyone is still playing (your menu says who), and freezes when everyone in it has paused.
- If a controller disconnects, that player's view dims behind "Reconnect your controller" and their game pauses; plug it
  back in to carry on. After a minute, one **A** on another controller takes that player over, right where they stand.
- **Leave game** takes a player out of the world and closes their game. Player 1's asks first, because everyone's game
  closes with theirs.
- Player 1's PC keyboard can type names into the on-screen keyboards (on a lobby card, for a new world, or for a
  newcomer in the world while player 1 plays on a controller).

**Controls on a controller** (Bedrock layout by default): A jump, B sneak, triggers attack and use, bumpers switch items,
X crafting, Y inventory, D-pad down drops one item (hold it to drop the whole stack), Start pauses. In menus a pointer
follows the left stick; A picks, X takes half a stack, Y moves a stack, B closes.

## Settings

Player 1's pause menu has **Split-screen…**, where the settings below change in-game: the layout and look apply at once
(the views glide to the new layout), and the preset, render distance and memory apply to the next player who joins.
With Mod Menu installed, its **Configure** button for Hearthside opens the same screen.

They are saved in `.minecraft/config/hearthside.json`, created the first time you open Co-op. You can also edit it with
Minecraft closed; it applies from the next party.

| Setting | Default | What it does |
|---|---|---|
| `preset` | `auto` | How hard the extra players' games work: `quality`, `balanced`, `performance`, or `auto`, which picks one for this PC each time you open Co-op (it's in the log: `Hearthside preset auto → …`). See **Performance presets** below. |
| `presetForPlayer1` | `false` | `true` gives player 1's own game the preset's render distance, lighter graphics and frame-rate cap too. |
| `guestMemoryMb` | 1536 | Memory for each extra player's game, in MB (768–8192). Lower it on a tight machine; raise it for big modpacks. |
| `guestRenderDistance` | `"preset"` | The most chunks an extra player's game renders (2–32). `"preset"` uses the preset's; `0` lets each player use their own setting. Player 1 keeps theirs, unless `presetForPlayer1` is on. |
| `vulkanOffer` | `pending` | Your answer to the "runs smoother on Vulkan" card (`accepted` or `declined`). Set it back to `pending` to see the card again. |
| `twoPlayerLayout` | `side_by_side` | `side_by_side` or `stacked` (top and bottom). |
| `threePlayerLayout` | `grid_with_panel` | `grid_with_panel` (a party map in the fourth corner), `wide_top` or `tall_left`. |
| `partyPanel` | `true` | Shows the party map in the free corner with three players. |
| `borders` | `thin` | The line around each view, in its player's colour: `thin`, `colour` (twice as thick) or `off`. |
| `nameTags` | `true` | Each player's name in the corner of their view for a few seconds after the layout changes. |
| `layoutAnimation` | `true` | `false` moves the views straight to their new places instead of gliding. |
| `titleStyle` | `hearthside` | The title screen's look: `hearthside` (the plank wall and firelight) or `vanilla`. |

**Swap views** on the Split-screen screen moves a player earlier or later in the layout, so player 1 needn't be
top-left. It lasts until everyone else has left.

**Performance presets.** Each extra player's game is a whole Minecraft, so a laptop works hard with three or four.
The preset applies for the session only; nobody's own settings are changed.

| | Quality | Balanced | Performance |
|---|---|---|---|
| Extra players' render distance | their own | at most 8 | at most 6 |
| Extra players' graphics | their own | their own | lighter: fast leaves, no clouds, fewer particles, nearer entities, no biome blend |
| Extra players' frame rate | your screen's | your screen's | at most 45 |
| World simulation distance | your own | your own | at most 6 |
| Extra players' resolution | full | full | slightly lower on big views, with text the same size as yours |

`auto` picks **Performance** on integrated graphics (most laptops), on graphics it can't identify, or under 12 GB of
memory; **Balanced** on a graphics card with 3–4 players (it counts the connected controllers) or a window bigger than
1440p; and **Quality** otherwise. The lower resolution only applies to views big enough to keep text sharp: on a 1080p
TV with three or four players, Performance saves through render distance, lighter graphics and the frame-rate cap.

**Graphics.** Minecraft's own **Options → Video Settings → Graphics API** picks OpenGL or Vulkan; the other players
follow player 1. On some PCs (most Intel laptops) OpenGL can't hand each player's picture straight to player 1's
window, so it is copied every frame; their Vulkan can. On such a PC, the first time you open Co-op, a card says "Split-screen
runs smoother on Vulkan on this PC": **Switch and restart** switches and restarts Minecraft for you; **Not now** is
remembered. It isn't offered with Iris installed, because Iris can't start on Vulkan.

Every view shows the same GUI size: player 1's GUI scale (Options → Video Settings) sets everyone's, or **Auto** picks
the size that fits the smallest view. Each extra player can change their own video settings (including Sodium's) from
their pause menu; they stay theirs. Fullscreen is player 1's alone.

## Known limitations

- Changing your controls or profile mid-game, and controller sensitivity and stick calibration, come in the next update.
- Skins: Minecraft's built-in skins only. Your own skin file or a player's skin by name comes soon.
- Each player's own Minecraft settings (brightness, FOV, volumes…) belong to the seat they sit in, not their profile;
  per-profile settings come with the next update.
- Only player 1 can open Split-screen (they're the party's settings).
- A few things look for Minecraft's own pause screen by name (for example the friends key); they work from
  **Game menu…**.
- **Switch and restart** starts Minecraft again the way your launcher started it. If it doesn't come back (some
  launchers can't be restarted from inside the game), start Minecraft yourself: it's already set to Vulkan.
- Windows only.

## When something goes wrong

Send these files (and roughly when it happened):
- `.minecraft/logs/latest.log` — player 1's game
- `.minecraft/hearthside/satellite-1/logs/latest.log` (and `-2`, `-3`) — the other players' games
- `.minecraft/logs/hearthside-relaunch.log` — if **Switch and restart** didn't bring Minecraft back

To start fresh, close Minecraft and delete `.minecraft/hearthside` (profiles and the extra players' game folders) and
`.minecraft/config/hearthside.json`.

## Uninstall

Remove `hearthside-0.1.0.jar` from `mods`. Optionally delete `.minecraft/hearthside` and
`.minecraft/config/hearthside.json`.
