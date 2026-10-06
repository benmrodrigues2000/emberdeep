package com.emberdeep.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.emberdeep.game.core.Btn
import com.emberdeep.game.core.Draw
import com.emberdeep.game.core.GameApp
import com.emberdeep.game.core.Palette
import com.emberdeep.game.core.Screen
import com.emberdeep.game.core.TouchEvent
import com.emberdeep.game.model.GameState

class PauseOverlay(app: GameApp, private val state: GameState) : Screen(app) {

    override val isOverlay = true

    private val resumeBtn = Btn("Resume") { app.pop() }
    private val settingsBtn = Btn("Settings") { app.push(SettingsScreen(app)) }
    private val quitBtn = Btn("Save & Quit") {
        app.saves.saveRun(state)
        app.setScreen(MenuScreen(app))
    }
    private val buttons = listOf(resumeBtn, settingsBtn, quitBtn)

    override fun onShow() = layout()
    override fun onResize() = layout()

    private fun layout() {
        val w = app.width.toFloat()
        val h = app.height.toFloat()
        val bw = minOf(app.s(280f), w * 0.78f)
        val bh = app.s(54f)
        var y = h / 2f - app.s(60f)
        for (b in buttons) {
            b.layout((w - bw) / 2f, y, bw, bh)
            y += bh + app.s(16f)
        }
        resumeBtn.accent = true
    }

    override fun render(c: Canvas) {
        val w = app.width.toFloat()
        val h = app.height.toFloat()
        Draw.fill.color = Palette.alpha(0xFF000000.toInt(), 180)
        c.drawRect(0f, 0f, w, h, Draw.fill)
        Draw.label(
            c, "PAUSED", w / 2f, h / 2f - app.s(110f), app.s(34f),
            Palette.EMBER_BRIGHT, Paint.Align.CENTER, Draw.serifBold
        )
        Draw.label(
            c, "Floor ${state.floor} - Turn ${state.turn}", w / 2f, h / 2f - app.s(82f),
            app.s(14f), Palette.TEXT_DIM, Paint.Align.CENTER
        )
        for (b in buttons) b.render(c, app)
    }

    override fun onTouch(e: TouchEvent) {
        for (b in buttons) if (b.touch(e, app)) return
    }

    override fun onBack(): Boolean {
        app.pop()
        return true
    }
}
