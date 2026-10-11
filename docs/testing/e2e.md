# End-to-end checks

Automated by `E2eDriver` (enabled with `-Dhearthside.e2e=true`; `-Dhearthside.e2e.fakePads=true` adds scripted
controllers). Run it with:

```bash
./gradlew :fabric:runE2eClient
```

The driver creates or opens the world `hearthside-e2e` in `fabric/run-e2e/saves`, runs steps 1–6, and writes
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

## Controllers (scripted) — 2026-10-11, same machine

Run between steps 5 and 6 with a fake PlayStation-style pad; guest movement is measured on the host's server, on a
barrier platform at y = 249 so terrain never blocks it.

| # | Check | Result |
|---|---|---|
| P0 | `Player2` profile rebinds JUMP to Y | PASS |
| P1 | START on a new controller adds `Player2` | PASS (11 s, incl. satellite start) |
| P2 | Left stick forward 2 s moves the guest | PASS (7.73 blocks) |
| P3 | Right stick 0.4 s turns the guest | PASS (112.5°, ≈ 280°/s) |
| P4 | RB advances the guest's hotbar slot | PASS |
| P7 | Rebound Y makes the guest jump | PASS (rose 1.25 blocks) |
| P5 | Unplug pauses the guest; replug (same model, new id) resumes and movement works | PASS |
| P6 | Satellite input-isolation boot line present (the behaviour itself is a manual check) | PASS |
| M1 | Survival inventory: X takes half a 10-stack (server sees 5 on the cursor), A puts it back | PASS — and FAILS with the old GLFW button numbers |
| M2 | Holding B to close the inventory does not leave the guest sneaking | PASS |
| M3 | START opens the guest's pause menu, B closes it | PASS |
| P8 | Satellite received the PlayStation pad style | PASS |
| P9 | `Player2` leaves | PASS |

Found while building this:
- Satellites never see OS input, so Minecraft capped them at its 10 fps "away from keyboard" frame rate. Controller
  input now counts as activity.
- Menu clicks used GLFW mouse-button numbers; 26.3 uses SDL's (left 1, right 3), so A pressed nothing.
- A half-screen window is narrower than vanilla's 379 GUI px, where the recipe book is drawn over the inventory.
  Crafting (X) now only opens the book where it fits.

## Controllers (manual — needs real pads)

Use two physical controllers, ideally two identical ones, and one of a different family if available.

1. Open a world. Press START on a controller: a second window opens and that controller plays it.
2. Press START on a second controller: a third window; each controller moves only its own player.
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
