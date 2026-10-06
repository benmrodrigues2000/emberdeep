package com.emberdeep.game.systems

import com.emberdeep.game.core.Palette
import com.emberdeep.game.gen.DungeonGenerator
import com.emberdeep.game.model.ClassType
import com.emberdeep.game.model.DungeonMap
import com.emberdeep.game.model.Enemy
import com.emberdeep.game.model.EnemySpecial
import com.emberdeep.game.model.EnemyType
import com.emberdeep.game.model.GameState
import com.emberdeep.game.model.Item
import com.emberdeep.game.model.ItemKind
import com.emberdeep.game.model.ItemType

/** Visual/audio side effects raised by game logic; implemented by GameScreen. */
interface TurnEvents {
    fun onFloatText(x: Int, y: Int, text: String, color: Int, big: Boolean = false)
    fun onStruck(x: Int, y: Int, color: Int, heavy: Boolean)
    fun onMissEffect(x: Int, y: Int)
    fun onFire(x: Int, y: Int)
    fun onHealEffect(x: Int, y: Int)
    fun onEnemyDied(e: Enemy)
    fun onGoldPicked(amount: Int)
    fun onItemPicked(name: String)
    fun onLevelUp()
    fun onPlayerDied()
    fun onVictory()
    fun onDescended()
    fun onTeleport()
    fun onDoorOpened()
    fun onPlayerHurt(heavy: Boolean)
    fun onAttackSwing(crit: Boolean, hit: Boolean)
}

/**
 * The heart of the game: executes player actions, then runs every enemy's
 * turn, status effects, FOV and win/lose checks. Deterministic — no timing
 * or frame-rate dependence; animation is purely cosmetic and lives in the UI.
 */
