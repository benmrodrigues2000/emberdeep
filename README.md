# EMBERDEEP

A Dungeons & Dragons-style roguelike dungeon crawler for Android.

Descend 10 procedurally generated floors of the Emberdeep, fight monsters with
classic **d20 combat** (attack rolls vs. AC, damage dice, critical hits), loot
weapons, armor, potions and scrolls, level up — and slay **the Ember Dragon**
waiting at the bottom.

![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-green)
![Language](https://img.shields.io/badge/language-Kotlin-purple)
![Dependencies](https://img.shields.io/badge/runtime%20dependencies-zero-orange)

## Features

- **3 classes** — Fighter (Whirlwind), Rogue (Shadowstep), Mage (Firebolt),
  unlockable with gold banked across runs
- **Turn-based tactical combat** — true d20 rules: `d20 + ATK vs AC`,
  natural 20 crits, natural 1 misses
- **9 monster types** — rats, goblins, skeletons, archers, orcs, burning
  cultists, wall-phasing wraiths, regenerating trolls… and a boss dragon with
  a fire-breath pattern
- **Procedural dungeons** — rooms, corridors, doors, ember vents, a guaranteed
  hand-crafted boss arena on floor 10
- **Loot & progression** — 5 weapon tiers, 4 armor tiers, potions, scrolls,
  XP levels, meta-progression treasury and class unlocks
- **Roguelike systems** — field of view (recursive shadowcasting), fog of war,
  A* tap-to-move with auto-walk, message log, minimap
- **Fully procedural presentation** — every sprite is drawn in code and every
  sound effect + the ambient music loop is synthesized at startup; the game
  ships **zero** image/audio assets
- **Mobile-first UX** — large touch targets, haptic feedback, contextual
  buttons, portrait one-handed play, pause/resume/background-safe
- **Error-safe saves** — atomic writes, autosave every 10 turns and on
  lifecycle events, corrupt-save recovery

## Tech

| | |
|---|---|
| Language | Kotlin (JVM 17) |
| Engine | Custom `SurfaceView` canvas engine (no external frameworks) |
| Min / Target SDK | 24 (Android 7.0) / 34 (Android 14) |
| Runtime dependencies | Kotlin stdlib only |
| Release build | R8 minified + resource-shrunk, signed |

The custom engine was chosen over LibGDX/Godot deliberately: a turn-based 2D
tile game needs no heavy framework, and this keeps the APK tiny (~1.5 MB),
the build trivially stable, and performance excellent on mid-range phones
(fixed-pool particles, cached procedural bitmaps, zero allocations in the
render loop, clamped delta-time so logic is never frame-rate dependent).

## Building

Prerequisites: JDK 17 and the Android SDK (or just Android Studio).

```bash
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

Continuous integration (`.github/workflows/android.yml`) builds all three
outputs on every push and commits them to [`dist/`](dist/), so you can grab a
ready-to-install `Emberdeep-debug.apk` straight from the repo.

### Signing

A demo release keystore is committed at `keystore/emberdeep-release.jks`
(passwords in `app/build.gradle.kts`, overridable via the
`EMBERDEEP_STORE_PASSWORD` / `EMBERDEEP_KEY_ALIAS` / `EMBERDEEP_KEY_PASSWORD`
environment variables) so CI can produce installable signed builds.
**Before publishing to Google Play, replace it with your own private keystore.**

## Project structure

```
app/src/main/kotlin/com/emberdeep/game/
├── MainActivity.kt          # lifecycle, immersive mode, back handling
├── core/                    # engine: game loop, screens, UI kit,
│   │                        #   procedural sprites, synthesized audio,
│   │                        #   particles, RNG, palette
├── model/                   # classes, items, enemies, dungeon map,
│   │                        #   game state (+ JSON serialization)
├── gen/                     # procedural dungeon generator + loot tables
├── systems/                 # turn engine, d20 combat, FOV, A* pathfinding
├── data/                    # profile + save manager (atomic, corruption-safe)
└── ui/                      # menu, class select, game screen/HUD,
                             #   inventory, pause, settings, help,
                             #   game over, victory
```

## How to play

1. Tap a tile to walk (auto-walks when safe, single careful steps in combat).
2. Tap an adjacent enemy to attack; tap yourself to wait.
3. Walk over loot to pick it up; manage it in your pack.
4. Use your class ability (left bottom button) at the right moment.
5. Find the stairs, descend, survive to floor 10, slay the dragon.

Death is permanent — but half your gold is banked to unlock the Rogue
(150 g) and the Mage (400 g).
