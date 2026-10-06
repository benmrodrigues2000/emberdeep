package com.emberdeep.game.gen

import com.emberdeep.game.core.Rng
import com.emberdeep.game.model.DungeonMap
import com.emberdeep.game.model.Enemy
import com.emberdeep.game.model.EnemyType
import com.emberdeep.game.model.GroundItem
import com.emberdeep.game.model.Item
import com.emberdeep.game.model.ItemKind
import com.emberdeep.game.model.ItemType
import com.emberdeep.game.model.Room

/** Result of generating one floor. */
class FloorData(
    val map: DungeonMap,
    val enemies: List<Enemy>,
    val startX: Int,
    val startY: Int
)

object DungeonGenerator {

    const val FINAL_FLOOR = 10

    /** Upper bound on connectivity repairs per floor (normally zero). */
    private const val MAX_REPAIRS = 8

    private val NEIGHBOUR_X = intArrayOf(1, -1, 0, 0)
    private val NEIGHBOUR_Y = intArrayOf(0, 0, 1, -1)

    fun generate(floor: Int, rng: Rng): FloorData =
        if (floor >= FINAL_FLOOR) generateBossFloor(floor, rng)
        else generateStandard(floor, rng)

    // ------------------------------------------------------------------ //

    private fun generateStandard(floor: Int, rng: Rng): FloorData {
        val size = (34 + floor * 2).coerceAtMost(46)
        val map = DungeonMap(size, size)

        carveRooms(map, rng, roomTarget = 7 + floor / 2)
        decorate(map, rng, floor)

        val startRoom = map.rooms.first()
        // Rooms are chained as they are carved, but the geometry is only a
        // promise until it is proven: walk the map and repair any orphan.
        ensureConnectivity(map, rng, startRoom.cx, startRoom.cy)
        val endRoom = map.rooms.last()
        map.stairsX = endRoom.cx
        map.stairsY = endRoom.cy
        map.setTile(map.stairsX, map.stairsY, DungeonMap.STAIRS)

        val enemies = spawnEnemies(map, rng, floor, startRoom)
        spawnLoot(map, rng, floor, startRoom, enemies)

        return FloorData(map, enemies, startRoom.cx, startRoom.cy)
    }

    private fun generateBossFloor(floor: Int, rng: Rng): FloorData {
        val w = 25
        val h = 33
        val map = DungeonMap(w, h)

        // Entry chamber at the bottom, grand hall above, connected by a throat.
        val entry = Room(w / 2 - 3, h - 8, 7, 5)
        val hall = Room(3, 3, w - 6, h - 15)
        carveRoom(map, entry)
        carveRoom(map, hall)
        for (y in hall.y + hall.h until entry.y + 1) {
            map.setTile(w / 2 - 1, y, DungeonMap.FLOOR)
            map.setTile(w / 2, y, DungeonMap.FLOOR)
            map.setTile(w / 2 + 1, y, DungeonMap.FLOOR)
        }
        map.rooms.add(entry)
        map.rooms.add(hall)
        decorate(map, rng, floor)

        val enemies = ArrayList<Enemy>()
        val dragon = Enemy(EnemyType.EMBER_DRAGON, hall.cx, hall.y + 4)
        enemies.add(dragon)
        // The dragon's honor guard.
        for (i in 0 until 2) {
            val ex = hall.x + 2 + rng.nextInt(hall.w - 4)
            val ey = hall.cy + rng.nextInt(3)
            if (map.isWalkable(ex, ey) && enemies.none { it.x == ex && it.y == ey }) {
                val c = Enemy(EnemyType.CULTIST, ex, ey)
                c.scaleToFloor(floor)
                enemies.add(c)
            }
        }

        // Treasure around the hall edges.
        dropAt(map, hall.x + 1, hall.y + 1, Item(ItemType.POTION_GREATER_HEAL))
        dropAt(map, hall.x + hall.w - 2, hall.y + 1, Item(ItemType.SCROLL_FIREBALL))
        dropGold(map, hall.x + 1, hall.y + hall.h - 2, 40 + rng.nextInt(40))

        return FloorData(map, enemies, entry.cx, entry.cy + 1)
    }

    // ------------------------------------------------------------------ //

