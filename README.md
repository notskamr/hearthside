# Hearthside

Couch co-op split-screen for Minecraft: Java Edition. Up to four players on one PC, each with their own controller,
profile and view, in one window. Every player runs a real Minecraft game in the background and player 1's window shows
them all, on OpenGL or Vulkan, so Sodium, Iris and other mods behave as they do when you play alone.

Hearthside is an early alpha for Minecraft 26.3 on Fabric.

- **Play it:** [docs/install.md](docs/install.md) covers requirements, install, starting a party, settings and known
  limitations.
- **What's next:** planned work and known bugs live in [GitHub issues](https://github.com/notskamr/hearthside/issues).
- **Testing:** [docs/testing/e2e.md](docs/testing/e2e.md) describes the desktop end-to-end run and its results.

## Building

`./gradlew build` with Java 25. The mod jar is `fabric/build/libs/hearthside-<version>.jar`.

## License

Hearthside is licensed under the GNU Lesser General Public License v3.0 only ([COPYING.LESSER](COPYING.LESSER), which
builds on the GPL v3.0 in [COPYING](COPYING)).

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
