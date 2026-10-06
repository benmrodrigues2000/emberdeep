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

class MenuScreen(app: GameApp) : Screen(app) {

    private val particles = Particles(160)
    private val rng = Rng()
    private var time = 0f

    /** Whether a resumable run is known to exist (re-checked when shown). */
    private var runAvailable = false

    private val continueBtn = Btn("Continue Expedition") {
        val run = app.saves.loadRun()
        if (run != null) {
            app.setScreen(GameScreen(app, run))
        } else {
            // Corrupt or unreadable save: stop offering it.
            runAvailable = false
            refreshButtons()
        }
    }
    private val newBtn = Btn("New Expedition") { app.push(ClassSelectScreen(app)) }
    private val helpBtn = Btn("How to Play") { app.push(HelpScreen(app)) }
    private val settingsBtn = Btn("Settings") { app.push(SettingsScreen(app)) }

    private val buttons = listOf(continueBtn, newBtn, helpBtn, settingsBtn)

    override fun onShow() {
        runAvailable = app.saves.hasRun()
        refreshButtons()
        layout()
    }

    override fun onResize() = layout()

    private fun refreshButtons() {
        continueBtn.visible = runAvailable
        layout()
    }

    private fun layout() {
        val w = app.width.toFloat()
        val h = app.height.toFloat()
        val bw = minOf(app.s(300f), w * 0.82f)
        val bh = app.s(54f)
        val gap = app.s(14f)
        val visible = buttons.filter { it.visible }
        val totalH = visible.size * bh + (visible.size - 1) * gap
        var y = h * 0.52f
        if (y + totalH > h - app.s(90f)) y = h - app.s(90f) - totalH
        for (b in visible) {
            b.layout((w - bw) / 2f, y, bw, bh)
            y += bh + gap
        }
        newBtn.accent = !continueBtn.visible
        continueBtn.accent = continueBtn.visible
    }

    override fun update(dt: Float) {
        time += dt
        if (rng.chance(dt * 14f)) {
            particles.emberRise(
                rng.nextFloat() * app.width, app.height + app.s(8f),
                app.s(30f), app.s(60f), app.s(3f)
            )
        }
        particles.update(dt)
    }

    override fun render(c: Canvas) {
        val w = app.width.toFloat()
        val h = app.height.toFloat()
        c.drawColor(Palette.BG)

        // Deep glow at the bottom — the Emberdeep below.
        Draw.fill.shader = android.graphics.RadialGradient(
            w / 2f, h + h * 0.25f, h * 0.75f,
            Palette.alpha(Palette.EMBER, 70), 0, android.graphics.Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, Draw.fill)
        Draw.fill.shader = null

        particles.render(c)

        // Title with ember pulse.
        val pulse = 0.5f + 0.5f * kotlin.math.sin(time * 1.6f)
        val titleSize = app.s(56f)
        val ty = h * 0.26f
        Draw.label(
            c, "EMBERDEEP", w / 2f + app.s(2f), ty + app.s(2f), titleSize,
            Palette.alpha(0xFF000000.toInt(), 160), Paint.Align.CENTER, Draw.serifBold
        )
        Draw.label(
            c, "EMBERDEEP", w / 2f, ty, titleSize,
            Palette.mix(Palette.EMBER, Palette.EMBER_BRIGHT, pulse),
            Paint.Align.CENTER, Draw.serifBold
        )
        Draw.label(
            c, "Descend. Survive. Slay the dragon.", w / 2f, ty + app.s(30f),
            app.s(15f), Palette.TEXT_DIM, Paint.Align.CENTER
        )

        for (b in buttons) b.render(c, app)

        // Footer stats.
        val p = app.profile
        val stats = buildString {
            append("Best floor: ${p.bestFloor}")
            append("   Victories: ${p.victories}")
            append("   Gold: ${p.treasury}")
        }
        Draw.label(
            c, stats, w / 2f, h - app.s(40f), app.s(13f),
            Palette.TEXT_DIM, Paint.Align.CENTER
        )
        Draw.label(
            c, "v1.0", w / 2f, h - app.s(20f), app.s(11f),
            Palette.TEXT_FAINT, Paint.Align.CENTER
        )
    }

    override fun onTouch(e: TouchEvent) {
        for (b in buttons) if (b.touch(e, app)) return
    }
}
