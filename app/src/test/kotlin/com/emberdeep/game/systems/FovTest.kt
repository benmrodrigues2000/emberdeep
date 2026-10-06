package com.emberdeep.game.systems

import com.emberdeep.game.model.DungeonMap
import com.emberdeep.game.testutil.A
import org.junit.Test

class FovTest {

    /** A 5x5 room floating in solid rock, plus a second room further away. */
    private fun map(): DungeonMap {
        val m = DungeonMap(21, 21)
        for (y in 5..9) for (x in 5..9) m.setTile(x, y, DungeonMap.FLOOR)
        for (y in 5..9) for (x in 15..19) m.setTile(x, y, DungeonMap.FLOOR)
        m.setTile(4, 7, DungeonMap.DOOR)
        return m
    }

    @Test
    fun `the hero can see its own tile and the room around it`() {
        val m = map()
        Fov.compute(m, 7, 7, 3)
        A.isTrue(m.isVisible(7, 7), "hero tile must be visible")
        A.isTrue(m.isVisible(5, 7), "open floor 2 tiles away must be visible")
        A.isTrue(m.isVisible(9, 9), "diagonal floor inside the radius must be visible")
        A.isTrue(m.isVisible(6, 6), "diagonal floor must be visible")
    }

    @Test
    fun `rock hides whatever lies beyond it`() {
        val m = map()
        Fov.compute(m, 7, 7, 6)
        A.isFalse(m.isVisible(3, 7), "tiles beyond the room wall must stay hidden")
        A.isFalse(m.isVisible(1, 1), "far away rock must stay hidden")
        A.isFalse(m.isVisible(15, 7), "a room with no line of sight must stay hidden")
    }

    @Test
    fun `the sight radius is respected`() {
        val m = map()
        Fov.compute(m, 7, 7, 2)
        A.isFalse(m.isVisible(9, 9), "2.83 tiles away is outside a radius of 2")
        A.isFalse(m.isVisible(5, 5), "outside the radius")
        A.isTrue(m.isVisible(8, 7), "1 tile away is inside a radius of 2")
        A.isTrue(m.isVisible(7, 9), "2 tiles away is inside a radius of 2")
        Fov.compute(m, 7, 7, 4)
        A.isTrue(m.isVisible(9, 7), "inside a radius of 4")
    }

    @Test
    fun `everything visible also becomes explored`() {
        val m = map()
        Fov.compute(m, 7, 7, 4)
        for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                if (m.isVisible(x, y)) A.isTrue(m.isExplored(x, y), "visible tile ($x, $y) not explored")
            }
        }
        // Exploration is sticky: looking away does not unforget the map.
        Fov.compute(m, 6, 6, 1)
        A.isTrue(m.isExplored(9, 9), "explored tiles must stay explored")
    }

    @Test
    fun `line of sight is blocked by walls and doors`() {
        val m = map()
        A.isTrue(Fov.lineOfSight(m, 5, 7, 9, 7), "clear row inside one room")
        A.isFalse(Fov.lineOfSight(m, 5, 7, 3, 7), "a door blocks sight")
        A.isFalse(Fov.lineOfSight(m, 5, 7, 15, 7), "solid rock between the rooms")
        A.isTrue(Fov.lineOfSight(m, 7, 7, 7, 7), "a tile can see itself")
    }

    @Test
    fun `computing outside the map is harmless`() {
        val m = map()
        Fov.compute(m, -5, 200, 5)
        A.isFalse(m.isVisible(7, 7), "nothing is revealed from an invalid origin")
    }
}
