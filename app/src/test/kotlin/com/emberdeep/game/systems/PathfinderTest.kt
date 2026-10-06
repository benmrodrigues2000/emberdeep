package com.emberdeep.game.systems

import com.emberdeep.game.core.Rng
import com.emberdeep.game.model.ClassType
import com.emberdeep.game.model.DungeonMap
import com.emberdeep.game.model.Enemy
import com.emberdeep.game.model.EnemyType
import com.emberdeep.game.model.GameState
import com.emberdeep.game.model.Player
import com.emberdeep.game.testutil.A
import org.junit.Test

class PathfinderTest {

    private fun state(map: DungeonMap, px: Int, py: Int): GameState {
        val player = Player(ClassType.ROGUE)
        player.x = px
        player.y = py
        player.snapDraw()
        return GameState(1, player, map, Rng(1L))
    }

    /** 1-tile-wide corridor from x=1 to x=9 on row 5. */
    private fun corridor(): DungeonMap {
        val m = DungeonMap(11, 11)
        for (x in 1..9) m.setTile(x, 5, DungeonMap.FLOOR)
        return m
    }

    /** Open 9x9 chamber from (1,1) to (9,9). */
    private fun chamber(): DungeonMap {
        val m = DungeonMap(11, 11)
        for (y in 1..9) for (x in 1..9) m.setTile(x, y, DungeonMap.FLOOR)
        return m
    }

    @Test
    fun `finds a straight path down a corridor`() {
        val s = state(corridor(), 1, 5)
        val path = Pathfinder.find(s, 1, 5, 9, 5)
        A.eq(8, path.size, "corridor path length")
        A.eq(2, path.first()[0], "first step x")
        A.eq(5, path.first()[1], "first step y")
        A.eq(9, path.last()[0], "last step x")
        var previous = intArrayOf(1, 5)
        for (step in path) {
            A.isTrue(s.map.isWalkable(step[0], step[1]), "path runs through a wall")
            A.eq(1, Pathfinder.dist(previous[0], previous[1], step[0], step[1]), "steps are adjacent")
            previous = step
        }
    }

    @Test
    fun `a monster blocks the player's path but not the monster's own search`() {
        val s = state(corridor(), 1, 5)
        s.enemies.add(Enemy(EnemyType.GOBLIN, 5, 5))
        A.isTrue(Pathfinder.find(s, 1, 5, 9, 5, blockOccupied = true).isEmpty(), "blocked corridor")
        val free = Pathfinder.find(s, 1, 5, 9, 5, blockOccupied = false)
        A.eq(8, free.size, "monsters walk through allies as a last resort")
        A.eq(-1, Pathfinder.nextStep(s, 1, 5, 9, 5, blockOccupied = true), "no step when blocked")
    }

    @Test
    fun `paths route around monsters when there is room`() {
        val s = state(chamber(), 1, 5)
        s.enemies.add(Enemy(EnemyType.ORC, 5, 5))
        val path = Pathfinder.find(s, 1, 5, 9, 5, blockOccupied = true)
        A.isTrue(path.isNotEmpty(), "the chamber has plenty of room to walk around")
        A.isTrue(
            path.none { it[0] == 5 && it[1] == 5 },
            "the path must not walk through the monster"
        )
        A.isTrue(path.size > 8, "a detour must be longer than the straight line")
    }

    @Test
    fun `unreachable and invalid targets are reported as no path`() {
        val m = corridor()
        m.setTile(5, 5, DungeonMap.WALL)  // seal the corridor
        val s = state(m, 1, 5)
        A.isTrue(Pathfinder.find(s, 1, 5, 9, 5).isEmpty(), "sealed corridor has no path")
        val open = state(chamber(), 1, 1)
        A.isTrue(Pathfinder.find(open, 1, 1, 0, 0).isEmpty(), "walls are not pathable")
        A.isTrue(Pathfinder.find(open, 1, 1, 99, 99).isEmpty(), "out of bounds is not pathable")
        A.eq(-1, Pathfinder.nextStep(open, 1, 1, 1, 1), "no step when already there")
    }

