# Walking skeleton — end-to-end check

Automated by `E2eDriver` (enabled with `-Dhearthside.e2e=true`). Run it with:

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
