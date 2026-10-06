# Emberdeep CI report

| | |
|---|---|
| commit | `fcdc30d02ece085223e3e8bab3248f2a53504cc4` |
| branch | `arena/5d7dde7e-emberdeep` |
| generated | 2026-10-06T17:01:40Z |
| unit tests (exit 0) | pass |
| apk/aab build (exit 0) | pass |

## Test failures

```
```

## Compiler errors and failed tasks

```
w: file:///home/runner/work/emberdeep/emberdeep/app/src/main/kotlin/com/emberdeep/game/core/GameApp.kt:36:42 'VIBRATOR_SERVICE: String' is deprecated. Deprecated in Java
w: file:///home/runner/work/emberdeep/emberdeep/app/src/main/kotlin/com/emberdeep/game/core/GameApp.kt:36:42 'VIBRATOR_SERVICE: String' is deprecated. Deprecated in Java
```

## Balance simulation

```
    ================ EMBERDEEP BALANCE SIMULATION ================
    runs: 12   wins: 1   deaths: 11   timeouts: 0
    win rate: 8.3%   avg floor reached: 7.8   avg level: 5.8
    avg kills (finished runs): 19.8   avg turns: 511.1   avg gold: 212.7
      Fighter  runs 4   wins 0   avg floor 7.0   avg level 5.5
      Rogue    runs 4   wins 0   avg floor 6.2   avg level 3.8
      Mage     runs 4   wins 1   avg floor 10.0   avg level 8.2
      reached the dragon: 6 runs, wins 1, avg level 8.0, avg gold 318.5
    deaths by floor: F2=1  F3=1  F5=1  F7=2  F9=1  F10=5  
    ==============================================================


SimulationTest > twelve complete expeditions never break a structural invariant PASSED

SimulationTest > the dungeon can be beaten and every run terminates PASSED

CombatTest > armour class reduces the hit rate PASSED

CombatTest > a better weapon and higher level deal more damage PASSED

CombatTest > monster attacks respect the hero's armour PASSED

CombatTest > natural 20 hits even against an impossible armour class PASSED

CombatTest > player attacks always deal at least one damage when they land PASSED

FovTest > computing outside the map is harmless PASSED
    EMBERDEEP ENDGAME: turns=63 swings=21 hits=13 misses=45 heals=7 hero=20/95 dragon=-7 bossDefeated=true
    EMBERDEEP ENDGAME LOG: You miss the The Ember Dragon. | The flames sear you for 2! | The The Ember Dragon misses you. | The Ember Cultist misses you. | You miss the The Ember Dragon. | The flames sear you for 2! | The The Ember Dragon misses you. | The Ember Cultist shoots you for 4. | You hit the The Ember Dragon for 12. | The The Ember Dragon is slain!
```
