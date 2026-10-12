# Hearthside

Couch co-op split-screen for Minecraft Java Edition: up to four players on one PC, each with their own controller,
profile and view, in one window. Every player runs a real Minecraft game side by side (hidden), and player 1's window
composites them — on OpenGL or Vulkan — so Sodium, Iris and other mods behave as they do alone.

- **Play it:** [docs/install.md](docs/install.md) — requirements, install, how to start a party, settings and known
  limitations.
- **Status and plans:** [docs/backlog.md](docs/backlog.md).
- **How it's built:** the specs in [docs/superpowers/specs/](docs/superpowers/specs/) (the original design, the
  compositor, the co-op experience and "one window everywhere").
- **Testing:** [docs/testing/e2e.md](docs/testing/e2e.md) — the desktop end-to-end run and its results.

Minecraft Java 26.3, Fabric. Build with `./gradlew build` (Java 25); the mod jar is `fabric/build/libs/hearthside-<version>.jar`.
