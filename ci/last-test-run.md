# Emberdeep CI report

| | |
|---|---|
| commit | `335de54aea73df305f75550ee62bdbd485f9076c` |
| branch | `arena/5d7dde7e-emberdeep` |
| generated | 2026-10-06T16:42:37Z |
| unit tests (exit 1) | FAIL |
| apk/aab build (exit 0) | pass |

## Test failures

```
    <failure message="java.lang.AssertionError: seed 3237998083: run hit the turn cap&#10;&#10;================ EMBERDEEP BALANCE SIMULATION ================&#10;runs: 21   wins: 0   deaths: 12   timeouts: 9&#10;win rate: 0.0%   avg floor reached: 4.4   avg level: 3.8&#10;avg kills (finished runs): 13.5   avg turns: 837.0   avg gold: 90.0&#10;  Fighter  runs 7   wins 0   avg floor 4.4   avg level 4.0&#10;  Rogue    runs 7   wins 0   avg floor 3.4   avg level 2.3&#10;  Mage     runs 7   wins 0   avg floor 5.4   avg level 5.0&#10;deaths by floor: F2=2  F4=3  F5=1  F6=2  F7=1  F9=2  F10=1  &#10;==============================================================&#10;" type="java.lang.AssertionError">java.lang.AssertionError: seed 3237998083: run hit the turn cap

================ EMBERDEEP BALANCE SIMULATION ================
runs: 21   wins: 0   deaths: 12   timeouts: 9
win rate: 0.0%   avg floor reached: 4.4   avg level: 3.8
avg kills (finished runs): 13.5   avg turns: 837.0   avg gold: 90.0
  Fighter  runs 7   wins 0   avg floor 4.4   avg level 4.0
  Rogue    runs 7   wins 0   avg floor 3.4   avg level 2.3
  Mage     runs 7   wins 0   avg floor 5.4   avg level 5.0
deaths by floor: F2=2  F4=3  F5=1  F6=2  F7=1  F9=2  F10=1  
==============================================================

	at org.junit.Assert.fail(Assert.java:89)
	at com.emberdeep.game.testutil.A.isTrue(A.kt:36)
	at com.emberdeep.game.sim.SimulationTest.the dungeon can be beaten and every run terminates(SimulationTest.kt:55)
--
    <failure message="java.lang.AssertionError: expected a walkable floor (found 44)" type="java.lang.AssertionError">java.lang.AssertionError: expected a walkable floor (found 44)
	at org.junit.Assert.fail(Assert.java:89)
	at com.emberdeep.game.testutil.A.isTrue(A.kt:36)
	at com.emberdeep.game.systems.PathfinderTest.long searches on the biggest floors still find a route(PathfinderTest.kt:148)
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
    runs: 12   wins: 0   deaths: 7   timeouts: 5
    win rate: 0.0%   avg floor reached: 5.1   avg level: 4.2
    avg kills (finished runs): 14.9   avg turns: 815.2   avg gold: 103.3
      Fighter  runs 4   wins 0   avg floor 4.8   avg level 3.8
      Rogue    runs 4   wins 0   avg floor 5.0   avg level 3.5
      Mage     runs 4   wins 0   avg floor 5.5   avg level 5.2
    deaths by floor: F3=1  F4=2  F7=2  F8=1  F10=1  
    ==============================================================


SimulationTest > twelve complete expeditions never break a structural invariant PASSED

SimulationTest > the dungeon can be beaten and every run terminates FAILED
    java.lang.AssertionError: seed 3237998083: run hit the turn cap

    ================ EMBERDEEP BALANCE SIMULATION ================
    runs: 21   wins: 0   deaths: 12   timeouts: 9
    win rate: 0.0%   avg floor reached: 4.4   avg level: 3.8
    avg kills (finished runs): 13.5   avg turns: 837.0   avg gold: 90.0
      Fighter  runs 7   wins 0   avg floor 4.4   avg level 4.0
      Rogue    runs 7   wins 0   avg floor 3.4   avg level 2.3
      Mage     runs 7   wins 0   avg floor 5.4   avg level 5.0
    deaths by floor: F2=2  F4=3  F5=1  F6=2  F7=1  F9=2  F10=1  
    ==============================================================
        at org.junit.Assert.fail(Assert.java:89)
        at com.emberdeep.game.testutil.A.isTrue(A.kt:36)
        at com.emberdeep.game.sim.SimulationTest.the dungeon can be beaten and every run terminates(SimulationTest.kt:55)

CombatTest > armour class reduces the hit rate PASSED

CombatTest > a better weapon and higher level deal more damage PASSED

CombatTest > monster attacks respect the hero's armour PASSED

CombatTest > natural 20 hits even against an impossible armour class PASSED

CombatTest > player attacks always deal at least one damage when they land PASSED

FovTest > computing outside the map is harmless PASSED
```
