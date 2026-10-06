package com.emberdeep.game.sim

import com.emberdeep.game.core.Rng
import com.emberdeep.game.gen.DungeonGenerator
import com.emberdeep.game.model.ClassType
import com.emberdeep.game.model.Enemy
import com.emberdeep.game.model.EnemyType
import com.emberdeep.game.model.GameState
import com.emberdeep.game.model.ItemKind
import com.emberdeep.game.model.ItemType
import com.emberdeep.game.model.Player
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
    var swings = 0
    var swingHits = 0
    var misses = 0

    override fun onFloatText(x: Int, y: Int, text: String, color: Int, big: Boolean) { floats++ }
    override fun onStruck(x: Int, y: Int, color: Int, heavy: Boolean) { strikes++ }
    override fun onMissEffect(x: Int, y: Int) { misses++ }
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
    override fun onAttackSwing(crit: Boolean, hit: Boolean) {
        swings++
        if (hit) swingHits++
    }
}

/**
 * A scripted adventurer that plays a real run through the real [TurnEngine].
 *
 * Intentionally a *competent but not perfect* player: it beelines for the
 * stairs, fights whatever blocks the path, heals when hurt, uses its class
 * ability and prepares for the boss. Its win rate therefore measures how
 * winnable the game is, and any invariant violation it hits is a genuine bug.
 *
 * Two policy details matter for the simulation to be meaningful:
 *
 *  * **Goals are committed.** Weighing every item on the floor freshly each
 *    turn made the bot pace back and forth between two treasures forever
 *    (Manhattan distance rises while walking around a wall), so a chosen
 *    destination is kept until it is reached or abandoned.
 *  * **Treasure trips are bounded** by real path length and by a turn budget,
 *    so looting can never crowd out descending.
 */
class Bot(private val rng: Rng) {

    /** Actions attempted for one turn; more than expected means it is stuck. */
    private var attempts = 0

    /** The treasure the bot has currently committed to, if any. */
    private var lootGoal: IntArray? = null
    private var lootTurns = 0

    /**
     * Tiles whose loot the hero already failed to pick up (a full pack, for
     * example): remembered so it is never chased again.
     */
    private val uncollectable = HashSet<Int>()

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

        // Standing on loot that did not move into the pack means it never will.
        if (state.map.itemAt(p.x, p.y) != null) {
            uncollectable.add(p.y * state.map.width + p.x)
            lootGoal = null
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

        // 3. Wear anything better, and unload an offensive scroll into a crowd.
        equipUpgrades(state, engine)
        if (visibleEnemies(state) >= 3 && readScroll(state, engine, ItemType.SCROLL_FIREBALL)) {
            return true
        }

        // 4. Class ability.
        if (engine.abilityReady() && useAbility(state, engine)) return true

        // 5. Attack anything adjacent (cheapest kill first).
        val adjacent = state.enemies.filter {
            it.hp > 0 && Pathfinder.dist(it.x, it.y, p.x, p.y) == 1
        }
        if (adjacent.isNotEmpty()) {
            val target = adjacent.minByOrNull { it.hp } ?: adjacent.first()
            if (engine.tryMove(target.x - p.x, target.y - p.y)) return true
        }

        // 6. The stairs are right here.
        if (p.x == state.map.stairsX && p.y == state.map.stairsY &&
            state.floor < DungeonGenerator.FINAL_FLOOR
        ) {
            if (engine.descend()) return true
        }

        // 7. Treasure — a committed, bounded detour.
        if (stepToLoot(state, engine)) return true

        // 8. Objective: the stairs, or the dragon on the final floor.
        val goal = objective(state) ?: return engine.waitTurn()
        if (stepToward(state, engine, goal[0], goal[1])) return true
        return engine.waitTurn()
    }

    // ------------------------------------------------------------------ //
    // Movement
    // ------------------------------------------------------------------ //

    /** One step of the path towards (tx, ty); attacks whatever blocks the way. */
    private fun stepToward(state: GameState, engine: TurnEngine, tx: Int, ty: Int): Boolean {
        val p = state.player
        if (p.x == tx && p.y == ty) return false
        val path = Pathfinder.find(
            state, p.x, p.y, tx, ty,
            blockOccupied = false, exploredOnly = false
        )
        if (path.isEmpty()) {
            // No route: take a greedy step so a run can never stall waiting for
            // something it will never reach.
            val greedy = Pathfinder.greedyStep(p.x, p.y, tx, ty)
            if (!state.map.isWalkable(greedy[0], greedy[1])) return false
            return engine.tryMove(greedy[0] - p.x, greedy[1] - p.y)
        }
        val step = path.first()
        return engine.tryMove(step[0] - p.x, step[1] - p.y)
    }