    private fun carveRooms(map: DungeonMap, rng: Rng, roomTarget: Int) {
        var attempts = 0
        while (map.rooms.size < roomTarget && attempts < 220) {
            attempts++
            val rw = rng.range(4, 9)
            val rh = rng.range(4, 8)
            val rx = rng.range(1, map.width - rw - 2)
            val ry = rng.range(1, map.height - rh - 2)
            val room = Room(rx, ry, rw, rh)
            if (map.rooms.any { it.intersects(room) }) continue
            carveRoom(map, room)
            if (map.rooms.isNotEmpty()) {
                connect(map, rng, map.rooms.last(), room)
            }
            map.rooms.add(room)
        }
        // Safety: an unconnectable map should never happen, but guarantee
        // at least two rooms exist.
        if (map.rooms.size < 2) {
            val a = Room(2, 2, 6, 6)
            val b = Room(map.width - 9, map.height - 9, 6, 6)
            carveRoom(map, a); carveRoom(map, b)
            connect(map, rng, a, b)
            map.rooms.add(a); map.rooms.add(b)
        }
    }

    /**
     * Guarantees every room can be walked to from the entrance.
     *
     * Chained room carving should already connect them, but a rare layout (or
     * the emergency fallback in [carveRooms]) can leave one isolated. Rather
     * than trusting the geometry, the generator proves it with a flood fill
     * and carves a corridor to the nearest reached room when needed.
     */
    private fun ensureConnectivity(map: DungeonMap, rng: Rng, startX: Int, startY: Int) {
        for (attempt in 0 until MAX_REPAIRS) {
            val reached = floodFill(map, startX, startY)
            val orphan = map.rooms.firstOrNull { !reached[map.idx(it.cx, it.cy)] } ?: return
            val anchor = map.rooms
                .filter { reached[map.idx(it.cx, it.cy)] }
                .minByOrNull { manhattan(it.cx, it.cy, orphan.cx, orphan.cy) } ?: return
            connect(map, rng, anchor, orphan)
        }
    }

    /** @return the tiles reachable on foot from (sx, sy). */
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
                val nx = x + NEIGHBOUR_X[d]
                val ny = y + NEIGHBOUR_Y[d]
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

    private fun manhattan(x0: Int, y0: Int, x1: Int, y1: Int): Int =
        Math.abs(x1 - x0) + Math.abs(y1 - y0)

    private fun carveRoom(map: DungeonMap, room: Room) {
        for (yy in room.y until room.y + room.h) {
            for (xx in room.x until room.x + room.w) {
                map.setTile(xx, yy, DungeonMap.FLOOR)
            }
        }
    }

    private fun connect(map: DungeonMap, rng: Rng, a: Room, b: Room) {
        var x = a.cx
        var y = a.cy
        val horizontalFirst = rng.chance(0.5f)
        if (horizontalFirst) {
            while (x != b.cx) { x += if (b.cx > x) 1 else -1; carveCorridor(map, x, y) }
            while (y != b.cy) { y += if (b.cy > y) 1 else -1; carveCorridor(map, x, y) }
        } else {
            while (y != b.cy) { y += if (b.cy > y) 1 else -1; carveCorridor(map, x, y) }
            while (x != b.cx) { x += if (b.cx > x) 1 else -1; carveCorridor(map, x, y) }
        }
    }

    private fun carveCorridor(map: DungeonMap, x: Int, y: Int) {
        if (map.tile(x, y) == DungeonMap.WALL) map.setTile(x, y, DungeonMap.FLOOR)
    }

    private fun decorate(map: DungeonMap, rng: Rng, floor: Int) {
        for (i in map.tiles.indices) {
            map.variant[i] = rng.nextInt(4).toByte()
            if (map.tiles[i] == DungeonMap.FLOOR && rng.chance(0.012f + floor * 0.002f)) {
                map.tiles[i] = DungeonMap.VENT
            }
        }
        // Doors where corridors meet room walls.
        for (room in map.rooms) {
            addDoors(map, rng, room)
        }
    }

    private fun addDoors(map: DungeonMap, rng: Rng, room: Room) {
        for (xx in room.x until room.x + room.w) {
            maybeDoor(map, rng, xx, room.y - 1, horizontal = true)
            maybeDoor(map, rng, xx, room.y + room.h, horizontal = true)
        }
        for (yy in room.y until room.y + room.h) {
            maybeDoor(map, rng, room.x - 1, yy, horizontal = false)
            maybeDoor(map, rng, room.x + room.w, yy, horizontal = false)
        }
    }

    private fun maybeDoor(map: DungeonMap, rng: Rng, x: Int, y: Int, horizontal: Boolean) {
        if (map.tile(x, y) != DungeonMap.FLOOR) return
        val sideWalls = if (horizontal) {
            map.tile(x - 1, y) == DungeonMap.WALL && map.tile(x + 1, y) == DungeonMap.WALL
        } else {
            map.tile(x, y - 1) == DungeonMap.WALL && map.tile(x, y + 1) == DungeonMap.WALL
        }
        if (sideWalls && rng.chance(0.55f)) map.setTile(x, y, DungeonMap.DOOR)
    }