class TurnEngine(
    private val state: GameState,
    private val events: TurnEvents,
    private val autosave: () -> Unit
) {

    val player get() = state.player
    val map get() = state.map

    var gameEnded = false
        private set

    /**
     * Set when a class ability consumed its cooldown this turn, so the end of
     * turn tick does not immediately shave a turn off it (off-by-one).
     */
    private var abilityUsedThisTurn = false

    fun refreshFov() {
        Fov.compute(map, player.x, player.y, FOV_RADIUS)
    }

    private fun log(text: String, color: Int = Palette.TEXT_DIM) = state.addLog(text, color)

    // ----------------------------------------------------------------- //
    // Player actions. Each returns true when a turn was consumed.
    // ----------------------------------------------------------------- //

    fun tryMove(dx: Int, dy: Int): Boolean {
        if (gameEnded) return false
        val nx = player.x + dx
        val ny = player.y + dy
        if (!map.inBounds(nx, ny)) return false

        val enemy = state.enemyAt(nx, ny)
        if (enemy != null) {
            meleeAttack(enemy)
            endPlayerTurn()
            return true
        }
        if (!map.isWalkable(nx, ny)) return false

        if (map.tile(nx, ny) == DungeonMap.DOOR) {
            map.setTile(nx, ny, DungeonMap.FLOOR)
            log("You push the old door open.", Palette.TEXT_DIM)
            events.onDoorOpened()
            endPlayerTurn()
            return true
        }

        player.x = nx
        player.y = ny
        pickupHere()
        endPlayerTurn()
        return true
    }

    fun waitTurn(): Boolean {
        if (gameEnded) return false
        endPlayerTurn()
        return true
    }

    fun descend(): Boolean {
        if (gameEnded) return false
        if (player.x != map.stairsX || player.y != map.stairsY) return false
        val next = state.floor + 1
        buildFloor(next)
        log("You descend to floor $next of the Emberdeep.", Palette.EMBER_BRIGHT)
        if (next == DungeonGenerator.FINAL_FLOOR) {
            log("A vast heat rises from below. Something ancient stirs...", Palette.BAD)
        }
        events.onDescended()
        autosave()
        return true
    }

    private fun buildFloor(floor: Int) {
        val data = DungeonGenerator.generate(floor, state.rng)
        state.floor = floor
        state.map = data.map
        state.enemies.clear()
        state.enemies.addAll(data.enemies)
        player.x = data.startX
        player.y = data.startY
        player.snapDraw()
        refreshFov()
    }

    // ----------------------------------------------------------------- //
    // Items
    // ----------------------------------------------------------------- //

    /** @return true when the action consumed a turn. */
    fun useItem(item: Item): Boolean {
        if (gameEnded) return false
        val t = item.type
        when (t.kind) {
            ItemKind.WEAPON -> {
                player.inventory.remove(item)
                val old = player.weapon
                player.weapon = t
                if (old != null) player.addItem(old)
                log("You wield the ${t.display}.", Palette.GOOD)
                return false
            }
            ItemKind.ARMOR -> {
                player.inventory.remove(item)
                val old = player.armor
                player.armor = t
                if (old != null) player.addItem(old)
                log("You don the ${t.display}.", Palette.GOOD)
                return false
            }
            ItemKind.POTION -> {
                player.removeOne(item)
                drinkPotion(t)
                endPlayerTurn()
                return true
            }
            ItemKind.SCROLL -> {
                player.removeOne(item)
                readScroll(t)
                if (!gameEnded) endPlayerTurn()
                return true
            }
            ItemKind.GOLD -> return false
        }
    }

    fun dropItem(item: Item) {
        if (map.itemAt(player.x, player.y) != null) {
            log("There is no room to drop that here.", Palette.TEXT_DIM)
            return
        }
        player.inventory.remove(item)
        map.groundItems.add(
            com.emberdeep.game.model.GroundItem(player.x, player.y, item)
        )
        log("You drop the ${item.type.display}.", Palette.TEXT_DIM)
    }

    private fun drinkPotion(t: ItemType) {
        when (t) {
            ItemType.POTION_HEAL, ItemType.POTION_GREATER_HEAL -> {
                val healed = (player.maxHp - player.hp).coerceAtMost(t.power)
                player.hp += healed
                log("You drink the ${t.display} and recover $healed HP.", Palette.GOOD)
                events.onHealEffect(player.x, player.y)
                events.onFloatText(player.x, player.y, "+$healed", Palette.GOOD)
            }
            ItemType.POTION_STRENGTH -> {
                player.strengthTurns = t.power
                log("Your muscles surge with might! (+3 attack)", Palette.GOOD)
                events.onHealEffect(player.x, player.y)
            }
            ItemType.POTION_SHIELD -> {
                player.shieldTurns = t.power
                log("Your skin hardens like stone. (+4 AC)", Palette.GOOD)
                events.onHealEffect(player.x, player.y)
            }
            else -> {}
        }
    }

    private fun readScroll(t: ItemType) {
        when (t) {
            ItemType.SCROLL_FIREBALL -> {
                log("The scroll erupts in flame!", Palette.EMBER_BRIGHT)
                val targets = state.enemies.filter { it.hp > 0 && map.isVisible(it.x, it.y) }
                if (targets.isEmpty()) log("...but nothing was there to burn.", Palette.TEXT_DIM)
                for (e in targets) {
                    val dmg = state.rng.dice(4, 6)
                    e.hp -= dmg
                    e.burnTurns = 2
                    e.awake = true
                    events.onFire(e.x, e.y)
                    events.onFloatText(e.x, e.y, "$dmg", Palette.EMBER_BRIGHT)
                    if (e.hp <= 0) killEnemy(e) else {
                        log("${e.type.display} is scorched for $dmg!", Palette.EMBER)
                    }
                }
            }
            ItemType.SCROLL_TELEPORT -> {
                teleportPlayerRandom()
            }
            else -> {}
        }
    }

    private fun teleportPlayerRandom() {
        for (attempt in 0 until 200) {
            val x = state.rng.nextInt(map.width)
            val y = state.rng.nextInt(map.height)
            if (map.isWalkable(x, y) && !state.isOccupied(x, y)) {
                player.x = x
                player.y = y
                player.snapDraw()
                refreshFov()
                log("Reality folds and you blink elsewhere.", Palette.INFO)
                events.onTeleport()
                pickupHere()
                return
            }
        }
    }

    // ----------------------------------------------------------------- //
    // Class abilities
    // ----------------------------------------------------------------- //

    fun abilityReady(): Boolean = player.abilityCd <= 0 && !gameEnded

    /** Fighter ability — no target needed. */
    fun useWhirlwind(): Boolean {
        if (!abilityReady() || player.classType != ClassType.FIGHTER) return false
        val targets = state.enemies.filter {
            it.hp > 0 && Pathfinder.chebyshev(it.x, it.y, player.x, player.y) <= 1
        }
        if (targets.isEmpty()) {
            log("You spin your blade at empty air.", Palette.TEXT_DIM)
            return false
        }
        log("You unleash a whirlwind of steel!", Palette.EMBER_BRIGHT)
        events.onAttackSwing(crit = true, hit = true)
        for (e in targets) meleeAttack(e, bonusDmg = 4, silentSwing = true)
        player.abilityCd = player.classType.abilityCooldown
        abilityUsedThisTurn = true
        endPlayerTurn()
        return true
    }

    /** Rogue ability — teleport to a visible tile. */
    fun useShadowstep(tx: Int, ty: Int): Boolean {
        if (!abilityReady() || player.classType != ClassType.ROGUE) return false
        if (!map.isVisible(tx, ty) || !map.isWalkable(tx, ty) || state.isOccupied(tx, ty)) {
            log("You cannot step there.", Palette.TEXT_DIM)
            return false
        }
        player.x = tx
        player.y = ty
        player.snapDraw()
        player.autoCrit = true
        player.abilityCd = player.classType.abilityCooldown
        abilityUsedThisTurn = true
        log("You melt into shadow. Your next strike will be lethal.", Palette.INFO)
        events.onTeleport()
        pickupHere()
        endPlayerTurn()
        return true
    }

    /** Mage ability — firebolt a visible enemy. */
    fun useFirebolt(target: Enemy): Boolean {
        if (!abilityReady() || player.classType != ClassType.MAGE) return false
        if (target.hp <= 0 || !map.isVisible(target.x, target.y)) return false
        val dmg = state.rng.dice(3, 6)
        target.hp -= dmg
        target.burnTurns = 3
        target.awake = true
        events.onFire(target.x, target.y)
        events.onFloatText(target.x, target.y, "$dmg", Palette.EMBER_BRIGHT, big = true)
        log("Your firebolt sears the ${target.type.display} for $dmg!", Palette.EMBER_BRIGHT)
        if (target.hp <= 0) killEnemy(target)
        player.abilityCd = player.classType.abilityCooldown
        abilityUsedThisTurn = true
        endPlayerTurn()
        return true
    }

    // ----------------------------------------------------------------- //
    // Internals
    // ----------------------------------------------------------------- //

    private fun meleeAttack(enemy: Enemy, bonusDmg: Int = 0, silentSwing: Boolean = false) {
        val autoCrit = player.autoCrit
        player.autoCrit = false
        player.startLunge(enemy.x - player.x, enemy.y - player.y)
        val r = Combat.playerAttack(state.rng, player, enemy, autoCrit, bonusDmg)
        enemy.awake = true
        if (!silentSwing) events.onAttackSwing(r.crit, r.hit)
        if (!r.hit) {
            log("You miss the ${enemy.type.display}.", Palette.TEXT_DIM)
            events.onMissEffect(enemy.x, enemy.y)
            return
        }
        enemy.hitFlash = 1f
        events.onStruck(enemy.x, enemy.y, enemy.type.tint, r.crit)
        events.onFloatText(
            enemy.x, enemy.y, "${r.dmg}",
            if (r.crit) Palette.GOLD else Palette.TEXT, big = r.crit
        )
        if (r.crit) log("Critical hit! ${enemy.type.display} takes ${r.dmg}.", Palette.GOLD)
        else log("You hit the ${enemy.type.display} for ${r.dmg}.", Palette.TEXT)
        if (enemy.hp <= 0) killEnemy(enemy)
    }

    private fun killEnemy(enemy: Enemy) {
        state.enemies.remove(enemy)
        player.kills++
        events.onEnemyDied(enemy)
        log("The ${enemy.type.display} is slain!", Palette.GOOD)

        // Loot drop.
        if (enemy.type == EnemyType.EMBER_DRAGON) {
            state.bossDefeated = true
            gameEnded = true
            events.onVictory()
            return
        }
        if (state.rng.chance(0.35f) && map.itemAt(enemy.x, enemy.y) == null) {
            map.groundItems.add(
                com.emberdeep.game.model.GroundItem(
                    enemy.x, enemy.y, Item(ItemType.GOLD_PILE),
                    3 + state.floor * 2 + state.rng.nextInt(8)
                )
            )
        }

        for (msg in player.gainXp(enemy.xp)) {
            log(msg, Palette.GOLD)
            events.onLevelUp()
        }
    }

    private fun pickupHere() {
        val gi = map.itemAt(player.x, player.y) ?: return
        if (gi.item.type.kind == ItemKind.GOLD) {
            player.gold += gi.gold
            map.groundItems.remove(gi)
            log("You pick up ${gi.gold} gold.", Palette.GOLD)
            events.onGoldPicked(gi.gold)
            events.onFloatText(player.x, player.y, "+${gi.gold}g", Palette.GOLD)
            return
        }
        if (player.addItem(gi.item.type, gi.item.count)) {
            map.groundItems.remove(gi)
            log("You pick up the ${gi.item.type.display}.", Palette.GOOD)
            events.onItemPicked(gi.item.type.display)
        } else {
            log("Your pack is full!", Palette.BAD)
        }
    }

    private fun endPlayerTurn() {
        if (gameEnded) return
        state.turn++

        // Player status effects.
        if (player.strengthTurns > 0) player.strengthTurns--
        if (player.shieldTurns > 0) player.shieldTurns--
        if (player.abilityCd > 0 && !abilityUsedThisTurn) player.abilityCd--
        abilityUsedThisTurn = false
        if (player.burnTurns > 0) {
            player.burnTurns--
            damagePlayer(2, "The flames sear you for 2!", heavy = false)
            if (gameEnded) return
        }

        refreshFov()
        runEnemyTurns()
        if (gameEnded) return
        refreshFov()

        if (state.turn % AUTOSAVE_TURNS == 0) autosave()
    }

    private fun runEnemyTurns() {
        // Iterate over a copy: kills/burns can mutate the list.
        val list = state.enemies.toList()
        for (e in list) {
            if (e.hp <= 0 || gameEnded) continue
            tickEnemyStatus(e)
            if (e.hp <= 0 || gameEnded) continue
            actEnemy(e)
        }
    }

    private fun tickEnemyStatus(e: Enemy) {
        if (e.burnTurns > 0) {
            e.burnTurns--
            e.hp -= 3
            if (map.isVisible(e.x, e.y)) {
                events.onFloatText(e.x, e.y, "3", Palette.EMBER)
                events.onFire(e.x, e.y)
            }
            if (e.hp <= 0) {
                log("The ${e.type.display} burns to ash!", Palette.EMBER)
                killEnemy(e)
                return
            }
        }
        if (e.type.special == EnemySpecial.REGEN && e.hp < e.maxHp && e.burnTurns == 0) {
            e.hp = (e.hp + 2).coerceAtMost(e.maxHp)
        }
    }

    private fun actEnemy(e: Enemy) {
        val dist = Pathfinder.dist(e.x, e.y, player.x, player.y)

        // Wake up when the player is in sight (FOV is symmetric enough).
        if (!e.awake) {
            if (map.isVisible(e.x, e.y) && dist <= WAKE_RANGE) {
                e.awake = true
                if (e.type == EnemyType.EMBER_DRAGON) {
                    log("THE EMBER DRAGON AWAKENS!", Palette.BAD)
                }
            } else if (dist <= 2) {
                e.awake = true
            } else {
                return
            }
        }

        if (e.type == EnemyType.EMBER_DRAGON) {
            actDragon(e, dist)
            return
        }

        // Melee adjacency (4-directional, same as movement).
        if (dist == 1) {
            enemyStrike(e)
            return
        }

        // Ranged attack.
        if (e.type.ranged && dist <= e.type.attackRange &&
            Fov.lineOfSight(map, e.x, e.y, player.x, player.y)
        ) {
            enemyStrike(e, ranged = true)
            return
        }

        moveEnemyTowardPlayer(e)
    }

    private fun actDragon(e: Enemy, dist: Int) {
        e.breathCharge++
        val hasLos = Fov.lineOfSight(map, e.x, e.y, player.x, player.y)
        if (e.breathCharge >= 4 && dist <= 6 && hasLos) {
            e.breathCharge = 0
            val dmg = state.rng.dice(3, 6)
            log("The Ember Dragon breathes fire! ($dmg damage)", Palette.BAD)
            events.onFire(player.x, player.y)
            player.burnTurns = (player.burnTurns + 2).coerceAtMost(4)
            damagePlayer(dmg, null, heavy = true)
            return
        }
        if (dist == 1) {
            enemyStrike(e)
            return
        }
        moveEnemyTowardPlayer(e)
    }

    private fun enemyStrike(e: Enemy, ranged: Boolean = false) {
        e.startLunge(
            Integer.signum(player.x - e.x), Integer.signum(player.y - e.y)
        )
        val r = Combat.enemyAttack(state.rng, e, player)
        if (!r.hit) {
            log("The ${e.type.display} misses you.", Palette.TEXT_FAINT)
            events.onMissEffect(player.x, player.y)
            return
        }
        if (e.type.special == EnemySpecial.BURNER && state.rng.chance(0.3f)) {
            player.burnTurns = (player.burnTurns + 2).coerceAtMost(4)
            log("Embers cling to you — you are burning!", Palette.EMBER)
        }
        if (e.type.special == EnemySpecial.DRAIN) {
            e.hp = (e.hp + r.dmg / 2).coerceAtMost(e.maxHp)
        }
        val verb = if (ranged) "shoots" else "hits"
        val msg = if (r.crit) {
            "The ${e.type.display} CRITICALLY $verb you for ${r.dmg}!"
        } else {
            "The ${e.type.display} $verb you for ${r.dmg}."
        }
        log(msg, if (r.crit) Palette.BAD else Palette.HP)
        events.onFloatText(player.x, player.y, "${r.dmg}", Palette.BAD, big = r.crit)
        damagePlayer(0, null, heavy = r.crit) // effects + death check; hp already reduced
    }

    private fun moveEnemyTowardPlayer(e: Enemy) {
        val map = state.map
        if (e.type.special == EnemySpecial.PHASE ||
            e.type.special == EnemySpecial.DRAIN
        ) {
            // Wraiths drift straight through stone.
            val step = Pathfinder.greedyStep(e.x, e.y, player.x, player.y)
            if (map.inBounds(step[0], step[1]) && !state.isOccupied(step[0], step[1])) {
                e.x = step[0]; e.y = step[1]
                return
            }
        }
        // Tiles held by allies are expensive rather than impassable, so packs
        // take a detour instead of queueing up behind each other, and the step
        // is only taken when the destination is genuinely free.
        val step = Pathfinder.nextStep(
            state, e.x, e.y, player.x, player.y,
            blockOccupied = false, exploredOnly = false
        )
        if (step < 0) return
        val nx = step % map.width
        val ny = step / map.width
        if (!state.isOccupied(nx, ny)) {
            e.x = nx
            e.y = ny
        }
    }

    /** Apply damage (when [amount] > 0) plus hurt feedback and death check. */
    private fun damagePlayer(amount: Int, message: String?, heavy: Boolean) {
        if (amount > 0) {
            player.hp -= amount
            if (message != null) log(message, Palette.HP)
            events.onFloatText(player.x, player.y, "$amount", Palette.BAD, big = heavy)
        }
        player.hitFlash = 1f
        events.onPlayerHurt(heavy)
        checkPlayerDeath()
    }

    private fun checkPlayerDeath() {
        if (player.hp > 0 || gameEnded) return
        player.hp = 0
        gameEnded = true
        log("You have fallen in the Emberdeep...", Palette.BAD)
        events.onPlayerDied()
    }

    companion object {
        const val FOV_RADIUS = 7
        const val WAKE_RANGE = 7
        const val AUTOSAVE_TURNS = 10
    }
}