    @Test
    fun `unexplored tiles can be excluded from the search`() {
        val s = state(chamber(), 1, 1)
        for (y in 0 until s.map.height) {
            for (x in 0 until s.map.width) s.map.explored[s.map.idx(x, y)] = x <= 4
        }
        A.isTrue(
            Pathfinder.find(s, 1, 1, 9, 9, exploredOnly = true).isEmpty(),
            "the target sits in the dark"
        )
        A.isTrue(
            Pathfinder.find(s, 1, 1, 9, 9, exploredOnly = false).isNotEmpty(),
            "with full knowledge the path exists"
        )
    }

    @Test
    fun `nextStep agrees with the full path`() {
        val s = state(chamber(), 2, 2)
        val path = Pathfinder.find(s, 2, 2, 8, 7)
        A.isTrue(path.isNotEmpty(), "expected a path across the chamber")
        val packed = Pathfinder.nextStep(s, 2, 2, 8, 7)
        A.eq(path.first()[1] * s.map.width + path.first()[0], packed, "packed first step")
    }

    @Test
    fun `searches are deterministic and allocation-free on repeat`() {
        val s = state(chamber(), 1, 1)
        val first = Pathfinder.find(s, 1, 1, 9, 9)
        val second = Pathfinder.find(s, 1, 1, 9, 9)
        A.eq(first.size, second.size, "repeat search size")
        for (i in first.indices) {
            A.eq(first[i][0], second[i][0], "repeat x at $i")
            A.eq(first[i][1], second[i][1], "repeat y at $i")
        }
    }

    /** Tiles reachable on foot from (sx, sy), ignoring monsters. */
    private fun floodFill(map: DungeonMap, sx: Int, sy: Int): BooleanArray {
        val seen = BooleanArray(map.width * map.height)
        val queue = IntArray(map.width * map.height)
        var head = 0
        var tail = 0
        seen[map.idx(sx, sy)] = true
        queue[tail++] = map.idx(sx, sy)
        while (head < tail) {
            val i = queue[head++]
            val x = i % map.width
            val y = i / map.width
            for (d in 0 until 4) {
                val nx = x + DX[d]
                val ny = y + DY[d]
                if (!map.inBounds(nx, ny) || !map.isWalkable(nx, ny)) continue
                val ni = map.idx(nx, ny)
                if (!seen[ni]) {
                    seen[ni] = true
                    queue[tail++] = ni
                }
            }
        }
        return seen
    }

    private companion object {
        val DX = intArrayOf(1, -1, 0, 0)
        val DY = intArrayOf(0, 0, 1, -1)
    }

    @Test
    fun `A star agrees with a flood fill across a whole late floor`() {
        // Regression: the search heap used to drop entries once it filled up,
        // which made distant targets on late, large floors look unreachable.
        val rng = Rng(24601L)
        val data = com.emberdeep.game.gen.DungeonGenerator.generate(9, rng)
        val map = data.map
        val start = state(map, data.startX, data.startY)
        val reachable = floodFill(map, data.startX, data.startY)

        var unreachableTiles = 0
        var sampled = 0
        var mismatches = 0
        var detail = ""
        var index = 0
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                if (!map.isWalkable(x, y)) continue
                val flood = reachable[map.idx(x, y)]
                if (!flood) unreachableTiles++
                // The hero's own tile is trivially "no path needed".
                if (x == data.startX && y == data.startY) continue
                if (index++ % 5 != 0) continue
                sampled++
                val found = Pathfinder.find(start, data.startX, data.startY, x, y).isNotEmpty()
                if (found != flood) {
                    mismatches++
                    detail = "($x, $y) A*=$found floodFill=$flood"
                }
            }
        }
        A.eq(0, unreachableTiles, "the generator must connect every walkable tile")
        A.isTrue(sampled >= 20, "expected to sample a walkable floor (sampled $sampled)")
        A.eq(0, mismatches, "A* disagrees with the flood fill at $detail")
    }

    @Test
    fun `every enemy in a generated dungeon can path to the hero`() {
        val rng = Rng(31337L)
        val data = com.emberdeep.game.gen.DungeonGenerator.generate(6, rng)
        val player = Player(ClassType.FIGHTER)
        player.x = data.startX
        player.y = data.startY
        val s = GameState(6, player, data.map, rng)
        s.enemies.addAll(data.enemies)
        var reached = 0
        for (e in s.enemies) {
            val step = Pathfinder.nextStep(s, e.x, e.y, player.x, player.y)
            if (step >= 0) reached++
        }
        A.eq(s.enemies.size, reached, "every monster must be able to reach the hero")
    }
}
