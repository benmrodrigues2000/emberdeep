# Emberdeep CI report

| | |
|---|---|
| commit | `ccf233153563e75e3252103379d3f1ec204bd2e6` |
| branch | `arena/5d7dde7e-emberdeep` |
| generated | 2026-10-06T16:53:55Z |
| unit tests (exit 1) | FAIL |
| apk/aab build (exit 0) | pass |

## Test failures

```
    <failure message="java.lang.AssertionError: invariant broken (seed 3789422594, Mage): ground item on an unusable tile" type="java.lang.AssertionError">java.lang.AssertionError: invariant broken (seed 3789422594, Mage): ground item on an unusable tile
	at org.junit.Assert.fail(Assert.java:89)
	at com.emberdeep.game.testutil.A.isTrue(A.kt:36)
	at com.emberdeep.game.sim.SimulationTest.twelve complete expeditions never break a structural invariant(SimulationTest.kt:28)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)
	at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
	at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:56)
	at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
	at org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
	at org.junit.runners.BlockJUnit4ClassRunner$1.evaluate(BlockJUnit4ClassRunner.java:100)
	at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:366)
--
    <failure message="java.lang.AssertionError: invariant broken (seed 3237998082, Mage): ground item on an unusable tile&#10;&#10;================ EMBERDEEP BALANCE SIMULATION ================&#10;runs: 21   wins: 1   deaths: 19   timeouts: 1&#10;win rate: 4.7%   avg floor reached: 6.9   avg level: 5.5&#10;avg kills (finished runs): 17.6   avg turns: 453.9   avg gold: 212.1&#10;  Fighter  runs 7   wins 0   avg floor 6.0   avg level 4.6&#10;  Rogue    runs 7   wins 0   avg floor 4.7   avg level 3.3&#10;  Mage     runs 7   wins 1   avg floor 9.9   avg level 8.7&#10;deaths by floor: F2=1  F3=1  F4=4  F5=3  F6=2  F7=1  F10=7  &#10;INVARIANT VIOLATION seed=3237998082 class=MAGE: ground item on an unusable tile&#10;TIMEOUT seed=3237998082 Mage: floor=9 hero=(13,41) hp=27/60 level=8 kills=32 stairs=(6,41) pathToStairs=0 adjacentEnemies=1 visibleEnemies=1 awake=1 items=6 potions=12 rooms=11 walkable=594 | The Cave Troll hits you for 13. / You hit the Deep Wraith for 16. / The Deep Wraith is slain! / The Cave Troll hits you for 9.&#10;==============================================================&#10;" type="java.lang.AssertionError">java.lang.AssertionError: invariant broken (seed 3237998082, Mage): ground item on an unusable tile

================ EMBERDEEP BALANCE SIMULATION ================
runs: 21   wins: 1   deaths: 19   timeouts: 1
win rate: 4.7%   avg floor reached: 6.9   avg level: 5.5
avg kills (finished runs): 17.6   avg turns: 453.9   avg gold: 212.1
  Fighter  runs 7   wins 0   avg floor 6.0   avg level 4.6
  Rogue    runs 7   wins 0   avg floor 4.7   avg level 3.3
  Mage     runs 7   wins 1   avg floor 9.9   avg level 8.7
deaths by floor: F2=1  F3=1  F4=4  F5=3  F6=2  F7=1  F10=7  
INVARIANT VIOLATION seed=3237998082 class=MAGE: ground item on an unusable tile
TIMEOUT seed=3237998082 Mage: floor=9 hero=(13,41) hp=27/60 level=8 kills=32 stairs=(6,41) pathToStairs=0 adjacentEnemies=1 visibleEnemies=1 awake=1 items=6 potions=12 rooms=11 walkable=594 | The Cave Troll hits you for 13. / You hit the Deep Wraith for 16. / The Deep Wraith is slain! / The Cave Troll hits you for 9.
==============================================================

	at org.junit.Assert.fail(Assert.java:89)
```

## Compiler errors and failed tasks

```
w: file:///home/runner/work/emberdeep/emberdeep/app/src/main/kotlin/com/emberdeep/game/core/GameApp.kt:36:42 'VIBRATOR_SERVICE: String' is deprecated. Deprecated in Java
> Task :app:testDebugUnitTest FAILED
FAILURE: Build failed with an exception.
w: file:///home/runner/work/emberdeep/emberdeep/app/src/main/kotlin/com/emberdeep/game/core/GameApp.kt:36:42 'VIBRATOR_SERVICE: String' is deprecated. Deprecated in Java
```

## Balance simulation

```
    ================ EMBERDEEP BALANCE SIMULATION ================
    runs: 21   wins: 1   deaths: 19   timeouts: 1
    win rate: 4.7%   avg floor reached: 6.9   avg level: 5.5
    avg kills (finished runs): 17.6   avg turns: 453.9   avg gold: 212.1
      Fighter  runs 7   wins 0   avg floor 6.0   avg level 4.6
      Rogue    runs 7   wins 0   avg floor 4.7   avg level 3.3
      Mage     runs 7   wins 1   avg floor 9.9   avg level 8.7
    deaths by floor: F2=1  F3=1  F4=4  F5=3  F6=2  F7=1  F10=7  
    INVARIANT VIOLATION seed=3237998082 class=MAGE: ground item on an unusable tile
    TIMEOUT seed=3237998082 Mage: floor=9 hero=(13,41) hp=27/60 level=8 kills=32 stairs=(6,41) pathToStairs=0 adjacentEnemies=1 visibleEnemies=1 awake=1 items=6 potions=12 rooms=11 walkable=594 | The Cave Troll hits you for 13. / You hit the Deep Wraith for 16. / The Deep Wraith is slain! / The Cave Troll hits you for 9.
    ==============================================================
        at org.junit.Assert.fail(Assert.java:89)
        at com.emberdeep.game.testutil.A.isTrue(A.kt:36)
        at com.emberdeep.game.sim.SimulationTest.the dungeon can be beaten and every run terminates(SimulationTest.kt:51)

CombatTest > armour class reduces the hit rate PASSED

CombatTest > a better weapon and higher level deal more damage PASSED

CombatTest > monster attacks respect the hero's armour PASSED

CombatTest > natural 20 hits even against an impossible armour class PASSED

CombatTest > player attacks always deal at least one damage when they land PASSED

FovTest > computing outside the map is harmless PASSED

    EMBERDEEP ENDGAME: turns=63 swings=21 hits=13 misses=45 heals=7 hero=20/95 dragon=-7 bossDefeated=true
    EMBERDEEP ENDGAME LOG: You miss the The Ember Dragon. | The flames sear you for 2! | The The Ember Dragon misses you. | The Ember Cultist misses you. | You miss the The Ember Dragon. | The flames sear you for 2! | The The Ember Dragon misses you. | The Ember Cultist shoots you for 4. | You hit the The Ember Dragon for 12. | The The Ember Dragon is slain!
```
