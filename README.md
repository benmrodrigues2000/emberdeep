# EMBERDEEP

A Dungeons & Dragons-style roguelike dungeon crawler for Android.

Descend 10 procedurally generated floors of the Emberdeep, fight monsters with
classic **d20 combat** (attack rolls vs. AC, damage dice, critical hits), loot
weapons, armor, potions and scrolls, level up — and slay **the Ember Dragon**
waiting at the bottom.

![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-green)
![Language](https://img.shields.io/badge/language-Kotlin-purple)
![Dependencies](https://img.shields.io/badge/runtime%20dependencies-zero-orange)
![Tests](https://img.shields.io/badge/tests-10%20suites%20%2B%20balance%20simulation-blue)

## Features

- **3 classes** — Fighter (Whirlwind), Rogue (Shadowstep), Mage (Firebolt),
  unlockable with gold banked across runs
- **Turn-based tactical combat** — true d20 rules: `d20 + ATK vs AC`,
  natural 20 crits, natural 1 misses
- **9 monster types** — rats, goblins, skeletons, archers, orcs, burning
  cultists, wall-phasing wraiths, regenerating trolls… and a boss dragon with
  a fire-breath pattern
- **Procedural dungeons** — rooms, corridors, doors, ember vents, and a
  guaranteed hand-crafted boss arena on floor 10; connectivity between every
  room, monster and treasure pile is *proven* by flood fill at generation time
- **Loot & progression** — 5 weapon tiers, 4 armor tiers, potions, scrolls,
  XP levels, meta-progression treasury and class unlocks
- **Roguelike systems** — field of view (recursive shadowcasting), fog of war,
  A* tap-to-move with auto-walk, message log, minimap
- **Fully procedural presentation** — every sprite is drawn in code and every
  sound effect + the ambient music loop is synthesized at startup; the game
  ships **zero** image/audio assets
- **Mobile-first UX** — large touch targets, haptic feedback, contextual
  buttons, portrait one-handed play, pause/resume/background-safe
- **Error-safe saves** — atomic writes with a previous-good backup, corrupt-save
  recovery, autosave every 10 turns, and autosaves written on a background
  worker so saving never drops a frame

## Tech

| | |
|---|---|
| Language | Kotlin (JVM 17) |
| Engine | Custom `SurfaceView` canvas engine (no external frameworks) |
| Min / Target SDK | 24 (Android 7.0) / 34 (Android 14) |
| Runtime dependencies | Kotlin stdlib only |
| Release build | R8 minified + resource-shrunk, signed |
| Version | 1.1.0 (versionCode 2) |

The custom engine was chosen over LibGDX/Godot deliberately: a turn-based 2D
tile game needs no heavy framework, and this keeps the build tiny (~1.0 MB debug, ~200 KB release),
the build trivially stable, and performance excellent on mid-range phones.
The full rationale and every system specification live in
[`docs/DESIGN.md`](docs/DESIGN.md).

## Building

Prerequisites: JDK 17 and the Android SDK (or just Android Studio).

```bash
# Unit tests, invariant fuzzing and the balance simulation
./gradlew :app:testDebugUnitTest

# Debug APK
./gradlew :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# Release APK (signed)
./gradlew :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk

# Release AAB for Play Store
./gradlew :app:bundleRelease
# -> app/build/outputs/bundle/release/app-release.aab
```

Or open the project in Android Studio and press Run.

Continuous integration (`.github/workflows/android.yml`) runs the test suite
and the balance simulation, builds all three outputs on every push, and commits
them to [`dist/`](dist/) so you can grab a ready-to-install
`Emberdeep-debug.apk` straight from the repo. Because runner log storage is not
reachable from every environment, each run also publishes its own report —
test results, compiler errors and the balance numbers — to
[`ci/last-test-run.md`](ci/last-test-run.md).

### Signing

A demo release keystore is committed at `keystore/emberdeep-release.jks`
(passwords in `app/build.gradle.kts`, overridable via the
`EMBERDEEP_STORE_PASSWORD` / `EMBERDEEP_KEY_ALIAS` / `EMBERDEEP_KEY_PASSWORD`
environment variables) so CI can produce installable signed builds.
**Before publishing to Google Play, replace it with your own private keystore.**

## Verification

The gameplay layer (`model`, `gen`, `systems`, `data`) contains no Android
imports, so the entire game can be played headlessly in unit tests:

- **10 JVM test suites** cover the codec, RNG determinism, d20 combat maths,
  field of view, pathfinding, dungeon connectivity across 6 seeds × 10 floors,
  progression, the turn engine (movement, abilities, potions, pack limits,
  stairs, death, scrolls) and the save system (including corrupt-save
  recovery).
- **`SimulationTest`** drives the real `TurnEngine` through complete
  expeditions with a scripted bot and checks structural invariants after
  *every turn* — hero on walkable ground, HP bounds, no two monsters sharing a
  tile, no dead monster still listed, usable stairs, finite positions.
- **Balance report** — win rate, floor-by-floor death histogram, levels, kills
  and turn counts, printed by the suite and published by CI. It also proves the
  dungeon is beatable and that a levelled, geared hero can kill the dragon.

Any failing test prints the exact seed, so every reported problem replays
deterministically.

## Project structure

```
app/src/main/kotlin/com/emberdeep/game/
├── MainActivity.kt          # lifecycle, immersive mode, back handling
├── core/                    # engine: game loop, screens, UI kit,
│   │                        #   procedural sprites, synthesized audio,
│   │                        #   particles, RNG, palette, base64 codec
├── model/                   # classes, items, enemies, dungeon map,
│   │                        #   game state (+ JSON serialization)
├── gen/                     # procedural dungeon generator + loot tables
├── systems/                 # turn engine, d20 combat, FOV, A* pathfinding,
│                            #   run setup
├── data/                    # profile + save manager (atomic, backup, worker)
└── ui/                      # menu, class select, game screen/HUD,
                             #   inventory, pause, settings, help,
                             #   game over, victory

app/src/test/kotlin/com/emberdeep/game/
├── core/  systems/  model/  gen/  data/   # 10 unit test suites
└── sim/                     # headless player bot + balance simulation

docs/DESIGN.md               # full internal development specification
ci/last-test-run.md          # latest CI results and balance report
```

## How to play

1. Tap a tile to walk (auto-walks when safe, single careful steps in combat).
2. Tap an adjacent enemy to attack; tap yourself to wait.
3. Walk over loot to pick it up; manage it in your pack.
4. Use your class ability (left bottom button) at the right moment.
5. Find the stairs, descend, survive to floor 10, slay the dragon.

Death is permanent — but half your gold is banked to unlock the Rogue
(150 g) and the Mage (400 g).
