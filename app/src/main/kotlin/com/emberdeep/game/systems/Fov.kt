package com.emberdeep.game.systems

import com.emberdeep.game.model.DungeonMap

/** Recursive shadowcasting field-of-view. */
object Fov {

    private val MULT = arrayOf(
        intArrayOf(1, 0, 0, -1, -1, 0, 0, 1),
        intArrayOf(0, 1, -1, 0, 0, -1, 1, 0),
        intArrayOf(0, 1, 1, 0, 0, -1, -1, 0),
        intArrayOf(1, 0, 0, 1, -1, 0, 0, -1)
    )

    fun compute(map: DungeonMap, px: Int, py: Int, radius: Int) {
        java.util.Arrays.fill(map.visible, false)
        if (!map.inBounds(px, py)) return
        reveal(map, px, py)
        for (oct in 0 until 8) {
            castLight(
                map, px, py, radius, 1, 1f, 0f,
                MULT[0][oct], MULT[1][oct], MULT[2][oct], MULT[3][oct]
            )
        }
    }

    private fun reveal(map: DungeonMap, x: Int, y: Int) {
        if (!map.inBounds(x, y)) return
        val i = map.idx(x, y)
        map.visible[i] = true
        map.explored[i] = true
    }

    private fun castLight(
        map: DungeonMap, cx: Int, cy: Int, radius: Int,
        row: Int, startSlopeIn: Float, endSlope: Float,
        xx: Int, xy: Int, yx: Int, yy: Int
    ) {
        var startSlope = startSlopeIn
        if (startSlope < endSlope) return
        var nextStart = startSlope
        val r2 = radius * radius
        var blocked = false
        var dist = row
        while (dist <= radius && !blocked) {
            val dy = -dist
            for (dx in -dist..0) {
                val curX = cx + dx * xx + dy * xy
                val curY = cy + dx * yx + dy * yy
                val lSlope = (dx - 0.5f) / (dy + 0.5f)
                val rSlope = (dx + 0.5f) / (dy - 0.5f)
                if (rSlope > startSlope) continue
                if (lSlope < endSlope) break
                if (dx * dx + dy * dy <= r2) reveal(map, curX, curY)
                val opaque = !map.inBounds(curX, curY) || map.blocksSight(curX, curY)
                if (blocked) {
                    if (opaque) {
                        nextStart = rSlope
                    } else {
                        blocked = false
                        startSlope = nextStart
                    }
                } else if (opaque && dist < radius) {
                    blocked = true
                    castLight(map, cx, cy, radius, dist + 1, startSlope, lSlope, xx, xy, yx, yy)
                    nextStart = rSlope
                }
            }
            dist++
        }
    }

    /** Simple symmetric line-of-sight test using Bresenham. */
    fun lineOfSight(map: DungeonMap, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        var x = x0
        var y = y0
        val dx = Math.abs(x1 - x0)
        val dy = Math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        while (!(x == x1 && y == y1)) {
            if (!(x == x0 && y == y0) && map.blocksSight(x, y)) return false
            val e2 = 2 * err
            if (e2 > -dy) { err -= dy; x += sx }
            if (e2 < dx) { err += dx; y += sy }
        }
        return true
    }
}
