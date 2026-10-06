package com.emberdeep.game.systems

import com.emberdeep.game.core.Rng
import com.emberdeep.game.model.ClassType
import com.emberdeep.game.model.DungeonMap
import com.emberdeep.game.model.Enemy
import com.emberdeep.game.model.EnemyType
import com.emberdeep.game.model.GameState
import com.emberdeep.game.model.GroundItem
import com.emberdeep.game.model.Item
import com.emberdeep.game.model.ItemKind
import com.emberdeep.game.model.ItemType
import com.emberdeep.game.model.Player
import com.emberdeep.game.sim.RecordingEvents
import com.emberdeep.game.testutil.A
import org.junit.Test

/** Rules the turn engine must guarantee, independent of any rendering. */
class TurnEngineTest {

    private fun engine(
        map: DungeonMap,
        player: Player,
        px: Int,
        py: Int,
        enemies: List<Enemy> = emptyList()
    ): Pair<TurnEngine, GameState> {
        player.x = px
        player.y = py
        player.snapDraw()
        val state = GameState(1, player, map, Rng(4242L))
        state.enemies.addAll(enemies)
        Fov.compute(map, px, py, TurnEngine.FOV_RADIUS)
        return TurnEngine(state, RecordingEvents()) { } to state
    }

    /** 9x9 open chamber surrounded by rock. */
    private fun chamber(): DungeonMap {
        val map = DungeonMap(11, 11)
        for (y in 1..9) for (x in 1..9) map.setTile(x, y, DungeonMap.FLOOR)
        return map
    }

    /** A harmless punching bag: it can barely hurt the hero, so tests are stable. */
    private fun dummy(type: EnemyType, x: Int, y: Int, hp: Int = 999): Enemy =
        Enemy(type, x, y).apply {
            this.hp = hp
            maxHp = hp
            atk = 0
            dmgN = 0
            dmgB = 0
        }

    @Test
    fun `walking into rock is refused and costs no turn`() {
        val (engine, state) = engine(chamber(), Player(ClassType.FIGHTER), 5, 2)
        A.isTrue(engine.tryMove(0, -1), "walking onto open floor must work")
        A.eq(1, state.turn, "a step costs one turn")
        val before = state.turn
        A.isFalse(engine.tryMove(0, -1), "walking into rock must be refused")
        A.isFalse(engine.tryMove(0, -99), "stepping out of the map must be refused")
        A.eq(before, state.turn, "refused moves must not consume a turn")
        A.eq(5, state.player.x, "the hero did not move sideways")
        A.eq(1, state.player.y, "the hero did not move further up")
    }

    @Test
    fun `attacking a wall-phasing monster inside rock leaves no unreachable loot`() {
        val hero = Player(ClassType.ROGUE)
        val map = chamber()
        // (5, 0) is solid rock above the chamber: a wraith can phase in and die there.
        for (attempt in 0 until 25) {
            val wraith = Enemy(EnemyType.WRAITH, 5, 0).apply {
                hp = 1
                maxHp = 1
            }
            val (engine, state) = engine(map, hero, 5, 1, listOf(wraith))
            hero.autoCrit = true   // guarantees the swing lands, so the kill is certain
            engine.tryMove(0, -1)
            A.isTrue(wraith.hp <= 0, "the wraith must die to a guaranteed critical hit")
            for (gi in state.map.groundItems) {
                A.isTrue(
                    state.map.isWalkable(gi.x, gi.y),
                    "loot dropped at (${gi.x}, ${gi.y}) can never be collected"
                )
            }
        }
    }

    @Test
    fun `killing a monster on open floor still drops loot eventually`() {
        var drops = 0
        for (seed in 0 until 40) {
            val hero = Player(ClassType.ROGUE)
            val rat = Enemy(EnemyType.RAT, 5, 4).apply {
                hp = 1
                maxHp = 1
            }
            val map = chamber()
            hero.x = 5
            hero.y = 5
            hero.snapDraw()
            val state = GameState(1, hero, map, Rng(seed.toLong() + 1))
            state.enemies.add(rat)
            Fov.compute(map, 5, 5, TurnEngine.FOV_RADIUS)
            val engine = TurnEngine(state, RecordingEvents()) { }
            hero.autoCrit = true
            engine.tryMove(0, -1)
            if (state.map.groundItems.isNotEmpty()) drops++
        }
        A.isTrue(drops > 5, "monsters should drop gold now and then (dropped $drops of 40)")
    }

    @Test
    fun `a class ability costs its full cooldown`() {
        val hero = Player(ClassType.FIGHTER)
        val foes = listOf(dummy(EnemyType.RAT, 4, 5), dummy(EnemyType.GOBLIN, 6, 5))
        val (engine, state) = engine(chamber(), hero, 5, 5, foes)
        A.isTrue(engine.abilityReady(), "the ability starts ready")
        A.isTrue(engine.useWhirlwind(), "whirlwind with two adjacent enemies must fire")
        val cooldown = ClassType.FIGHTER.abilityCooldown
        A.eq(cooldown, state.player.abilityCd, "the ability must be on full cooldown")
        repeat(cooldown - 1) { engine.waitTurn() }
        A.eq(1, state.player.abilityCd, "cooldown after ${cooldown - 1} turns")
        A.isFalse(engine.abilityReady(), "still recharging")
        engine.waitTurn()
        A.eq(0, state.player.abilityCd, "cooldown after $cooldown turns")
        A.isTrue(engine.abilityReady(), "ready again exactly after the advertised cooldown")
    }

