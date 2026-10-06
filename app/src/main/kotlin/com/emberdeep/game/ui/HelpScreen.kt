package com.emberdeep.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.emberdeep.game.core.Btn
import com.emberdeep.game.core.Draw
import com.emberdeep.game.core.GameApp
import com.emberdeep.game.core.Palette
import com.emberdeep.game.core.Screen
import com.emberdeep.game.core.TouchEvent

class HelpScreen(app: GameApp) : Screen(app) {

    private val okBtn = Btn("Got It") { app.pop() }

    private val lines = listOf(
        "THE QUEST" to Palette.EMBER_BRIGHT,
        "Descend 10 floors of the Emberdeep and slay" to Palette.TEXT,
        "the Ember Dragon that sleeps at the bottom." to Palette.TEXT,
        "" to 0,
        "MOVING & FIGHTING" to Palette.EMBER_BRIGHT,
        "Tap a tile to walk there. Tap an enemy to" to Palette.TEXT,
        "attack it. Tap yourself to wait a turn." to Palette.TEXT,
        "Combat uses classic d20 rolls against AC." to Palette.TEXT_DIM,
        "Natural 20s deal critical damage!" to Palette.TEXT_DIM,
        "" to 0,
        "TURNS" to Palette.EMBER_BRIGHT,
        "The dungeon only moves when you do. Think" to Palette.TEXT,
        "before you step - monsters hit hard below." to Palette.TEXT,
        "" to 0,
        "LOOT & GEAR" to Palette.EMBER_BRIGHT,
        "Walk over items to pick them up. Equip" to Palette.TEXT,
        "better weapons and armor from your pack." to Palette.TEXT,
        "Potions and scrolls can save your life." to Palette.TEXT,
        "" to 0,
        "ABILITIES" to Palette.EMBER_BRIGHT,
        "Each class has a signature ability with a" to Palette.TEXT,
        "cooldown. Use it at the right moment." to Palette.TEXT,
        "" to 0,
        "DEATH & GOLD" to Palette.EMBER_BRIGHT,
        "Death is permanent, but half your gold is" to Palette.TEXT,
        "banked to unlock new classes. Win to bank" to Palette.TEXT,
        "everything plus a 300 gold bounty." to Palette.TEXT
    )

    override fun onShow() = layout()
    override fun onResize() = layout()

    private fun layout() {
        val w = app.width.toFloat()
        val bw = minOf(app.s(280f), w * 0.7f)
        okBtn.layout((w - bw) / 2f, app.height - app.s(80f), bw, app.s(52f))
        okBtn.accent = true
    }

    override fun render(c: Canvas) {
        val w = app.width.toFloat()
        c.drawColor(Palette.BG)
        Draw.label(
            c, "How to Play", w / 2f, app.s(54f), app.s(26f),
            Palette.TEXT, Paint.Align.CENTER, Draw.serifBold
        )
        var y = app.s(92f)
        for ((text, color) in lines) {
            if (text.isNotEmpty()) {
                val isHeader = color == Palette.EMBER_BRIGHT
                Draw.label(
                    c, text, app.s(28f), y,
                    if (isHeader) app.s(14f) else app.s(13f), color,
                    font = if (isHeader) Draw.sansBold else Draw.sans
                )
            }
            y += app.s(17f)
        }
        okBtn.render(c, app)
    }

    override fun onTouch(e: TouchEvent) {
        okBtn.touch(e, app)
    }

    override fun onBack(): Boolean {
        app.pop()
        return true
    }
}
