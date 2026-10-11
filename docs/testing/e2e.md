# End-to-end checks

Automated by `E2eDriver` (enabled with `-Dhearthside.e2e=true`; `-Dhearthside.e2e.fakePads=true` adds scripted
controllers). Run it with:

```bash
./gradlew :fabric:runE2eClient
```

With fake pads the driver first runs the party steps (B7, B6, B4, B5) from the title screen. It then creates or opens
the world `hearthside-e2e` in `fabric/run-e2e/saves`, runs steps 1–6, and writes
`fabric/run-e2e/hearthside-e2e-report.txt`. It then rejoins a guest and prints
`READY_FOR_HOST_KILL host=<pid> satellite=<pid>`. Step 7 is done from outside the game: force-kill the host pid and
confirm the satellite pid exits.

Dev runtime includes Sodium `mc26.3-0.9.2-fabric` and Iris `1.11.7+26.3-fabric`.

## Results — 2026-10-11 (after final-review fixes), Windows 11, Ryzen 5 7600X, RX 9060 XT, 1920×1080

| # | Check | Result |
|---|---|---|
| 1 | `join Guest2` → second window, guest in the world (2 players), host window tiled into the left half (`0,0 960×1080`) | PASS (15 s) |
| 2 | Satellite log (`run-e2e/hearthside/satellite-1/logs/latest.log`) shows Sodium, Iris and `starting as SATELLITE` | PASS |
| 3 | Force-killed satellite is restarted and the guest rejoins | PASS (12 s) |
| 4 | Joining with the host's own name is refused | PASS |
| 4b | Open to LAN, then Stop Sharing → guest stays in the world; guest listener reopened (step 6 rejoins through it) | PASS |
| 5 | `leave Guest2` → 1 player, satellite process gone, host window back to its original bordered `533,300 854×480` | PASS |
| 6 | Closing the world closes satellites | PASS (gone 475 ms after close) |
| 7 | Force-killing the host → satellite exits by itself; no Hearthside java processes left | PASS (0.9 s) |

## Party (scripted) — 2026-10-11, same machine

Plan 5b1: the party exists before a world. Run with a fake PlayStation-style pad; 44/44 steps pass in the full run (after the final-review fixes; every new
step below marked "FAILED before" was watched failing on the code before its fix).