    @Test
    fun `drinking a potion heals, costs a turn and respects the maximum`() {
        val hero = Player(ClassType.FIGHTER)
        hero.hp = 5
        hero.addItem(ItemType.POTION_HEAL)
        val (engine, state) = engine(chamber(), hero, 5, 5)
        val potion = state.player.inventory.first { it.type == ItemType.POTION_HEAL }
        A.isTrue(engine.useItem(potion), "drinking costs a turn")
        A.eq(5 + ItemType.POTION_HEAL.power, state.player.hp, "healed by the potion's power")
        A.isTrue(state.player.inventory.isEmpty(), "the potion is consumed")

        state.player.hp = state.player.maxHp - 3
        state.player.addItem(ItemType.POTION_HEAL)
        val second = state.player.inventory.first { it.type == ItemType.POTION_HEAL }
        engine.useItem(second)
        A.eq(state.player.maxHp, state.player.hp, "healing never exceeds max hp")
    }

    @Test
    fun `picking up treasure respects the pack limit and stays on the floor`() {
        val hero = Player(ClassType.FIGHTER)
        repeat(Player.MAX_SLOTS - 1) { hero.addItem(ItemType.RUSTY_DAGGER) }
        hero.addItem(ItemType.POTION_HEAL)
        A.eq(Player.MAX_SLOTS, hero.inventory.size, "pack is full")

        val map = chamber()
        map.groundItems.add(GroundItem(6, 5, Item(ItemType.SHORT_SWORD)))
        val (engine, state) = engine(map, hero, 5, 5)
        engine.tryMove(1, 0)
        A.eq(6, state.player.x, "the hero walked onto the sword")
        A.eq(1, state.map.groundItems.size, "a full pack must leave the loot on the floor")
        A.isTrue(
            state.log.any { it.text.contains("pack is full") },
            "the player must be told why the loot stayed behind"
        )
    }

    @Test
    fun `descending is only possible while standing on the stairs`() {
        val map = chamber()
        map.stairsX = 8
        map.stairsY = 8
        map.setTile(8, 8, DungeonMap.STAIRS)
        val (engine, state) = engine(map, Player(ClassType.MAGE), 5, 5)
        A.isFalse(engine.descend(), "descending away from the stairs must be refused")
        A.eq(1, state.floor, "the hero is still on floor one")
        state.player.x = 8
        state.player.y = 8
        A.isTrue(engine.descend(), "standing on the stairs must work")
        A.eq(2, state.floor, "the hero descends to the next floor")
        A.isTrue(
            state.map.isWalkable(state.player.x, state.player.y),
            "the hero arrives on walkable ground"
        )
    }

    @Test
    fun `the dead can no longer act`() {
        val hero = Player(ClassType.MAGE)
        hero.hp = 1
        val brute = Enemy(EnemyType.ORC, 5, 4).apply {
            atk = 50
            dmgB = 50
            awake = true
        }
        val (engine, state) = engine(chamber(), hero, 5, 5, listOf(brute))
        engine.waitTurn()
        A.isTrue(state.player.hp <= 0, "the orc should have finished the hero off")
        A.isTrue(engine.gameEnded, "the run is over")
        A.isFalse(engine.tryMove(0, 1), "no more moves after death")
        A.isFalse(engine.waitTurn(), "no more waiting after death")
        A.isFalse(engine.descend(), "no descending after death")
    }

    @Test
    fun `fireball scrolls scorch everything the hero can see`() {
        val hero = Player(ClassType.MAGE)
        val foes = listOf(dummy(EnemyType.GOBLIN, 4, 5), dummy(EnemyType.GOBLIN, 6, 5))
        hero.addItem(ItemType.SCROLL_FIREBALL)
        val (engine, state) = engine(chamber(), hero, 5, 5, foes)
        val scroll = state.player.inventory.first { it.type.kind == ItemKind.SCROLL }
        A.isTrue(engine.useItem(scroll), "reading a scroll costs a turn")
        for (foe in foes) {
            A.isTrue(foe.hp < 999, "${foe.type.display} was not hit by the fireball")
            A.isTrue(foe.burnTurns > 0, "the fireball should set monsters alight")
        }
        A.isTrue(
            state.player.inventory.none { it.type == ItemType.SCROLL_FIREBALL },
            "the scroll is consumed"
        )
    }

    @Test
    fun `levelling up in a fight tops the hero back up`() {
        val hero = Player(ClassType.MAGE)
        hero.hp = 3
        val rat = Enemy(EnemyType.RAT, 5, 4).apply {
            hp = 1
            maxHp = 1
            xp = 200
        }
        val (engine, state) = engine(chamber(), hero, 5, 5, listOf(rat))
        hero.autoCrit = true
        engine.tryMove(0, -1)
        A.isTrue(rat.hp <= 0, "the rat must die")
        A.isTrue(state.player.level > 1, "200 xp must grant a level")
        A.eq(state.player.maxHp, state.player.hp, "a level up fully heals")
        A.isFalse(engine.gameEnded, "the hero survived")
    }
}
