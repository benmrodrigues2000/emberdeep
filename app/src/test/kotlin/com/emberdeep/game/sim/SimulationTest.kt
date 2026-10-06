package com.emberdeep.game.sim

import com.emberdeep.game.core.Rng
import com.emberdeep.game.gen.DungeonGenerator
import com.emberdeep.game.model.ClassType
import com.emberdeep.game.model.GameState
import com.emberdeep.game.model.ItemType
import com.emberdeep.game.model.Player
import com.emberdeep.game.systems.Fov
import com.emberdeep.game.systems.TurnEngine
import com.emberdeep.game.testutil.A
import org.junit.Test

/**
 * Plays the game headlessly and checks both that it never breaks and that it
 * is actually beatable. Every failure message names the seed, so any problem
 * reported here can be reproduced exactly by re-running that seed.
 */
class SimulationTest {

    @Test
    fun `twelve complete expeditions never break a structural invariant`() {
        val results = ArrayList<RunResult>()
        for (i in 0 until 12) {
            val cls = ClassType.entries[i % ClassType.entries.size]
            val sim = Simulation(seed = 0xE1DE0000L + i, cls = cls)
            val result = sim.run()
            A.isTrue(
                result.violation == null,
                "invariant broken (seed ${result.seed}, ${result.cls.display}): ${result.violation}"
            )
            A.isTrue(result.turns > 0, "seed ${result.seed}: the run never advanced")
            results.add(result)
        }
        A.eq(12, results.size, "runs completed")
        A.isTrue(results.any { it.turns > 30 }, "runs should last a while, not end instantly")
        println(BalanceReport.format(results))
    }

    @Test
    fun `the dungeon can be beaten and every run terminates`() {
        val results = (0 until 21).map { i ->
            Simulation(
                seed = 0xC0FFEE00L + i,
                cls = ClassType.entries[i % ClassType.entries.size]
            ).run()
        }
        val report = BalanceReport.format(results)

        for (r in results) {
            A.isTrue(
                r.violation == null,
                "invariant broken (seed ${r.seed}, ${r.cls.display}): ${r.violation}\n$report"
            )
            A.isTrue(r.turns < Simulation.MAX_TURNS, "seed ${r.seed}: run hit the turn cap\n$report")
            A.isTrue(
                r.victory || r.died,
                "seed ${r.seed}: the run neither ended in victory nor in death\n$report"
            )
        }
        A.isTrue(
            results.any { it.floorReached >= DungeonGenerator.FINAL_FLOOR },
            "no simulated expedition ever reached the dragon: the difficulty ramp is too steep\n$report"
        )
        A.isTrue(
            results.any { it.victory },
            "no simulated expedition ever won: the game looks unwinnable\n$report"
        )
    }

    @Test
    fun `a levelled and geared hero can kill the dragon`() {
        val rng = Rng(777L)
        val floor = DungeonGenerator.generate(DungeonGenerator.FINAL_FLOOR, rng)
        val hero = Player(ClassType.FIGHTER)
        hero.x = floor.startX
        hero.y = floor.startY
        hero.snapDraw()
        hero.level = 8
        hero.maxHp = ClassType.FIGHTER.baseHp + 7 * (ClassType.FIGHTER.hitDie + 2)
        hero.hp = hero.maxHp
        hero.weapon = ItemType.EMBERFANG
        hero.armor = ItemType.EMBERPLATE
        repeat(5) { hero.addItem(ItemType.POTION_GREATER_HEAL) }
        hero.addItem(ItemType.POTION_STRENGTH)
        hero.addItem(ItemType.POTION_SHIELD)
        hero.addItem(ItemType.SCROLL_FIREBALL)

        val state = GameState(DungeonGenerator.FINAL_FLOOR, hero, floor.map, rng)
        state.enemies.addAll(floor.enemies)
        Fov.compute(state.map, hero.x, hero.y, TurnEngine.FOV_RADIUS)

        val events = RecordingEvents()
        val engine = TurnEngine(state, events) { }
        val bot = Bot(Rng(99L))
        var turns = 0
        while (!engine.gameEnded && turns < 600) {
            var consumed = false
            var attempts = 0
            while (!consumed && attempts++ < 8) {
                bot.resetTurn()
                consumed = bot.takeTurn(state, engine)
            }
            A.isTrue(
                Invariants.check(state, engine) == null,
                "endgame invariant broken after ${turns} turns: ${Invariants.check(state, engine)}"
            )
            turns++
        }

        val dragonHp = floor.enemies.firstOrNull { it.type.name == "EMBER_DRAGON" }?.hp ?: 0
        println(
            "EMBERDEEP ENDGAME: turns=$turns swings=${events.swings} hits=${events.strikes} " +
                "misses=${events.misses} heals=${events.heals} hero=${hero.hp}/${hero.maxHp} " +
                "dragon=$dragonHp bossDefeated=${state.bossDefeated}"
        )
        println("EMBERDEEP ENDGAME LOG: " + state.log.takeLast(10).joinToString(" | ") { it.text })
        A.isTrue(
            state.bossDefeated,
            "the dragon must be killable by a levelled hero with endgame gear " +
                "(survived $turns turns, hero at ${hero.hp}/${hero.maxHp} hp, " +
                "dragon at $dragonHp hp, swings=${events.swings} hits=${events.strikes}, " +
                "misses=${events.misses}, potions=${events.heals})"
        )
        A.eq(1, events.victories, "the victory event fires exactly once")
    }

    @Test
    fun `a lone hero on floor one is never instantly killed`() {
        // Fairness guard: the starting floor must not be able to end a run
        // before the player has been given a chance to act.
        for (i in 0 until 9) {
            val sim = Simulation(seed = 0xFA12L + i, cls = ClassType.entries[i % 3])
            val result = sim.run()
            A.isTrue(
                result.turns > 5,
                "seed ${result.seed}: the run ended after only ${result.turns} turns"
            )
        }
    }
}
