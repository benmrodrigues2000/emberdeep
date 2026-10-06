package com.emberdeep.game.model

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class Room(val x: Int, val y: Int, val w: Int, val h: Int) {
    val cx: Int get() = x + w / 2
    val cy: Int get() = y + h / 2
    fun intersects(o: Room, gap: Int = 1): Boolean =
        x - gap < o.x + o.w && x + w + gap > o.x &&
            y - gap < o.y + o.h && y + h + gap > o.y
    fun contains(px: Int, py: Int): Boolean =
        px >= x && px < x + w && py >= y && py < y + h
}

class DungeonMap(val width: Int, val height: Int) {

    val tiles = ByteArray(width * height) { WALL }
    val variant = ByteArray(width * height)
    val visible = BooleanArray(width * height)
    val explored = BooleanArray(width * height)
    val rooms = ArrayList<Room>()
    val groundItems = ArrayList<GroundItem>()

    var stairsX = -1
    var stairsY = -1

    fun idx(x: Int, y: Int): Int = y * width + x

    fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    fun tile(x: Int, y: Int): Byte = if (inBounds(x, y)) tiles[idx(x, y)] else WALL

    fun setTile(x: Int, y: Int, t: Byte) {
        if (inBounds(x, y)) tiles[idx(x, y)] = t
    }

    fun isWalkable(x: Int, y: Int): Boolean {
        val t = tile(x, y)
        return t == FLOOR || t == DOOR || t == STAIRS || t == VENT
    }

    fun blocksSight(x: Int, y: Int): Boolean {
        val t = tile(x, y)
        return t == WALL || t == DOOR
    }

    fun isVisible(x: Int, y: Int): Boolean = inBounds(x, y) && visible[idx(x, y)]

    fun isExplored(x: Int, y: Int): Boolean = inBounds(x, y) && explored[idx(x, y)]

    fun itemAt(x: Int, y: Int): GroundItem? =
        groundItems.firstOrNull { it.x == x && it.y == y }

    fun toJson(): JSONObject = JSONObject().apply {
        put("w", width); put("h", height)
        put("tiles", Base64.encodeToString(tiles, Base64.NO_WRAP))
        put("var", Base64.encodeToString(variant, Base64.NO_WRAP))
        put("explored", Base64.encodeToString(packBooleans(explored), Base64.NO_WRAP))
        put("sx", stairsX); put("sy", stairsY)
        val items = JSONArray()
        for (gi in groundItems) {
            items.put(JSONObject().apply {
                put("x", gi.x); put("y", gi.y)
                put("t", gi.item.type.name); put("c", gi.item.count)
                put("g", gi.gold)
            })
        }
        put("items", items)
    }

    companion object {
        const val WALL: Byte = 0
        const val FLOOR: Byte = 1
        const val DOOR: Byte = 2
        const val STAIRS: Byte = 3
        const val VENT: Byte = 4

        fun fromJson(o: JSONObject): DungeonMap {
            val m = DungeonMap(o.getInt("w"), o.getInt("h"))
            val t = Base64.decode(o.getString("tiles"), Base64.NO_WRAP)
            t.copyInto(m.tiles, 0, 0, minOf(t.size, m.tiles.size))
            val v = Base64.decode(o.optString("var", ""), Base64.NO_WRAP)
            if (v.isNotEmpty()) v.copyInto(m.variant, 0, 0, minOf(v.size, m.variant.size))
            unpackBooleans(
                Base64.decode(o.optString("explored", ""), Base64.NO_WRAP), m.explored
            )
            m.stairsX = o.optInt("sx", -1)
            m.stairsY = o.optInt("sy", -1)
            val items = o.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val io = items.optJSONObject(i) ?: continue
                    val type = ItemType.byName(io.optString("t")) ?: continue
                    m.groundItems.add(
                        GroundItem(
                            io.optInt("x"), io.optInt("y"),
                            Item(type, io.optInt("c", 1)), io.optInt("g", 0)
                        )
                    )
                }
            }
            return m
        }

        private fun packBooleans(src: BooleanArray): ByteArray {
            val out = ByteArray((src.size + 7) / 8)
            for (i in src.indices) if (src[i]) {
                out[i / 8] = (out[i / 8].toInt() or (1 shl (i % 8))).toByte()
            }
            return out
        }

        private fun unpackBooleans(src: ByteArray, dst: BooleanArray) {
            for (i in dst.indices) {
                val b = i / 8
                if (b < src.size) dst[i] = (src[b].toInt() shr (i % 8)) and 1 == 1
            }
        }
    }
}
