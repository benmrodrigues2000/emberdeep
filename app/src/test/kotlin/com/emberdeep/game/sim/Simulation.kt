package com.emberdeep.game.sim

import com.emberdeep.game.core.Rng
import com.emberdeep.game.model.ClassType
import com.emberdeep.game.model.GameState
import com.emberdeep.game.systems.RunSetup
import com.emberdeep.game.systems.TurnEngine

/** How one simulated expedition ended. */
class RunResult(
    val seed: Long,
    val cls: ClassType,
    val victory: Boolean,
    val died: Boolean,
    val timedOut: Boolean,
    val floorReached: Int,
    val level: Int,
    val kills: Int,
    val gold: Int,
    val turns: Int,
    val violation: String?
) {
    val finished: Boolean get() = victory || died
}

/** Structural rules that must hold after every single turn of every run. */
object Invariants {

    private var markers = IntArray(0)
    private var generation = 0

    fun check(state: GameState, engine: TurnEngine): String? {
        val map = state.map
        val p = state.player
        if (!map.inBounds(p.x, p.y)) return "player out of bounds (${p.x}, ${p.y})"
        if (!map.isWalkable(p.x, p.y)) return "player inside a non-walkable tile"
        if (p.hp < 0) return "player hp is negative (${p.hp})"
        if (p.hp > p.maxHp) return "player hp ${p.hp} exceeds max ${p.maxHp}"
        if (!engine.gameEnded && p.hp <= 0) return "player at ${p.hp} hp but the run is not over"
        if (!p.drawX.isFinite() || !p.drawY.isFinite()) return "player draw position is not finite"
        if (p.level < 1 || p.xp < 0) return "corrupt progression (level ${p.level}, xp ${p.xp})"
        if (p.inventory.size > com.emberdeep.game.model.Player.MAX_SLOTS) return "inventory overflow"
        if (p.inventory.any { it.count <= 0 }) return "empty item stack in inventory"
        if (state.turn < 0) return "negative turn counter"
        if (map.stairsX >= 0 && !map.isWalkable(map.stairsX, map.stairsY)) {
            return "stairs placed on a tile that cannot be used"
        }

        val n = map.width * map.height
        if (markers.size < n) {
            markers = IntArray(n)
            generation = 0
        }
        generation++
        for (e in state.enemies) {
            if (!map.inBounds(e.x, e.y)) return "${e.type} out of bounds (${e.x}, ${e.y})"
            if (e.hp <= 0) return "${e.type} is still listed with ${e.hp} hp"
            if (e.hp > e.maxHp) return "${e.type} hp ${e.hp} exceeds max ${e.maxHp}"
            if (e.x == p.x && e.y == p.y) return "${e.type} is stacked on the player"
            if (!e.drawX.isFinite()) return "${e.type} draw position is not finite"
            val i = map.idx(e.x, e.y)
            if (markers[i] == generation) return "two monsters share tile (${e.x}, ${e.y})"
            markers[i] = generation
        }

        for (gi in map.groundItems) {
            if (!map.isWalkable(gi.x, gi.y)) return "ground item on an unusable tile"
            if (gi.item.count <= 0) return "ground item stack is empty"
        }
        if (state.bossDefeated && state.enemies.count { it.type.name == "EMBER_DRAGON" } > 0) {
            return "boss marked defeated but still alive"
        }
        return null
    }
}

/**
 * Plays complete expeditions headlessly through the real game systems
 * (generator → turn engine → combat → saves-free state) with a scripted bot.
 *
 * Deterministic: the same seed always produces the same run, so any failure
 * reported by the test suite can be reproduced exactly.
 */
class Simulation(private val seed: Long, private val cls: ClassType) {

    val state: GameState = RunSetup.newRun(cls, seed)
    val events = RecordingEvents()
    val engine = TurnEngine(state, events) { /* no autosave while simulating */ }
    private val bot = Bot(Rng(seed * 31L + 7L))

    fun run(maxTurns: Int = MAX_TURNS): RunResult {
        var violation: String? = null
        var guard = 0
        while (state.turn < maxTurns && !engine.gameEnded) {
            bot.resetTurn()
            var consumed = false
            var attempts = 0
            while (!consumed && attempts++ < RETRY_LIMIT) {
                consumed = bot.takeTurn(state, engine)
            }
            violation = Invariants.check(state, engine)
            if (violation != null) break
            // The bot is stuck (nothing it may legally do): stop rather than spin.
            if (!consumed) break
            if (++guard > maxTurns * 2) break
        }
        val p = state.player
        return RunResult(
            seed = seed,
            cls = cls,
            victory = state.bossDefeated,
            died = p.hp <= 0,
            timedOut = !state.bossDefeated && p.hp > 0,
            floorReached = state.floor,
            level = p.level,
            kills = p.kills,
            gold = p.gold,
            turns = state.turn,
            violation = violation
        )
    }

    companion object {
        /** Generous: a careful full run takes a few hundred turns. */
        const val MAX_TURNS = 1500

        /** How many different actions the bot may try inside a single turn. */
        private const val RETRY_LIMIT = 8
    }
}

/** Aggregates run results into the balance report printed by CI. */
object BalanceReport {

    fun format(results: List<RunResult>): String {
        val sb = StringBuilder()
        val wins = results.count { it.victory }
        val deaths = results.count { it.died }
        val timeouts = results.count { it.timedOut }
        val violations = results.filter { it.violation != null }
        val finished = results.filter { it.finished }

        sb.appendLine()
        sb.appendLine("================ EMBERDEEP BALANCE SIMULATION ================")
        sb.appendLine("runs: ${results.size}   wins: $wins   deaths: $deaths   timeouts: $timeouts")
        sb.appendLine(
            "win rate: ${pct(wins, results.size)}   " +
                "avg floor reached: ${avg(results.map { it.floorReached })}   " +
                "avg level: ${avg(results.map { it.level })}"
        )
        sb.appendLine(
            "avg kills (finished runs): ${avg(finished.map { it.kills })}   " +
                "avg turns: ${avg(results.map { it.turns })}   " +
                "avg gold: ${avg(results.map { it.gold })}"
        )
        for (cls in ClassType.entries) {
            val group = results.filter { it.cls == cls }
            if (group.isEmpty()) continue
            sb.appendLine(
                "  ${cls.display.padEnd(8)} runs ${group.size}   wins ${
                    group.count { it.victory }
                }   avg floor ${avg(group.map { it.floorReached })}   avg level ${
                    avg(group.map { it.level })
                }"
            )
        }
        // Where heroes die.
        val byFloor = IntArray(16)
        results.filter { it.died }.forEach { byFloor[it.floorReached.coerceIn(0, 15)]++ }
        sb.append("deaths by floor: ")
        for (f in 1..10) if (byFloor[f] > 0) sb.append("F$f=${byFloor[f]}  ")
        sb.appendLine()
        violations.forEach { sb.appendLine("INVARIANT VIOLATION seed=${it.seed} class=${it.cls}: ${it.violation}") }
        sb.appendLine("==============================================================")
        return sb.toString()
    }

    private fun pct(n: Int, total: Int): String =
        if (total == 0) "n/a" else "${n * 1000 / total / 10}.${n * 1000 / total % 10}%"

    private fun avg(values: List<Int>): String {
        if (values.isEmpty()) return "n/a"
        val mean = values.sum().toDouble() / values.size
        return (kotlin.math.round(mean * 10) / 10).toString()
    }
}