    /**
     * Walks towards the committed treasure, choosing one when none is held.
     *
     * @return true when a turn was consumed.
     */
    private fun stepToLoot(state: GameState, engine: TurnEngine): Boolean {
        val p = state.player
        var goal = lootGoal

        if (goal != null) {
            val stillThere = state.map.itemAt(goal[0], goal[1]) != null
            val expired = ++lootTurns > LOOT_TURN_BUDGET
            if (!stillThere || expired) {
                goal = null
                lootGoal = null
            }
        }

        if (goal == null) {
            goal = chooseLoot(state) ?: return false
            lootGoal = goal
            lootTurns = 0
        }

        if (stepToward(state, engine, goal[0], goal[1])) return true
        // Cannot get there after all: give up on this piece.
        uncollectable.add(goal[1] * state.map.width + goal[0])
        lootGoal = null
        return false
    }

    /**
     * The most valuable treasure worth a real detour.
     *
     * Uses path length (not straight-line distance) and only items the pack can
     * actually accept — both of which the simulation proved necessary.
     */
    private fun chooseLoot(state: GameState): IntArray? {
        val p = state.player
        val potions = countPotions(state)
        val onBossFloor = state.floor >= DungeonGenerator.FINAL_FLOOR
        val budget = if (onBossFloor) BOSS_LOOT_PATH_LIMIT else LOOT_PATH_LIMIT
        var bestScore = Int.MAX_VALUE
        var best: IntArray? = null

        for (gi in state.map.groundItems) {
            val type = gi.item.type
            val value = when (type.kind) {
                ItemKind.GOLD -> if (gi.gold >= 15) 2 else 5
                ItemKind.POTION, ItemKind.SCROLL -> when {
                    type == ItemType.POTION_STRENGTH || type == ItemType.POTION_SHIELD -> 2
                    potions < 3 -> 1
                    else -> 4
                }
                ItemKind.WEAPON, ItemKind.ARMOR -> if (isUpgrade(p, type)) 2 else 0
            }
            if (value == 0) continue
            if (!canPickUp(p, type)) continue
            if (uncollectable.contains(gi.y * state.map.width + gi.x)) continue
            if (Pathfinder.dist(p.x, p.y, gi.x, gi.y) > LOOT_RANGE) continue
            val path = Pathfinder.find(
                state, p.x, p.y, gi.x, gi.y,
                blockOccupied = false, exploredOnly = false
            )
            if (path.isEmpty() || path.size > budget) continue
            val score = path.size * value + rng.nextInt(2)
            if (score < bestScore) {
                bestScore = score
                best = intArrayOf(gi.x, gi.y)
            }
        }
        return best
    }

    // ------------------------------------------------------------------ //
    // Objectives and gear
    // ------------------------------------------------------------------ //

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

    private fun countPotions(state: GameState): Int =
        state.player.inventory
            .filter { it.type == ItemType.POTION_HEAL || it.type == ItemType.POTION_GREATER_HEAL }
            .sumOf { it.count }

    private fun drink(state: GameState, engine: TurnEngine, type: ItemType): Boolean {
        val item = state.player.inventory.firstOrNull { it.type == type && it.count > 0 } ?: return false
        return engine.useItem(item)
    }

    private fun readScroll(state: GameState, engine: TurnEngine, type: ItemType): Boolean {
        val scroll = state.player.inventory.firstOrNull { it.type == type && it.count > 0 }
            ?: return false
        return engine.useItem(scroll)
    }

    /** Mirrors [Player.addItem] so the bot never chases what it cannot take. */
    private fun canPickUp(p: Player, type: ItemType): Boolean {
        if (type.kind == ItemKind.GOLD) return true
        if (type.stackable && p.inventory.any { it.type == type }) return true
        return p.inventory.size < Player.MAX_SLOTS
    }

    private fun isUpgrade(p: Player, type: ItemType): Boolean = when (type.kind) {
        ItemKind.WEAPON -> (p.weapon?.tier ?: 0) < type.tier
        ItemKind.ARMOR -> (p.armor?.tier ?: 0) < type.tier
        else -> false
    }

    /** Wearing better gear costs no turn. */
    private fun equipUpgrades(state: GameState, engine: TurnEngine) {
        val p = state.player
        val better = p.inventory
            .filter { isUpgrade(p, it.type) }
            .maxByOrNull { it.type.tier } ?: return
        engine.useItem(better)
    }

    private fun visibleEnemies(state: GameState): Int =
        state.enemies.count { it.hp > 0 && state.map.isVisible(it.x, it.y) }

    // ------------------------------------------------------------------ //
    // Class abilities
    // ------------------------------------------------------------------ //

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

        /** Straight-line distance beyond which a target is not even considered. */
        const val LOOT_RANGE = 12

        /** Path lengths a treasure detour may cost on a normal floor. */
        const val LOOT_PATH_LIMIT = 14

        /** …and on the dragon's floor, where time is health. */
        const val BOSS_LOOT_PATH_LIMIT = 8

        /** A committed detour is abandoned after this many turns. */
        const val LOOT_TURN_BUDGET = 30
    }
}
