package com.emberdeep.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.emberdeep.game.core.Btn
import com.emberdeep.game.core.Draw
import com.emberdeep.game.core.GameApp
import com.emberdeep.game.core.Palette
import com.emberdeep.game.core.Particles
import com.emberdeep.game.core.Rng
import com.emberdeep.game.core.Screen
import com.emberdeep.game.core.Spr
import com.emberdeep.game.core.TouchEvent
import com.emberdeep.game.model.GameState

class VictoryScreen(app: GameApp, state: GameState) : Screen(app) {

    private val level = state.player.level
    private val kills = state.player.kills
    private val goldBanked = state.player.gold + 300
    private val turns = state.turn
    private val cls = state.player.classType.display

    private val particles = Particles(200)
    private val rng = Rng()
    private var time = 0f

    private val menuBtn = Btn("Return Victorious") { app.setScreen(MenuScreen(app)) }

    override fun onShow() = layout()
    override fun onResize() = layout()

    private fun layout() {
        val w = app.width.toFloat()
        val bw = minOf(app.s(300f), w * 0.8f)
        menuBtn.layout((w - bw) / 2f, app.height * 0.82f, bw, app.s(54f))
        menuBtn.accent = true
    }

    override fun update(dt: Float) {
        time += dt
        if (rng.chance(dt * 20f)) {
            particles.emberRise(
                rng.nextFloat() * app.width, app.height + app.s(8f),
                app.s(40f), app.s(80f), app.s(3f)
            )
        }
        if (rng.chance(dt * 8f)) {
            particles.burst(
                rng.nextFloat() * app.width, rng.nextFloat() * app.height * 0.4f,
                if (rng.chance(0.5f)) Palette.GOLD else Palette.EMBER_BRIGHT,
                8, app.s(60f), app.s(2.6f), app.s(40f)
            )
        }
        particles.update(dt)
    }

    override fun render(c: Canvas) {
        val w = app.width.toFloat()
        val h = app.height.toFloat()
        c.drawColor(Palette.BG)
        particles.render(c)

        val pulse = 0.5f + 0.5f * kotlin.math.sin(time * 2.2f)
        Draw.label(
            c, "VICTORY!", w / 2f, h * 0.14f, app.s(42f),
            Palette.mix(Palette.GOLD, Palette.EMBER_BRIGHT, pulse),
            Paint.Align.CENTER, Draw.serifBold
        )
        Draw.label(
            c, "The Ember Dragon is slain. The deep grows cold.",
            w / 2f, h * 0.14f + app.s(30f), app.s(14f),
            Palette.TEXT_DIM, Paint.Align.CENTER
        )

        // Fallen dragon trophy.
        val size = (app.s(120f)).toInt()
        val bmp = app.sprites.get(Spr.DRAGON, size)
        c.save()
        c.rotate(180f, w / 2f, h * 0.3f)
        c.drawBitmap(bmp, w / 2f - size / 2f, h * 0.3f - size / 2f, null)
        c.restore()

        val py = h * 0.42f
        Draw.panel(c, w * 0.12f, py, w * 0.88f, py + app.s(196f), app.s(16f))
        var y = py + app.s(36f)
        for ((label, value) in listOf(
            "Hero" to "$cls  Lv $level",
            "Monsters slain" to "$kills",
            "Turns taken" to "$turns",
            "Gold banked" to "$goldBanked g",
            "Victories" to "${app.profile.victories}"
        )) {
            Draw.label(c, label, w * 0.18f, y, app.s(14f), Palette.TEXT_DIM)
            Draw.label(
                c, value, w * 0.82f, y, app.s(14f),
                if (label == "Gold banked") Palette.GOLD else Palette.TEXT,
                Paint.Align.RIGHT, Draw.sansBold
            )
            y += app.s(31f)
        }

        menuBtn.render(c, app)
    }

    override fun onTouch(e: TouchEvent) {
        menuBtn.touch(e, app)
    }

    override fun onBack(): Boolean {
        app.setScreen(MenuScreen(app))
        return true
    }
}
