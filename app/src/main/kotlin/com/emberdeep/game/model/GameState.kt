package com.emberdeep.game.model

import com.emberdeep.game.core.Rng
import org.json.JSONArray
import org.json.JSONObject

/** One log line with its display color. */
class LogLine(val text: String, val color: Int, var age: Float = 0f)

/** The complete state of a run: everything needed to save and resume. */
class GameState(
    var floor: Int,
    var player: Player,
    var map: DungeonMap,
    val rng: Rng
) {
    val enemies = ArrayList<Enemy>()
    val log = ArrayList<LogLine>()
    var turn = 0
    var bossDefeated = false

    fun enemyAt(x: Int, y: Int): Enemy? =
        enemies.firstOrNull { it.hp > 0 && it.x == x && it.y == y }

    fun isOccupied(x: Int, y: Int): Boolean =
        (player.x == x && player.y == y) || enemyAt(x, y) != null

    fun addLog(text: String, color: Int) {
        log.add(LogLine(text, color))
        while (log.size > 40) log.removeAt(0)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("ver", SAVE_VERSION)
        put("floor", floor)
        put("turn", turn)
        put("boss", bossDefeated)
        put("rng", rng.state)
        put("player", player.toJson())
        put("map", map.toJson())
        val es = JSONArray()
        for (e in enemies) if (e.hp > 0) es.put(e.toJson())
        put("enemies", es)
        val ls = JSONArray()
        for (l in log.takeLast(12)) ls.put(JSONObject().put("t", l.text).put("c", l.color))
        put("log", ls)
    }

    companion object {
        const val SAVE_VERSION = 1

        fun fromJson(o: JSONObject): GameState {
            val rng = Rng(1L)
            rng.state = o.optLong("rng", System.nanoTime())
            val state = GameState(
                o.optInt("floor", 1),
                Player.fromJson(o.getJSONObject("player")),
                DungeonMap.fromJson(o.getJSONObject("map")),
                rng
            )
            state.turn = o.optInt("turn", 0)
            state.bossDefeated = o.optBoolean("boss", false)
            val es = o.optJSONArray("enemies")
            if (es != null) {
                for (i in 0 until es.length()) {
                    val eo = es.optJSONObject(i) ?: continue
                    state.enemies.add(Enemy.fromJson(eo))
                }
            }
            val ls = o.optJSONArray("log")
            if (ls != null) {
                for (i in 0 until ls.length()) {
                    val lo = ls.optJSONObject(i) ?: continue
                    state.log.add(LogLine(lo.optString("t"), lo.optInt("c", -1)))
                }
            }
            return state
        }
    }
}
