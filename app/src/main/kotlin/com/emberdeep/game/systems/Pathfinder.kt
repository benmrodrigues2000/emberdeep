package com.emberdeep.game.systems

import com.emberdeep.game.model.DungeonMap
import com.emberdeep.game.model.GameState
import java.util.PriorityQueue

/** Grid A* pathfinding (4-directional) with a node budget. */
object Pathfinder {

    private const val MAX_EXPANSIONS = 2500

    private class Node(val x: Int, val y: Int, val g: Int, val f: Int, val parent: Node?)

    /**
     * Find a path from (sx, sy) to (tx, ty). Returns the list of steps
     * excluding the start tile, or empty when unreachable.
     * Occupied tiles block the path except for the target itself.
     */
    fun find(
        state: GameState, sx: Int, sy: Int, tx: Int, ty: Int,
        ignoreOccupied: Boolean = false, exploredOnly: Boolean = true
    ): List<IntArray> {
        val map = state.map
        if (!map.inBounds(tx, ty) || !map.isWalkable(tx, ty)) return emptyList()
        if (sx == tx && sy == ty) return emptyList()

        val open = PriorityQueue<Node>(64, compareBy { it.f })
        val bestG = HashMap<Int, Int>(256)
        open.add(Node(sx, sy, 0, dist(sx, sy, tx, ty), null))
        bestG[map.idx(sx, sy)] = 0

        var expansions = 0
        while (open.isNotEmpty() && expansions < MAX_EXPANSIONS) {
            val cur = open.poll() ?: break
            expansions++
            if (cur.x == tx && cur.y == ty) {
                val path = ArrayList<IntArray>()
                var n: Node? = cur
                while (n != null && n.parent != null) {
                    path.add(intArrayOf(n.x, n.y))
                    n = n.parent
                }
                path.reverse()
                return path
            }
            for (d in DIRS) {
                val nx = cur.x + d[0]
                val ny = cur.y + d[1]
                if (!map.inBounds(nx, ny) || !map.isWalkable(nx, ny)) continue
                if (exploredOnly && !map.isExplored(nx, ny)) continue
                val isTarget = nx == tx && ny == ty
                if (!ignoreOccupied && !isTarget && state.enemyAt(nx, ny) != null) continue
                val g = cur.g + 1
                val key = map.idx(nx, ny)
                val prev = bestG[key]
                if (prev != null && prev <= g) continue
                bestG[key] = g
                open.add(Node(nx, ny, g, g + dist(nx, ny, tx, ty), cur))
            }
        }
        return emptyList()
    }

    /** Greedy fallback step toward a target (used by phasing enemies). */
    fun greedyStep(sx: Int, sy: Int, tx: Int, ty: Int): IntArray {
        val dx = Integer.signum(tx - sx)
        val dy = Integer.signum(ty - sy)
        return if (Math.abs(tx - sx) >= Math.abs(ty - sy)) {
            intArrayOf(sx + dx, sy)
        } else {
            intArrayOf(sx, sy + dy)
        }
    }

    fun dist(x0: Int, y0: Int, x1: Int, y1: Int): Int =
        Math.abs(x1 - x0) + Math.abs(y1 - y0)

    /** Chebyshev distance — used for adjacency incl. diagonals. */
    fun chebyshev(x0: Int, y0: Int, x1: Int, y1: Int): Int =
        maxOf(Math.abs(x1 - x0), Math.abs(y1 - y0))

    val DIRS = arrayOf(
        intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1)
    )
}
