package com.emberdeep.game.gen

import com.emberdeep.game.core.Rng
import com.emberdeep.game.model.DungeonMap
import com.emberdeep.game.model.Enemy
import com.emberdeep.game.model.EnemyType
import com.emberdeep.game.model.ItemKind
import com.emberdeep.game.testutil.A
import org.junit.Test

class DungeonGeneratorTest {

    /** Tiles reachable on foot from (sx, sy), ignoring monsters. */
    private fun reachable(map: DungeonMap, sx: Int, sy: Int): BooleanArray {
        val seen = BooleanArray(map.width * map.height)
        val queue = IntArray(map.width * map.height)
        var head = 0
        var tail = 0
        val start = map.idx(sx, sy)
        seen[start] = true
        queue[tail++] = start
        while (head < tail) {
            val i = queue[head++]
            val x = i % map.width
            val y = i / map.width
            for (d in 0 until 4) {
                val nx = x + DIR_X[d]
                val ny = y + DIR_Y[d]
                if (!map.inBounds(nx, ny) || !map.isWalkable(nx, ny)) continue
                val ni = map.idx(nx, ny)
                if (seen[ni]) continue
                seen[ni] = true
                queue[tail++] = ni
            }
        }
        return seen
    }

    @Test
    fun `every floor of the dungeon is fully connected and populated`() {
        for (floor in 1..DungeonGenerator.FINAL_FLOOR) {
            for (seed in 1L..6L) {
                val data = DungeonGenerator.generate(floor, Rng(seed * 1000 + floor))
                val map = data.map
                val where = "floor $floor seed $seed"
                val seen = reachable(map, data.startX, data.startY)

                A.isTrue(map.isWalkable(data.startX, data.startY), "$where: start is walkable")
                A.isTrue(map.rooms.isNotEmpty(), "$where: has rooms")
                A.isTrue(seen[map.idx(data.startX, data.startY)], "$where: start reachable")

                if (floor < DungeonGenerator.FINAL_FLOOR) {
                    A.isTrue(map.isWalkable(map.stairsX, map.stairsY), "$where: stairs walkable")
                    A.isTrue(
                        seen[map.idx(map.stairsX, map.stairsY)],
                        "$where: stairs must be reachable from the entrance"
                    )
                }

                for (e in data.enemies) {
                    A.isTrue(map.inBounds(e.x, e.y), "$where: monster out of bounds")
                    A.isTrue(map.isWalkable(e.x, e.y), "$where: monster inside a wall")
                    A.isTrue(seen[map.idx(e.x, e.y)], "$where: monster sealed off")
                    A.isTrue(e.hp > 0 && e.hp == e.maxHp, "$where: monster starts damaged")
                    A.isFalse(
                        e.x == data.startX && e.y == data.startY,
                        "$where: monster spawned on the hero"
                    )
                }

                for (gi in map.groundItems) {
                    A.isTrue(map.isWalkable(gi.x, gi.y), "$where: loot inside a wall")
                    A.isTrue(seen[map.idx(gi.x, gi.y)], "$where: loot sealed off")
                    A.eq(
                        1,
                        map.groundItems.count { it.x == gi.x && it.y == gi.y },
                        "$where: two items stacked on one tile"
                    )
                    if (gi.item.type.kind == ItemKind.GOLD) {
                        A.isTrue(gi.gold > 0, "$where: empty gold pile")
                    }
                }

                if (floor == 1) {
                    A.isTrue(data.enemies.isNotEmpty(), "$where: no monsters")
                    A.isTrue(
                        data.enemies.all { it.hp <= 12 },
                        "$where: floor one must only hold weak monsters"
                    )
                }
            }
        }
    }

    @Test
    fun `monsters always match the floor they spawn on`() {
        for (floor in 1..9) {
            val pool = EnemyType.poolFor(floor)
            A.isTrue(pool.isNotEmpty(), "floor $floor has an empty monster pool")
            for (type in pool) {
                A.isTrue(floor >= type.minFloor, "${type.display} spawns too early")
                A.isTrue(floor <= type.maxFloor, "${type.display} spawns too late")
            }
        }
    }

    @Test
    fun `deeper floors scale up`() {
        val shallow = DungeonGenerator.generate(1, Rng(5))
        val deep = DungeonGenerator.generate(9, Rng(5))
        A.isTrue(deep.map.width >= shallow.map.width, "deeper dungeons must not shrink")
        A.isTrue(deep.enemies.size >= shallow.enemies.size, "deeper floors hold more monsters")

        val scaled = Enemy(EnemyType.RAT, 0, 0)
        scaled.scaleToFloor(9)
        A.isTrue(scaled.maxHp > EnemyType.RAT.hp, "depth must add hit points")
        A.isTrue(scaled.xp > EnemyType.RAT.xp, "depth must add experience value")
        A.isTrue(scaled.atk >= EnemyType.RAT.atk, "depth must not weaken monsters")
    }

    @Test
    fun `the final floor is a hand built arena with exactly one dragon`() {
        for (seed in 1L..4L) {
            val data = DungeonGenerator.generate(DungeonGenerator.FINAL_FLOOR, Rng(seed))
            val where = "boss floor seed $seed"
            val dragons = data.enemies.filter { it.type == EnemyType.EMBER_DRAGON }
            A.eq(1, dragons.size, "$where: exactly one dragon")
            A.isTrue(data.enemies.size >= 2, "$where: the dragon keeps a guard")
            val seen = reachable(data.map, data.startX, data.startY)
            for (e in data.enemies) {
                A.isTrue(seen[data.map.idx(e.x, e.y)], "$where: a monster is sealed off")
            }
        }
    }

    @Test
    fun `generation is deterministic for a given seed`() {
        val a = DungeonGenerator.generate(4, Rng(2026))
        val b = DungeonGenerator.generate(4, Rng(2026))
        A.eq(a.map.width, b.map.width, "width")
        A.eq(a.startX, b.startX, "start x")
        A.eq(a.startY, b.startY, "start y")
        A.eq(a.map.stairsX, b.map.stairsX, "stairs x")
        A.eq(a.map.stairsY, b.map.stairsY, "stairs y")
        A.eq(a.enemies.size, b.enemies.size, "monster count")
        for (i in a.map.tiles.indices) {
            if (a.map.tiles[i] != b.map.tiles[i]) {
                org.junit.Assert.fail("tile $i differs between identical seeds")
            }
        }
        for (i in a.enemies.indices) {
            A.eqAny(a.enemies[i].type, b.enemies[i].type, "$i monster type")
            A.eq(a.enemies[i].x, b.enemies[i].x, "$i monster x")
            A.eq(a.enemies[i].y, b.enemies[i].y, "$i monster y")
        }
    }

    private companion object {
        val DIR_X = intArrayOf(1, -1, 0, 0)
        val DIR_Y = intArrayOf(0, 0, 1, -1)
    }
}
