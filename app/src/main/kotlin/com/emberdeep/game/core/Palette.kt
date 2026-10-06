package com.emberdeep.game.core

/** Central color palette so the whole game shares one art direction. */
object Palette {
    const val BG = 0xFF0C0A10.toInt()
    const val BG_DEEP = 0xFF07060A.toInt()
    const val PANEL = 0xEE1A151E.toInt()
    const val PANEL_SOLID = 0xFF1A151E.toInt()
    const val PANEL_LIGHT = 0xFF2A2230.toInt()
    const val PANEL_STROKE = 0xFF4A3A40.toInt()

    const val EMBER = 0xFFFF6B2B.toInt()
    const val EMBER_BRIGHT = 0xFFFFA14D.toInt()
    const val EMBER_DIM = 0xFF8C3A1A.toInt()
    const val GOLD = 0xFFFFC94D.toInt()

    const val TEXT = 0xFFF2E8DC.toInt()
    const val TEXT_DIM = 0xFF9C8F86.toInt()
    const val TEXT_FAINT = 0xFF5F5650.toInt()

    const val HP = 0xFFE0443C.toInt()
    const val HP_DARK = 0xFF55201E.toInt()
    const val XP = 0xFF7BC65A.toInt()
    const val XP_DARK = 0xFF27401E.toInt()
    const val MANA = 0xFF5A9BD4.toInt()

    const val GOOD = 0xFF8CD96B.toInt()
    const val BAD = 0xFFFF5A4D.toInt()
    const val INFO = 0xFFB9A8E8.toInt()

    const val FLOOR = 0xFF3A3036.toInt()
    const val FLOOR_LIT = 0xFF4A3C40.toInt()
    const val WALL = 0xFF241D26.toInt()
    const val WALL_TOP = 0xFF55434E.toInt()

    /** Apply alpha 0..255 to a color. */
    fun alpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    /** Linear blend between two colors. */
    fun mix(c1: Int, c2: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val a = ((c1 ushr 24) + (((c2 ushr 24) - (c1 ushr 24)) * k)).toInt()
        val r = (((c1 shr 16) and 0xFF) + ((((c2 shr 16) and 0xFF) - ((c1 shr 16) and 0xFF)) * k)).toInt()
        val g = (((c1 shr 8) and 0xFF) + ((((c2 shr 8) and 0xFF) - ((c1 shr 8) and 0xFF)) * k)).toInt()
        val b = ((c1 and 0xFF) + (((c2 and 0xFF) - (c1 and 0xFF)) * k)).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