| # | Check | Result |
|---|---|---|
| B7 | The title screen has "Co-op (1 controller)" under Singleplayer and every widget stays on screen; it opens the lobby; Back returns with no party | PASS — FAILED before the button, and before the layout fix (Options/Quit and the copyright line were pushed off a 240-high GUI) |
| B6 | In the lobby, A seats a controller and starts its game; Back asks "End the party?"; End party stops every guest's game | PASS — FAILED before the confirm |
| B4 | A in the lobby starts the guest's game before any world, and it connects to the party | PASS |
| B8 | In the lobby, a seated controller's stick moves the host's cursor, and its own idle game gets no input | PASS — FAILED before (its input drove the guest's hidden title screen) |
| B10 | A title screen while the party waits (as when a world fails to open) goes back to the lobby, seat and game kept | PASS — FAILED before |
| B9 | B on a seated controller gives up the seat (its game stops); the lobby stays | PASS — FAILED before |
| B5 | A seats the controller again; Menu on it opens world select; the world brings the guest in (2 players) | PASS (12 s) |
| B2 | In a world, one A on a new controller does not join | PASS |
| B3 | The "press A again" prompt expires after 5 s | PASS |
| P1 | A, A on a new controller adds `Player2` (see below) | PASS (16 s, incl. satellite start) |

## Look and profiles (scripted) — 2026-10-11, same machine

Plan 5b2: the Hearthside look and profiles on lobby cards. The full run is 55/55 after the final-review fixes (it
includes the party and controller steps). The lobby's E2E pad has its own model GUID so lobby profiles never match the in-world steps' pads.

| # | Check | Result |
|---|---|---|
| H1 | Every Hearthside sprite and every glyph (4 controller families × 17 controls) is in the GUI atlas | PASS — FAILED before the art (76 missing) |
| H2 | A guest's button hints draw glyph sprites of its controller family (PlayStation), none of another | PASS — FAILED before |
| L5 | "+ Controller" on player 1's card, then A on a controller: it drives player 1 and takes no seat; it is remembered | PASS |
| L6 | Next time the lobby opens, that controller is player 1's again by itself; "− Controller" gives it back (free to join, forgotten) | PASS — FAILED before the button |
| L1 | A seated controller's card pre-selects the profile last used on it (Hazel) | PASS — failed first: the A that took the seat also confirmed (fixed) |
| L2 | Y and the D-pad cycle the carousel (wrapping through New); A confirms; the controller is lit in the player's colour | PASS |
| L9 | Y on a ready card reopens it with the current profile; A switches to another; B backs out keeping it | PASS |
| L4 | A on New starts quick setup; B steps back to the carousel; B again gives up the seat | PASS |
| L8 | Unplug mid-keyboard, plug the same model back under a new id: the card and typed name are kept, and it drives the card | PASS |
| L10 | Two identical controllers: both cards open on the same profile; once one takes it the other stops offering it at once; two new profiles set up together get different names and both save | PASS — FAILED before the fix (the second card still offered the taken profile) |
| L3 | A, A, A: the suggested name, a skin, the first free colour; the profile is saved for this controller | PASS (9 s) |
| B5 | Menu on a ready controller opens world select; a second seat still choosing goes in as what its card shows; both guests are in the world under their names (games launched as `Seat1`/`Seat2`). Join hints show the free Xbox pad's buttons; the shared hints show the PlayStation buttons of the controller driving (B8) | PASS (18 s) |

Screenshots of the lobby are saved to `fabric/run-e2e/screenshots/` (`lobby-empty`, `lobby-choosing`, `lobby-changing`,
`lobby-keyboard`, `lobby-three-players`).

## Worlds, title and profile tools (scripted) — 2026-10-11, same machine

Plan 5b3: controllers on the title screen, the Cozy-hearth title, the world picker and world creation, the controls
screen and Manage Profiles. After the final-review fixes the full run is 70/70, plus step 7 (the guest's game exits
1.06 s after the host is force-killed); it includes every step above. Every step marked "FAILED before" was watched
failing on the code before its fix.

| # | Check | Result |
|---|---|---|
| T3 | The title screen draws Hearthside's plank wall, firelight and wooden buttons (no panorama) | PASS — FAILED before (panorama drawn) |
| T4 | With Minecraft in the background, a controller moves nothing on the title screen | PASS — FAILED before (the D-pad moved focus) |
| T1 | A controller moves focus on the title screen (D-pad), and A on Co-op opens the lobby | PASS — FAILED before (focus never reached Co-op) |
| T2 | That controller is player 1's in the lobby, takes no seat, and is remembered | PASS |
| W3 | Menu in the lobby opens the picker focused on its first card; Create opens the new-world screen with a free name and focus on Create; A on the name opens the keyboard (X deletes, Menu keeps); right on Mode picks Creative; Create makes and opens the world; the generic hints stay off screens with their own chips; the name sits on parchment | PASS (13 s) — FAILED before three fixes (the picker closed on the press that opened it; vanilla's initial focus skipped Create) |
| W4 | With more worlds than fit, Down scrolls the picker to Create, and stops there (never onto a hint chip, whose A used to recurse until the game crashed) | PASS — FAILED before (cards out of view couldn't take focus) |
| W5 | A world deleted in More… (Minecraft's list) is gone from the picker on return | PASS — FAILED before (the list was cached) |
| W6 | B on New World goes back one screen, to the picker | PASS — FAILED before (the held B also closed the picker) |
| W7 | B on the name keyboard closes only the keyboard | PASS — FAILED before (it also closed New World and the picker) |
| W8 | With no worlds yet, the picker shows one Create card and one row of hints, Create focused (opened with Menu, as a player does) | PASS — FAILED before (two Create cards, six chips) |
| W1 | The picker puts the last co-op world first and focused, with "Last played with Ember and Birch" | PASS |
| W2 | A on it opens the world; the guests come in by name; the co-op record has them | PASS (15 s) |
| R1 | X on a ready card opens More (nothing in the lobby looks selected; the footer has only Back and Start); Controls is that seat's: A then Y makes Y Jump (Inventory takes A); RB to Southpaw (sticks swapped) and LB back to the edit; Menu saves to the profile, the seat and the guest's game; RB from Custom is Bedrock again | PASS (8 s) |
| M1 | More → Profiles: holding Y on a profile in use is refused with a reason; a Y still held while moving to another card deletes nothing; letting go early keeps a profile; holding 1.5 s deletes it (and nothing else) | PASS (11 s) — the held-over Y FAILED before (it deleted the next card) |

Screenshots: `title`, `title-focus`, `worlds`, `new-world`, `controls`, `manage-profiles`, `lobby-ready`, `lobby-more`.

## Controllers (scripted) — 2026-10-11, same machine

Run between steps 5 and 6 with a fake PlayStation-style pad; guest movement is measured on the host's server, on a
barrier platform at y = 249 so terrain never blocks it.

| # | Check | Result |
|---|---|---|
| P0 | `Player2` profile rebinds JUMP to Y | PASS |
| P1 | A, A on a new controller adds `Player2` (START no longer joins, Plan 5b1) | PASS (16 s, incl. satellite start) |
| P2 | Left stick forward 2 s moves the guest | PASS (7.73 blocks) |
| P3 | Right stick 0.4 s turns the guest | PASS (112.5°, ≈ 280°/s) |
| P4 | RB advances the guest's hotbar slot | PASS |
| P7 | Rebound Y makes the guest jump | PASS (rose 1.25 blocks) |
| P5 | Unplug pauses the guest; replug (same model, new id) resumes and movement works | PASS |
| P6 | Satellite input-isolation boot line present (the behaviour itself is a manual check) | PASS |
| M1 | Survival inventory: X takes half a 10-stack (server sees 5 on the cursor), A puts it back | PASS — and FAILS with the old GLFW button numbers |
| M2 | Holding B to close the inventory does not leave the guest sneaking | PASS |
| M3 | START opens the guest's pause menu, B closes it | PASS |
| A1 | RT punches a pig in front of the guest (survival) | PASS — FAILED before the fix |
| A2 | Holding RT mines dirt, with the guest window unfocused | PASS — FAILED before the fix |
| A3 | LT places a block | PASS |
| P8 | Satellite received the PlayStation pad style | PASS |
| P9 | `Player2` leaves | PASS |

Every controller step runs with the guest window **unfocused**, as in real play: the `e2eClient` run sets
`HEARTHSIDE_E2E_GUEST_UNFOCUSED=1`, which guests inherit and which makes their window report no focus (P1 checks it).
Test windows open with `SDL_WINDOW_ACTIVATE_WHEN_SHOWN=0`, so a run doesn't take your keyboard (the host window can
still flash forward for under a second as it starts).

Found after the first real-controller test (2026-10-11):
- RT never attacked: attack and use were only held down, but vanilla starts an attack from a key *click*. They now
  also click on the press edge, like a mouse button.
- Holding RT never mined in an unfocused window: vanilla only continues an attack while the mouse is grabbed, which
  needs focus. A controller driving the client now counts as grabbed for that one check (no OS cursor capture).
- The menu cursor kept its first position, which could belong to the guest's untiled 854×480 window, so the D-pad
  hop landed on an empty crafting slot. The cursor is now centred in the current window whenever a menu opens.
- A guest window could take focus when it opened. Guests now open with `SDL_WINDOW_ACTIVATE_WHEN_SHOWN=0` and a
  non-focusable window. Windows' foreground record confirmed no guest window took the foreground across the runs.

Found while building this:
- Satellites never see OS input, so Minecraft capped them at its 10 fps "away from keyboard" frame rate. Controller
  input now counts as activity.
- Menu clicks used GLFW mouse-button numbers; 26.3 uses SDL's (left 1, right 3), so A pressed nothing.
- A half-screen window is narrower than vanilla's 379 GUI px, where the recipe book is drawn over the inventory.
  Crafting (X) now only opens the book where it fits.

## Compositor (one window) — 2026-10-11, same machine

Every player is drawn into player 1's window; guests render hidden. In E2E runs each guest paints an 8×8 magenta
corner into the frames it exports (`HEARTHSIDE_E2E_PATTERN=1`) and the host logs the colour it composited just inside
that corner of the guest's view once a second; the steps wait for `ff00ff`.

| # | Check | Result |
|---|---|---|
| C1 | The guest's window is created hidden | PASS |
| C2 | The guest's frames reach the host's window (zero-copy GPU sharing) | PASS |
| C3 | Split-screen no longer moves or resizes the host window | PASS |
| C4 | Resizing the host window re-lays out the guest (guest re-renders at half the new width) | PASS |
| C5 | The copy fallback transport works | PASS — FAILED first (see below) |
| C6 | Maximise and restore keep the guest composited | PASS |
| C7 | A third player joins and leaves: both guests composited, no GL errors | PASS |
| C8 | After a guest crash, the restarted guest's frames come back | PASS |
| C9 | The host's OS mouse works inside its own view; clicks over a guest's view do nothing | PASS |
| C10 | When the last guest leaves, the host renders at full window size again | PASS |
| C11 | In the world, player 1's clicks count wherever the hidden cursor drifted; releases always land | PASS — FAILED before the review fix |
| C12 | A failed GPU import falls back to copying frames (failure simulated) | PASS |

All 35 steps (walking skeleton, controllers, menus, attack, compositor) pass in one run; force-killing the host still
ends the satellite (0.9 s). C6 and C8 require frames from a new import generation, so a stale frame can't pass them.
Host CPU time to composite one guest: p50 0.044 ms, p95 0.129 ms (929 frames; the GPU work is asynchronous).

Found while building this:
- Minecraft creates a GL 3.3 context; the compositor's 4.x calls (direct state access, copy image, clear texture) come
  from extensions, which this AMD driver exposes. The host checks them and falls back to tiled windows otherwise.
- The copy transport showed an empty texture: Minecraft leaves its last upload's `GL_UNPACK_ROW_LENGTH` set. Pixel
  transfers now set the pixel-store state first (through `GlStateManager`, so its cache stays right).
- `SDL_WINDOW_NOT_FOCUSABLE` alone does not stop a window activating when first shown; guests also start hidden.

## Seamless drop-in (Plan 5c1) — 2026-10-11, same machine

A joining guest's view never shows their game's loading screens: Hearthside's own GUI pass draws over every view (a
card while they join, the join prompt, name tags, borders, the party panel), views glide to each new layout, and a
leaving view fades out before the others glide into its space. Window captures (`windowShot`) save the whole
composited window, so they show every view and the overlay; the small magenta squares are the E2E's probe corners.

| # | Check | Result |
|---|---|---|
| O1 | Hearthside's overlay draws over every view, at the full window's GUI scale | PASS |
| D3 | One A on a new controller: the join prompt slides in with that controller's own button (PlayStation ✕) | PASS |
| D2 | A, A mid-game: the newcomer picks their profile on a card inside their own new view while everyone plays on | PASS — FAILED before (the card opened before the view existed) |
| D1 | A joining guest's view shows their card ("Joining…", their skin turning), never their game's loading screens, and crossfades into the world when their game says it is in | PASS |
| D4 | With three players the free quarter shows the party panel: a live map with an arrow per player, the day, the weather and who is playing, all inside its quarter | PASS |
| C7, C7b | A third, then a fourth player joins; the views glide to each layout; no GL errors | PASS |
| D5 | Guest4 leaves: their view fades out where it is (alphas 0.15 → 0.82), then the others glide; settled 579 ms after the leave (550 ± 100); the party panel fades in (0.12 → 0.90) | PASS — FAILED before (the layout snapped) |
| D6 | Guest3's own game leaves the world (as its pause menu's Disconnect does): their view fades, their game closes and the seat is free; the panel fades out (0.88 → 0.11) | PASS — FAILED before (the view froze) |
| C7c | Back to two players, no GL errors throughout | PASS |
| G1 | No bare divider colour inside a settled view while players join and leave (views fading on purpose are skipped) | PASS — FAILED before the overlay (47 gaps) |
| 3, C8 | A crashed guest's card says "Reconnecting…" over their last frame, dimmed, never their restarting game's screens; their frames come back | PASS |
| D7 | A guest whose game gives up: their card says "Couldn't start", then their view fades out and the seat is free | PASS — FAILED before (the seat stayed) |
| D8 | A guest whose game dies while it is still joining: their card says "Couldn't start" on the plank wall (never dimmed over their loading screens), then fades | PASS — FAILED before (from the final review: dimmed over a loading-screen frame) |
| S1 | Since the lobby cards: page turns, key taps (and the lower delete tap), ticks, knocks, a crackle and a snuff, and never vanilla's click on Hearthside's screens; six moves in 90 ms tick twice (at most three) | PASS — FAILED before |
| S2 | In the world: the join prompt slides in and out, a drop-in crackles, a leave snuffs | PASS — FAILED before |
| D9 | The last session forces tiled windows, as on a machine that can't composite: A, A joins straight away as the controller's profile, with no card (nothing would draw it) | PASS — FAILED before (from the final review: the newcomer got a card that nothing drew) |
| 7 | Force-killing the host (in the tiled session): the guest's game exits by itself | PASS (858 ms) |

All 85 steps pass in one run with fake pads, plus step 7. Screenshots are in `docs/screenshots/5c1/`: `joining`,
`drop-in-picker`, `drop-in-joining`, `toast` (player 1 alone), `toast-split`, `two-players`, `three-players`,
`four-players`, `leaving` (mid-fade), `quit-fading`, `reconnecting`, `couldnt-start` and `couldnt-start-joining`.

Found while building this:
- A guest's game could still be drawing Minecraft's loading overlay (the Mojang screen) for a moment after it joined:
  it now says it is in the world only once nothing covers its view.
- Hearthside's overlay first drew at player 1's GUI scale, so cards were tiny in a quarter; it uses the full window's.
- A fading view briefly showed the guest's "disconnecting" screen: a satellite stops sending frames once its world is
  gone, and the host fades out the last picture from the world.
- A restarting guest's title and loading screens showed through the dimmed "Reconnecting…" card: the host now holds
  the last frame until the restarted game is in the world.
- The view borders drew over the join prompt in split play (every relayout re-registered the views' layer above the
  prompt's): overlay layers now have an order, and each covers everything before it.
- Plain game screenshots show only player 1's view; the E2E captures the composited window instead.

## Controllers (manual — needs real pads)

Use two physical controllers, ideally two identical ones, and one of a different family if available.

1. Title screen → Co-op (it shows how many controllers are connected). Press A on a controller: it takes a seat and
   its player's game starts. Continue (or Menu on that controller), pick a world: that player is in it with you.
2. In the world, press A twice on a second controller: a third player joins; each controller moves only its own
   player.
3. In the world: does the Bedrock layout feel right? (A jump, B sneak, Y inventory, X inventory with recipe book,
   RT/LT attack/use, LB/RB hotbar, D-pad down drop, D-pad up camera, left-stick click sprint.)
4. Open an inventory (Y): the cursor snaps to slots and the D-pad hops between them. **Confirm the Bedrock roles:
   A take/place, X take half, Y quick move, B close.** Tell me if any of these are wrong.
5. Button hints show in the bottom-right in the world and along the bottom in inventories, with the right glyphs for
   each controller (✕ ○ □ △ on PlayStation).
6. Unplug one controller: only that player pauses. Plug it back: that player resumes — also with two identical pads.
7. Click on a guest window with the mouse: nothing happens in that game and the cursor is not captured.
8. `/hearthside pad`, then press A on a free controller: it now drives your (host) player.
9. In `.minecraft/hearthside/profiles.json`, set `"invertY": true` for a guest; their next join uses it.

### Problems found and fixed

- Final review: a second Hello on one login could skip the guest token; the host's name and non-guests could log in
  over the loopback listener; Stop Sharing kicked guests and closed their listener; split-screen changes to the host
  window and pause setting applied even without guests and were never undone; closing a guest window counted as a
  crash. All fixed (see commit history). In the real game, step 4b covers Stop Sharing and step 5 covers the window
  restore; the login rules and the clean-exit rule are covered by `core` unit tests.

- The satellite inherited the host's working directory, so it wrote into the host's `logs/latest.log` and had no log
  of its own. Satellites now start in their own folder (`LaunchCommand.workingDirectory`, tests
  `satelliteRunsInItsOwnFolder`, `runsInTheGivenWorkingDirectory`).
- A fresh game folder opens the accessibility onboarding screen first, not the title screen; the driver waits for any
  settled screen.

### Not covered here

- The satellite's own window position is applied by the satellite and is not read back; the host window position is.
- Production install (official launcher, real `mods/` folder) — see below.

## Production check (manual, after merge)

1. Install Fabric Loader 0.19.5 for Minecraft 26.3 with the official installer.
2. Put Fabric API `0.162.0+26.3`, Sodium, Iris and `fabric/build/libs/hearthside-0.1.0.jar` in `.minecraft/mods`.
3. Start the Fabric profile from the official launcher, open a world, run `/hearthside join Guest2`.
4. Expect checks 1, 2 and 6 above to hold; the satellite folder is `.minecraft/hearthside/satellite-1`.
