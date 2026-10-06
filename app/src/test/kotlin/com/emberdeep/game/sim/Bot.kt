package com.emberdeep.game.sim

import com.emberdeep.game.core.Rng
import com.emberdeep.game.gen.DungeonGenerator
import com.emberdeep.game.model.ClassType
import com.emberdeep.game.model.Enemy
import com.emberdeep.game.model.EnemyType
import com.emberdeep.game.model.GameState
import com.emberdeep.game.model.ItemKind
import com.emberdeep.game.model.ItemType
import com.emberdeep.game.systems.Pathfinder
import com.emberdeep.game.systems.TurnEngine
import com.emberdeep.game.systems.TurnEvents

/** Records the visual/audio side effects gameplay raises, without a screen. */
class RecordingEvents : TurnEvents {
    var kills = 0
    var levelUps = 0
    var descents = 0
    var deaths = 0
    var victories = 0
    var floats = 0
    var strikes = 0
    var heals = 0
    var teleports = 0
    var doors = 0

    override fun onFloatText(x: Int, y: Int, text: String, color: Int, big: Boolean) { floats++ }
    override fun onStruck(x: Int, y: Int, color: Int, heavy: Boolean) { strikes++ }
    override fun onMissEffect(x: Int, y: Int) {}
    override fun onFire(x: Int, y: Int) {}
    override fun onHealEffect(x: Int, y: Int) { heals++ }
    override fun onEnemyDied(e: Enemy) { kills++ }
    override fun onGoldPicked(amount: Int) {}
    override fun onItemPicked(name: String) {}
    override fun onLevelUp() { levelUps++ }
    override fun onPlayerDied() { deaths++ }
    override fun onVictory() { victories++ }
    override fun onDescended() { descents++ }
    override fun onTeleport() { teleports++ }
    override fun onDoorOpened() { doors++ }
    override fun onPlayerHurt(heavy: Boolean) {}
    override fun onAttackSwing(crit: Boolean, hit: Boolean) {}
}

/**
 * A scripted adventurer that plays a real run through the real [TurnEngine].
 *
 * It is intentionally a *competent but not perfect* player: it beelines for the
 * stairs, fights whatever blocks the path, heals when hurt, uses its class
 * ability and prepares for the boss. Its win rate therefore measures how
 * winnable the game is, and any invariant violation it hits is a genuine bug.
 */
class Bot(private val rng: Rng) {

    /** Actions attempted for one turn; more than a couple means something is stuck. */
    private var attempts = 0

    fun resetTurn() { attempts = 0 }

    /** @return true when a turn was consumed. */
    fun takeTurn(state: GameState, engine: TurnEngine): Boolean {
        val p = state.player
        if (engine.gameEnded) return false
        attempts++
        if (attempts > MAX_ATTEMPTS) {
            // Nothing the bot wants to do is available: burn the turn.
            return engine.waitTurn()
        }

        val onBossFloor = state.floor >= DungeonGenerator.FINAL_FLOOR
        val bossAlive = state.enemies.any { it.type == EnemyType.EMBER_DRAGON }
        val hurting = p.hp * 100 < p.maxHp * HEAL_THRESHOLD

        // 1. Stay alive.
        if (hurting || (onBossFloor && bossAlive && p.hp * 100 < p.maxHp * BOSS_HEAL_THRESHOLD)) {
            if (drink(state, engine, ItemType.POTION_GREATER_HEAL)) return true
            if (drink(state, engine, ItemType.POTION_HEAL)) return true
        }

        // 2. Buff before the dragon.
        if (onBossFloor && bossAlive) {
            val dragon = state.enemies.firstOrNull { it.type == EnemyType.EMBER_DRAGON }
            if (dragon != null && p.strengthTurns == 0 && p.hp * 100 > p.maxHp * 60) {
                if (drink(state, engine, ItemType.POTION_STRENGTH)) return true
                if (drink(state, engine, ItemType.POTION_SHIELD)) return true
            }
        }

        // 3. Class ability.
        if (engine.abilityReady() && useAbility(state, engine)) return true

        // 4. Attack anything adjacent (cheapest kill first).
        val adjacent = state.enemies.filter {
            it.hp > 0 && Pathfinder.dist(it.x, it.y, p.x, p.y) == 1
        }
        if (adjacent.isNotEmpty()) {
            val target = adjacent.minByOrNull { it.hp } ?: adjacent.first()
            if (engine.tryMove(target.x - p.x, target.y - p.y)) return true
        }

        // 5. The stairs are right here.
        if (p.x == state.map.stairsX && p.y == state.map.stairsY &&
            state.floor < DungeonGenerator.FINAL_FLOOR
        ) {
            if (engine.descend()) return true
        }

        // 6. Loot when it is cheap and useful.
        val item = lootTarget(state)
        if (item != null && stepToward(state, engine, item[0], item[1])) return true

        // 7. Objective: the stairs, or the dragon on the final floor.
        val goal = objective(state) ?: return engine.waitTurn()
        if (stepToward(state, engine, goal[0], goal[1])) return true
        return engine.waitTurn()
    }

