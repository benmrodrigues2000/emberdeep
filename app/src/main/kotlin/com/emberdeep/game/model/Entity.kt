package com.emberdeep.game.model

import org.json.JSONArray
import org.json.JSONObject

/** Shared animation state for anything drawn on the grid. */
abstract class GridEntity {
    var x = 0
    var y = 0

    // Smooth render position (in tile units) and attack lunge animation.
    var drawX = 0f
    var drawY = 0f
    var lungeX = 0f
    var lungeY = 0f
    var lungeT = 0f
    var hitFlash = 0f

    fun snapDraw() {
        drawX = x.toFloat()
        drawY = y.toFloat()
    }

    fun startLunge(dx: Int, dy: Int) {
        lungeX = dx.toFloat()
        lungeY = dy.toFloat()
        lungeT = 1f
    }

    /** True while the entity is still visually sliding to its tile. */
    val animating: Boolean
        get() = lungeT > 0.05f ||
            Math.abs(drawX - x) > 0.02f || Math.abs(drawY - y) > 0.02f

    fun updateAnim(dt: Float) {
        val speed = 11f * dt
        drawX += (x - drawX) * speed.coerceAtMost(1f)
        drawY += (y - drawY) * speed.coerceAtMost(1f)
        if (Math.abs(drawX - x) < 0.015f) drawX = x.toFloat()
        if (Math.abs(drawY - y) < 0.015f) drawY = y.toFloat()
        if (lungeT > 0f) lungeT = (lungeT - dt * 6f).coerceAtLeast(0f)
        if (hitFlash > 0f) hitFlash = (hitFlash - dt * 4f).coerceAtLeast(0f)
    }
}

class Enemy(val type: EnemyType, x: Int, y: Int) : GridEntity() {
    var maxHp = type.hp
    var hp = type.hp
    var atk = type.atk
    var ac = type.ac
    var dmgB = type.dmgB
    var xp = type.xp
    var awake = false
    var burnTurns = 0
    var breathCharge = 0   // boss only

    init {
        this.x = x; this.y = y
        snapDraw()
    }

    /** Scale stats with dungeon depth so early monsters stay relevant. */
    fun scaleToFloor(floor: Int) {
        val over = (floor - type.minFloor).coerceAtLeast(0)
        maxHp = type.hp + over
        hp = maxHp
        atk = type.atk + over / 3
        dmgB = type.dmgB + over / 4
        xp = type.xp + over * 3
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("t", type.name); put("x", x); put("y", y)
        put("hp", hp); put("mhp", maxHp); put("atk", atk); put("ac", ac)
        put("db", dmgB); put("xp", xp); put("aw", awake)
        put("burn", burnTurns); put("br", breathCharge)
    }

    companion object {
        fun fromJson(o: JSONObject): Enemy {
            val e = Enemy(EnemyType.byName(o.optString("t")), o.optInt("x"), o.optInt("y"))
            e.hp = o.optInt("hp", e.hp)
            e.maxHp = o.optInt("mhp", e.maxHp)
            e.atk = o.optInt("atk", e.atk)
            e.ac = o.optInt("ac", e.ac)
            e.dmgB = o.optInt("db", e.dmgB)
            e.xp = o.optInt("xp", e.xp)
            e.awake = o.optBoolean("aw", false)
            e.burnTurns = o.optInt("burn", 0)
            e.breathCharge = o.optInt("br", 0)
            e.snapDraw()
            return e
        }
    }
}

class Player(val classType: ClassType) : GridEntity() {

    var level = 1
    var xp = 0
    var maxHp = classType.baseHp
    var hp = maxHp
    var gold = 0
    var kills = 0

    var weapon: ItemType? = null
    var armor: ItemType? = null
    val inventory = ArrayList<Item>()

    var abilityCd = 0
    var strengthTurns = 0
    var shieldTurns = 0
    var burnTurns = 0
    var autoCrit = false
    var levelUpsPending = 0   // used for a brief HUD flash

    val xpToNext: Int get() = 20 + (level - 1) * 25

    val atkBonus: Int
        get() = classType.baseAtk + (level - 1) / 2 +
            (if (strengthTurns > 0) 3 else 0)

    val acTotal: Int
        get() = classType.baseAc + (armor?.acBonus ?: 0) +
            (if (shieldTurns > 0) 4 else 0)

    val dmgN: Int get() = weapon?.dmgN ?: classType.dmgN
    val dmgS: Int get() = weapon?.dmgS ?: classType.dmgS
    val dmgB: Int get() = (weapon?.dmgB ?: classType.dmgB) + (level - 1) / 2

    val damageLabel: String
        get() {
            val b = dmgB
            return "${dmgN}d$dmgS" + (if (b > 0) "+$b" else "")
        }

    fun addItem(type: ItemType, count: Int = 1): Boolean {
        if (type.stackable) {
            val existing = inventory.firstOrNull { it.type == type }
            if (existing != null) {
                existing.count += count
                return true
            }
        }
        if (inventory.size >= MAX_SLOTS) return false
        inventory.add(Item(type, count))
        return true
    }

    fun removeOne(item: Item) {
        item.count--
        if (item.count <= 0) inventory.remove(item)
    }

    /** @return list of level-up messages (may be empty). */
    fun gainXp(amount: Int): List<String> {
        xp += amount
        val msgs = ArrayList<String>()
        while (xp >= xpToNext) {
            xp -= xpToNext
            level++
            val gained = classType.hitDie + 2
            maxHp += gained
            hp = maxHp  // level-ups fully restore you — a classic earned reprieve
            levelUpsPending++
            msgs.add("Welcome to level $level! Max HP +$gained, fully healed.")
        }
        return msgs
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("cls", classType.name)
        put("x", x); put("y", y)
        put("lvl", level); put("xp", xp)
        put("hp", hp); put("mhp", maxHp)
        put("gold", gold); put("kills", kills)
        put("wpn", weapon?.name); put("arm", armor?.name)
        put("cd", abilityCd)
        put("str", strengthTurns); put("shd", shieldTurns)
        put("burn", burnTurns); put("ac2", autoCrit)
        val inv = JSONArray()
        for (it in inventory) inv.put(JSONObject().put("t", it.type.name).put("c", it.count))
        put("inv", inv)
    }

    companion object {
        const val MAX_SLOTS = 10

        fun fromJson(o: JSONObject): Player {
            val p = Player(ClassType.byName(o.optString("cls")))
            p.x = o.optInt("x"); p.y = o.optInt("y")
            p.level = o.optInt("lvl", 1)
            p.xp = o.optInt("xp", 0)
            p.maxHp = o.optInt("mhp", p.maxHp)
            p.hp = o.optInt("hp", p.maxHp).coerceIn(1, p.maxHp)
            p.gold = o.optInt("gold", 0)
            p.kills = o.optInt("kills", 0)
            p.weapon = ItemType.byName(o.optString("wpn", ""))
            p.armor = ItemType.byName(o.optString("arm", ""))
            p.abilityCd = o.optInt("cd", 0)
            p.strengthTurns = o.optInt("str", 0)
            p.shieldTurns = o.optInt("shd", 0)
            p.burnTurns = o.optInt("burn", 0)
            p.autoCrit = o.optBoolean("ac2", false)
            val inv = o.optJSONArray("inv")
            if (inv != null) {
                for (i in 0 until inv.length()) {
                    val io = inv.optJSONObject(i) ?: continue
                    val t = ItemType.byName(io.optString("t")) ?: continue
                    p.inventory.add(Item(t, io.optInt("c", 1).coerceAtLeast(1)))
                }
            }
            p.snapDraw()
            return p
        }
    }
}
