package com.emberdeep.game.systems

import com.emberdeep.game.model.DungeonMap
import com.emberdeep.game.model.GameState

/**
 * Grid A* pathfinding (4-directional).
 *
 * Two properties matter for a turn-based mobile roguelike:
 *
 *  * **no allocation while playing** — the search runs on grow-only scratch
 *    buffers reused between calls, so a turn in which a dozen monsters each
 *    run a search produces no garbage for the collector to sweep;
 *  * **monster-aware costs** — the player auto-walks *around* monsters, while
 *    monsters treat tiles held by allies as expensive instead of impassable,
 *    so packs spread out and take detours rather than jamming corridors.
 *
 * The object is stateful and therefore single-threaded: it is only used from
 * the game loop thread (plus unit tests).
 */
object Pathfinder {

    /** Extra cost for stepping onto a tile occupied by another monster. */
    const val ALLY_TILE_COST = 6

    private val DIR_X = intArrayOf(1, -1, 0, 0)
    private val DIR_Y = intArrayOf(0, 0, 1, -1)

    val DIRS = arrayOf(
        intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1)
    )

    // --- Scratch workspace (grown on demand, never shrunk) --------------- //

    private var width = 0
    private var height = 0
    private var gScore = IntArray(0)
    private var cameFrom = IntArray(0)
    private var visitStamp = IntArray(0)
    private var closedStamp = IntArray(0)
    private var occupancy = IntArray(0)
    private var generation = 0
    private var occupancyGeneration = 0

    private var heapNode = IntArray(0)
    private var heapCost = IntArray(0)
    private var heapG = IntArray(0)
    private var heapSize = 0
    private var poppedG = 0
    private var poppedNode = -1

    // --- Public API ------------------------------------------------------ //

    /**
     * Full path from (sx, sy) to (tx, ty), excluding the start tile and
     * including the target, or an empty list when unreachable.
     */
    fun find(
        state: GameState, sx: Int, sy: Int, tx: Int, ty: Int,
        blockOccupied: Boolean = true, exploredOnly: Boolean = false
    ): List<IntArray> {
        val map = state.map
        val goal = search(state, sx, sy, tx, ty, blockOccupied, exploredOnly) ?: return emptyList()
        if (goal == map.idx(sx, sy)) return emptyList()
        return reconstruct(map, goal)
    }

    /**
     * Just the first step of the path, packed as `y * width + x`, or -1 when
     * there is no route. This is the allocation-free entry point used by
     * monster AI (one call per monster per turn).
     */
    fun nextStep(
        state: GameState, sx: Int, sy: Int, tx: Int, ty: Int,
        blockOccupied: Boolean = false, exploredOnly: Boolean = false
    ): Int {
        val map = state.map
        val goal = search(state, sx, sy, tx, ty, blockOccupied, exploredOnly) ?: return -1
        val start = map.idx(sx, sy)
        if (goal == start) return -1
        var node = goal
        while (true) {
            val parent = cameFrom[node]
            if (parent == start) return node
            if (parent < 0) return -1
            node = parent
        }
    }

    /** Greedy fallback step toward a target (used by wall-phasing enemies). */
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

    // --- Search ---------------------------------------------------------- //

    /** @return the goal tile index when reached, otherwise null. */
    private fun search(
        state: GameState, sx: Int, sy: Int, tx: Int, ty: Int,
        blockOccupied: Boolean, exploredOnly: Boolean
    ): Int? {
        val map = state.map
        if (!map.isWalkable(tx, ty)) return null
        ensureCapacity(map.width, map.height)
        fillOccupancy(state)
        generation++

        val start = sy * width + sx
        val goal = ty * width + tx
        if (start == goal) return start

        heapSize = 0
        gScore[start] = 0
        visitStamp[start] = generation
        cameFrom[start] = -1
        heapPush(start, heuristic(sx, sy, tx, ty), 0)

        var expansions = 0
        val maxExpansions = width * height * 2
        while (heapSize > 0 && expansions < maxExpansions) {
            val node = heapPop()
            if (closedStamp[node] == generation) continue
            // Lazy deletion: skip entries that were superseded by a cheaper one.
            if (poppedG > gScore[node]) continue
            closedStamp[node] = generation
            expansions++
            if (node == goal) return goal

            val x = node % width
            val y = node / width
            val baseG = gScore[node]
            for (d in 0 until 4) {
                val nx = x + DIR_X[d]
                val ny = y + DIR_Y[d]
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                if (!map.isWalkable(nx, ny)) continue
                if (exploredOnly && !map.isExplored(nx, ny)) continue
                val ni = ny * width + nx
                if (closedStamp[ni] == generation) continue
                val isGoal = ni == goal
                val taken = occupancy[ni] == occupancyGeneration
                if (taken && !isGoal && blockOccupied) continue
                val step = if (taken && !isGoal) ALLY_TILE_COST else 1
                val tentative = baseG + step
                if (visitStamp[ni] == generation && tentative >= gScore[ni]) continue
                visitStamp[ni] = generation
                gScore[ni] = tentative
                cameFrom[ni] = node
                heapPush(ni, tentative + heuristic(nx, ny, tx, ty), tentative)
            }
        }
        return null
    }

    private fun reconstruct(map: DungeonMap, goal: Int): List<IntArray> {
        val out = ArrayList<IntArray>(12)
        var node = goal
        while (true) {
            val parent = cameFrom[node]
            if (parent < 0) break
            out.add(intArrayOf(node % map.width, node / map.width))
            node = parent
        }
        out.reverse()
        return out
    }

    private fun heuristic(x: Int, y: Int, tx: Int, ty: Int): Int = dist(x, y, tx, ty)

    private fun fillOccupancy(state: GameState) {
        occupancyGeneration++
        for (e in state.enemies) {
            if (e.hp <= 0) continue
            if (e.x < 0 || e.y < 0 || e.x >= width || e.y >= height) continue
            occupancy[e.y * width + e.x] = occupancyGeneration
        }
    }

    private fun ensureCapacity(w: Int, h: Int) {
        width = w
        height = h
        val n = w * h
        if (n <= 0) return
        if (gScore.size >= n) return
        gScore = IntArray(n)
        cameFrom = IntArray(n) { -1 }
        visitStamp = IntArray(n)
        closedStamp = IntArray(n)
        occupancy = IntArray(n)
        heapNode = IntArray(n + 1)
        heapCost = IntArray(n + 1)
        heapG = IntArray(n + 1)
        heapSize = 0
    }

    // --- Binary min-heap over tile indices ------------------------------- //

    private fun heapPush(node: Int, cost: Int, g: Int) {
        // Lazy deletion means duplicates can exceed the node count, so the
        // heap grows on demand rather than dropping entries (dropping one
        // would make a reachable target look unreachable).
        if (heapSize >= heapNode.size) growHeap()
        var i = heapSize++
        heapNode[i] = node
        heapCost[i] = cost
        heapG[i] = g
        while (i > 0) {
            val parent = (i - 1) / 2
            if (heapCost[parent] <= heapCost[i]) break
            heapSwap(i, parent)
            i = parent
        }
    }

    private fun heapPop(): Int {
        poppedNode = heapNode[0]
        poppedG = heapG[0]
        heapSize--
        if (heapSize > 0) {
            heapNode[0] = heapNode[heapSize]
            heapCost[0] = heapCost[heapSize]
            heapG[0] = heapG[heapSize]
            var i = 0
            while (true) {
                val left = 2 * i + 1
                val right = left + 1
                var smallest = i
                if (left < heapSize && heapCost[left] < heapCost[smallest]) smallest = left
                if (right < heapSize && heapCost[right] < heapCost[smallest]) smallest = right
                if (smallest == i) break
                heapSwap(i, smallest)
                i = smallest
            }
        }
        return poppedNode
    }

    private fun growHeap() {
        val capacity = (heapNode.size * 2).coerceAtLeast(64)
        heapNode = heapNode.copyOf(capacity)
        heapCost = heapCost.copyOf(capacity)
        heapG = heapG.copyOf(capacity)
    }

    private fun heapSwap(a: Int, b: Int) {
        val n = heapNode[a]; heapNode[a] = heapNode[b]; heapNode[b] = n
        val c = heapCost[a]; heapCost[a] = heapCost[b]; heapCost[b] = c
        val g = heapG[a]; heapG[a] = heapG[b]; heapG[b] = g
    }
}