    // ------------------------------------------------------------------ //

    private fun spawnEnemies(
        map: DungeonMap, rng: Rng, floor: Int, startRoom: Room
    ): ArrayList<Enemy> {
        val enemies = ArrayList<Enemy>()
        val pool = EnemyType.poolFor(floor)
        if (pool.isEmpty()) return enemies
        val totalWeight = pool.sumOf { it.weight }
        val count = 4 + (floor * 2) / 3 + rng.nextInt(3)

        var attempts = 0
        while (enemies.size < count && attempts < 300) {
            attempts++
            val room = rng.pick(map.rooms)
            if (room === startRoom) continue
            val ex = rng.range(room.x, room.x + room.w - 1)
            val ey = rng.range(room.y, room.y + room.h - 1)
            if (!map.isWalkable(ex, ey)) continue
            if (enemies.any { it.x == ex && it.y == ey }) continue

            var roll = rng.nextInt(totalWeight)
            var chosen = pool.first()
            for (t in pool) {
                roll -= t.weight
                if (roll < 0) { chosen = t; break }
            }
            val e = Enemy(chosen, ex, ey)
            e.scaleToFloor(floor)
            enemies.add(e)
        }
        return enemies
    }

    private fun spawnLoot(
        map: DungeonMap, rng: Rng, floor: Int, startRoom: Room, enemies: List<Enemy>
    ) {
        val drops = 3 + rng.nextInt(3)
        var attempts = 0
        var placed = 0
        while (placed < drops && attempts < 200) {
            attempts++
            val room = rng.pick(map.rooms)
            if (room === startRoom && rng.chance(0.7f)) continue
            val ix = rng.range(room.x, room.x + room.w - 1)
            val iy = rng.range(room.y, room.y + room.h - 1)
            if (!map.isWalkable(ix, iy)) continue
            if (map.itemAt(ix, iy) != null) continue
            if (ix == map.stairsX && iy == map.stairsY) continue
            if (enemies.any { it.x == ix && it.y == iy }) continue

            when (rng.nextInt(10)) {
                in 0..2 -> dropGold(map, ix, iy, 5 + floor * 3 + rng.nextInt(10 + floor * 2))
                in 3..6 -> dropAt(map, ix, iy, Item(rollConsumable(rng, floor)))
                7, 8 -> dropAt(map, ix, iy, Item(rollGear(rng, floor, ItemKind.WEAPON)))
                else -> dropAt(map, ix, iy, Item(rollGear(rng, floor, ItemKind.ARMOR)))
            }
            placed++
        }
        // Guarantee healing per floor so runs stay fair (two from floor 3).
        val healSpots = if (floor >= 3) 2 else 1
        for (i in 0 until healSpots) {
            val room = map.rooms[(map.rooms.size * (i + 1)) / (healSpots + 1)]
            val hx = room.cx + i
            if (map.isWalkable(hx, room.cy) && map.itemAt(hx, room.cy) == null &&
                !(hx == map.stairsX && room.cy == map.stairsY)
            ) {
                val potion = if (floor >= 5 && i == 0) ItemType.POTION_GREATER_HEAL
                else ItemType.POTION_HEAL
                dropAt(map, hx, room.cy, Item(potion))
            }
        }
    }

    fun rollConsumable(rng: Rng, floor: Int): ItemType {
        val options = ItemType.entries.filter {
            (it.kind == ItemKind.POTION || it.kind == ItemKind.SCROLL) && floor >= it.minFloor
        }
        // Healing is more common than utility.
        return if (rng.chance(0.45f)) {
            if (floor >= ItemType.POTION_GREATER_HEAL.minFloor && rng.chance(0.4f)) {
                ItemType.POTION_GREATER_HEAL
            } else ItemType.POTION_HEAL
        } else rng.pick(options)
    }

    fun rollGear(rng: Rng, floor: Int, kind: ItemKind): ItemType {
        val options = ItemType.entries.filter { it.kind == kind && floor >= it.minFloor }
        // Bias towards the best tier currently available.
        val best = options.maxOf { it.tier }
        return if (rng.chance(0.5f)) {
            options.first { it.tier == best }
        } else rng.pick(options)
    }

    private fun dropAt(map: DungeonMap, x: Int, y: Int, item: Item) {
        if (map.isWalkable(x, y) && map.itemAt(x, y) == null) {
            map.groundItems.add(GroundItem(x, y, item))
        }
    }

    private fun dropGold(map: DungeonMap, x: Int, y: Int, amount: Int) {
        if (map.isWalkable(x, y) && map.itemAt(x, y) == null) {
            map.groundItems.add(GroundItem(x, y, Item(ItemType.GOLD_PILE), amount))
        }
    }
}
