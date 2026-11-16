# Abyssal Breaker

A cave-miner × Arkanoid roguelite with a retro PS2 look. Your boring rig hangs over a lightless shaft and bounces the Wrecking Core, the only light down there, into the rock below. By **Kragnoth**.

This is the standalone Java port of the browser prototype ([SuperMineOut](https://github.com/kragnoth/SuperMineOut)), built on **LWJGL 3.3.3 + OpenGL 3.3 core + OpenAL**.

## Build and run

You need **JDK 21**. The Gradle wrapper downloads everything else.

```fish
# CachyOS / Arch
sudo pacman -S --needed jdk21-openjdk

./gradlew build                                              # compiles, runs the logic-sim tests, builds the fat jar
java -jar desktop/build/libs/abyssal-breaker-0.1.0-all.jar   # play
./gradlew :desktop:run                                       # or run straight from Gradle
```

The fat jar bundles LWJGL natives for Linux, Windows and macOS, so the same jar runs on all three. On macOS, add `-XstartOnFirstThread`.

### Launch options

| Option | Effect |
|---|---|
| `--fullscreen` / `--windowed 1920x1344` | window mode (F11 toggles in game) |
| `--demo` | the bot plays, menus included |
| `--boss 0..3` | jump straight to a wyrm in its biome |
| `--screen shop\|junction\|foundry\|over\|victory` | open a menu directly |
| `--shot FILE --shot-after SECONDS` | save a PNG of the window, then exit |

Saves go to `~/.local/share/abyssal-breaker/save.json` on Linux (or `$XDG_DATA_HOME`), `%APPDATA%\AbyssalBreaker` on Windows, and `~/Library/Application Support/AbyssalBreaker` on macOS.

## Controls

| Input | Action |
|---|---|
| Mouse / A D / ← → | move the rig |
| Mouse height / W S | lower the rig on its spring |
| Click / Space | launch the core (or release held cores) |
| Right-click / E | release the clamps and drop |
| C / J / M / P | CRT+dither / vertex jitter / mute / pause |
| B / G | junction: bank / push on |
| 1-4, 5, R, Enter | workshop: buy card, repair, reroll, continue |
| K / P / 1-3 | tarot: keep / pass / replace |
| F | Foundry (title and game-over screens) |

## What's in it

- **Spring rig** on a coil under a bridge trolley. Flick it down into a core for a **slam** (+35% speed, +2 damage).
- **Core upgrades** from pods, up to 8 per core, inherited by every split: Fission, Strength, Pierce, Blast, Arc, Prospector. **Evolutions** when two reach 2+: Storm, Drill Lance, Midas, Nova, Swarm.
- **Instant pods**: Mega Drill, Wide Rig, Magnet Surge, Catch, Hull Patch.
- **Wyrm bosses** at 80, 160 and 260 m, and **The Eldritch Heart** at 360 m, which wins the run. Then Surface, or go Endless.
- **Junctions** every 60 m: bank cargo, or push on for a multiplier.
- **Surface Workshop** (per run): Vampire Survivors-style offer cards with reroll and banish, wall panels (laser, skimmer), Grapple Bumper, Drill Bay and more.
- **The Foundry** (permanent): scrip upgrades with VS-style escalating prices, full refund, and achievement unlocks.
- **Tarot**: one Major Arcana card per lost rig, sometimes reversed. Keep up to 3 (each is active every run) or pass it for scrip.
- **Industrial audio**, synthesised at start-up: struck pipes, anvils, hydraulics and a machinery drone.

## Project layout

```
core/      pure-Java simulation, no rendering or audio dependencies (only Gson)
  Defs         every tuning constant and data table
  Game         world, physics, pods, evolutions, bosses, economy, Foundry, tarot, bot player
  Entities     plain state the renderer reads
  Fx           event sink for particles, sounds and messages
  SaveStore    JSON save, written atomically
  test/LogicSimTest   bot playthroughs that run during ./gradlew build
desktop/   LWJGL front-end
  render/      Renderer (GL 3.3 core), Geo, Textures (procedural), Particles
  audio/       Synth (offline synthesis), Audio (OpenAL)
  ui/          UiBatch (pixel-font atlas, 2D batch), Screens (HUD and menus)
  Main         window, input, game loop
```

## Logic-sim tests

`./gradlew build` runs `LogicSimTest`. A bot with human-like reaction time and aim error plays 30 chained runs, spending scrip in the Foundry and resolving tarot cards between runs. The build fails on any exception, soft-lock (60 s without progress), or broken invariant (upgrade cap, core cap). The test also checks that the world-generation noise matches the browser build bit for bit. Run `./gradlew :core:test` to see the per-run log.

## Notes for a Minecraft port

- The LWJGL version (3.3.3) and JOML match Minecraft 1.21.1, and Gson is already on Minecraft's classpath.
- `core` has no rendering dependencies, so it can be dropped into a mod as-is. Implement `Fx` for particles and sounds, and `SaveStore` for wherever the save should live.
- The shaft is drawn as one cached mesh per row, rebuilt only when that row changes, much like Minecraft's chunk sections.
- The shaders are GLSL 330. Minecraft 1.21.1 runs a 3.2 core context, so they'd need `#version 150` and the minor syntax changes that come with it.