    /** One step of the path towards (tx, ty); attacks whatever blocks the way. */
    private fun stepToward(state: GameState, engine: TurnEngine, tx: Int, ty: Int): Boolean {
        val p = state.player
        if (p.x == tx && p.y == ty) return false
        val path = Pathfinder.find(
            state, p.x, p.y, tx, ty,
            blockOccupied = false, exploredOnly = false
        )
        if (path.isEmpty()) return false
        val step = path.first()
        return engine.tryMove(step[0] - p.x, step[1] - p.y)
    }

    private fun objective(state: GameState): IntArray? {
        val map = state.map
        if (state.floor >= DungeonGenerator.FINAL_FLOOR) {
            val dragon = state.enemies.firstOrNull { it.type == EnemyType.EMBER_DRAGON }
            if (dragon != null) return intArrayOf(dragon.x, dragon.y)
            val any = state.enemies.firstOrNull { it.hp > 0 }
            return if (any != null) intArrayOf(any.x, any.y) else null
        }
        if (map.stairsX < 0 || map.stairsY < 0) return null
        return intArrayOf(map.stairsX, map.stairsY)
    }

    /** Healing potions (or gold right under the bot's feet) worth a detour. */
    private fun lootTarget(state: GameState): IntArray? {
        val p = state.player
        val map = state.map
        val potions = countPotions(state)
        var bestScore = Int.MAX_VALUE
        var best: IntArray? = null
        for (gi in map.groundItems) {
            val value = when (gi.item.type) {
                ItemType.POTION_HEAL, ItemType.POTION_GREATER_HEAL -> if (potions < 3) 1 else 4
                ItemType.POTION_STRENGTH, ItemType.POTION_SHIELD -> 2
                ItemType.GOLD_PILE -> if (gi.gold >= 20) 3 else 6
                else -> {
                    when (gi.item.type.kind) {
                        ItemKind.WEAPON, ItemKind.ARMOR -> 5
                        else -> 8
                    }
                }
            }
            val distance = Pathfinder.dist(p.x, p.y, gi.x, gi.y)
            if (distance == 0) continue
            // Deterministic jitter breaks ties between equally good targets.
            val score = distance * value + rng.nextInt(2)
            if (score < bestScore && distance <= LOOT_RANGE) {
                bestScore = score
                best = intArrayOf(gi.x, gi.y)
            }
        }
        return best
    }

    private fun countPotions(state: GameState): Int =
        state.player.inventory
            .filter { it.type == ItemType.POTION_HEAL || it.type == ItemType.POTION_GREATER_HEAL }
            .sumOf { it.count }

    private fun drink(state: GameState, engine: TurnEngine, type: ItemType): Boolean {
        val item = state.player.inventory.firstOrNull { it.type == type && it.count > 0 } ?: return false
        return engine.useItem(item)
    }

    private fun useAbility(state: GameState, engine: TurnEngine): Boolean {
        val p = state.player
        return when (p.classType) {
            ClassType.FIGHTER -> {
                val adjacent = state.enemies.count {
                    it.hp > 0 && Pathfinder.chebyshev(it.x, it.y, p.x, p.y) <= 1
                }
                adjacent >= 2 && engine.useWhirlwind()
            }
            ClassType.MAGE -> {
                val target = state.enemies
                    .filter { it.hp > 0 && state.map.isVisible(it.x, it.y) }
                    .minByOrNull { it.hp }
                target != null && engine.useFirebolt(target)
            }
            ClassType.ROGUE -> {
                val goal = objective(state) ?: return false
                if (!state.map.isVisible(goal[0], goal[1])) return false
                val tile = bestShadowstepTile(state, goal) ?: return false
                engine.useShadowstep(tile[0], tile[1])
            }
        }
    }

    /** Visible tile that leaves the bot closest to its objective. */
    private fun bestShadowstepTile(state: GameState, goal: IntArray): IntArray? {
        val p = state.player
        val map = state.map
        val radius = TurnEngine.FOV_RADIUS
        var bestScore = Pathfinder.dist(p.x, p.y, goal[0], goal[1])
        var best: IntArray? = null
        for (y in (p.y - radius)..(p.y + radius)) {
            for (x in (p.x - radius)..(p.x + radius)) {
                if (!map.inBounds(x, y) || !map.isVisible(x, y)) continue
                if (!map.isWalkable(x, y) || state.isOccupied(x, y)) continue
                val distance = Pathfinder.dist(x, y, goal[0], goal[1])
                if (distance < bestScore) {
                    bestScore = distance
                    best = intArrayOf(x, y)
                }
            }
        }
        return best
    }

    private companion object {
        const val MAX_ATTEMPTS = 6
        const val HEAL_THRESHOLD = 45
        const val BOSS_HEAL_THRESHOLD = 60
        const val LOOT_RANGE = 10
    }
}
