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
import com.emberdeep.game.core.TouchEvent
import com.emberdeep.game.model.GameState

class GameOverScreen(app: GameApp, state: GameState) : Screen(app) {

    private val floor = state.floor
    private val level = state.player.level
    private val kills = state.player.kills
    private val goldBanked = state.player.gold / 2
    private val turns = state.turn

    private val particles = Particles(120)
    private val rng = Rng()
    private var time = 0f

    private val retryBtn = Btn("Try Again") { app.setScreen(MenuScreen(app)); app.push(ClassSelectScreen(app)) }
    private val menuBtn = Btn("Main Menu") { app.setScreen(MenuScreen(app)) }
    private val buttons = listOf(retryBtn, menuBtn)

    override fun onShow() = layout()
    override fun onResize() = layout()

    private fun layout() {
        val w = app.width.toFloat()
        val h = app.height.toFloat()
        val bw = minOf(app.s(280f), w * 0.78f)
        retryBtn.layout((w - bw) / 2f, h * 0.66f, bw, app.s(54f))
        menuBtn.layout((w - bw) / 2f, h * 0.66f + app.s(68f), bw, app.s(50f))
        retryBtn.accent = true
    }

    override fun update(dt: Float) {
        time += dt
        if (rng.chance(dt * 6f)) {
            particles.spawn(
                rng.nextFloat() * app.width, -app.s(10f),
                (rng.nextFloat() - 0.5f) * app.s(16f), app.s(36f) + rng.nextFloat() * app.s(30f),
                2.6f, app.s(2.4f), Palette.EMBER_DIM
            )
        }
        particles.update(dt)
    }

    override fun render(c: Canvas) {
        val w = app.width.toFloat()
        val h = app.height.toFloat()
        c.drawColor(Palette.BG_DEEP)
        particles.render(c)

        Draw.label(
            c, "YOU HAVE FALLEN", w / 2f, h * 0.2f, app.s(32f),
            Palette.BAD, Paint.Align.CENTER, Draw.serifBold
        )
        Draw.label(
            c, "The Emberdeep claims another soul.", w / 2f, h * 0.2f + app.s(28f),
            app.s(14f), Palette.TEXT_DIM, Paint.Align.CENTER
        )

        val px = w / 2f
        val py = h * 0.32f
        Draw.panel(c, w * 0.12f, py, w * 0.88f, py + app.s(170f), app.s(16f))
        var y = py + app.s(34f)
        for ((label, value) in listOf(
            "Reached floor" to "$floor",
            "Hero level" to "$level",
            "Monsters slain" to "$kills",
            "Turns survived" to "$turns",
            "Gold banked" to "$goldBanked g"
        )) {
            Draw.label(c, label, w * 0.18f, y, app.s(14f), Palette.TEXT_DIM)
            Draw.label(
                c, value, w * 0.82f, y, app.s(14f),
                if (label == "Gold banked") Palette.GOLD else Palette.TEXT,
                Paint.Align.RIGHT, Draw.sansBold
            )
            y += app.s(29f)
        }
        Draw.label(
            c, "Treasury: ${app.profile.treasury} gold", px, py + app.s(196f),
            app.s(13f), Palette.GOLD, Paint.Align.CENTER
        )

        for (b in buttons) b.render(c, app)
    }

    override fun onTouch(e: TouchEvent) {
        for (b in buttons) if (b.touch(e, app)) return
    }

    override fun onBack(): Boolean {
        app.setScreen(MenuScreen(app))
        return true
    }
}
