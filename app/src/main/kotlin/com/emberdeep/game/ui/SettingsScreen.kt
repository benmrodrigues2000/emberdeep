package com.emberdeep.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.emberdeep.game.core.Btn
import com.emberdeep.game.core.Draw
import com.emberdeep.game.core.GameApp
import com.emberdeep.game.core.Palette
import com.emberdeep.game.core.Screen
import com.emberdeep.game.core.TouchEvent

class SettingsScreen(app: GameApp) : Screen(app) {

    override val isOverlay = true

    private var confirmReset = false

    private val soundBtn = Btn("") {
        app.profile.sound = !app.profile.sound
        app.audio.soundEnabled = app.profile.sound
        app.saveProfile()
        refreshLabels()
    }
    private val musicBtn = Btn("") {
        app.profile.music = !app.profile.music
        app.audio.setMusic(app.profile.music)
        app.saveProfile()
        refreshLabels()
    }
    private val hapticsBtn = Btn("") {
        app.profile.haptics = !app.profile.haptics
        app.saveProfile()
        refreshLabels()
    }
    private val minimapBtn = Btn("") {
        app.profile.minimap = !app.profile.minimap
        app.saveProfile()
        refreshLabels()
    }
    private val resetBtn = Btn("Reset All Progress") {
        if (!confirmReset) {
            confirmReset = true
            refreshLabels()
        } else {
            app.saves.resetAll()
            val fresh = app.saves.loadProfile()
            app.profile.sound = fresh.sound
            app.profile.music = fresh.music
            app.profile.haptics = fresh.haptics
            app.profile.minimap = fresh.minimap
            app.profile.unlocked.clear()
            app.profile.unlocked.addAll(fresh.unlocked)
            app.profile.treasury = 0
            app.profile.bestFloor = 0
            app.profile.victories = 0
            app.profile.runs = 0
            app.profile.totalKills = 0
            confirmReset = false
            app.setScreen(MenuScreen(app))
        }
    }
    private val backBtn = Btn("Back") { app.pop() }

    private val buttons = listOf(soundBtn, musicBtn, hapticsBtn, minimapBtn, resetBtn, backBtn)

    override fun onShow() {
        confirmReset = false
        layout()
        refreshLabels()
    }

    override fun onResize() = layout()

    private fun layout() {
        val w = app.width.toFloat()
        val h = app.height.toFloat()
        val bw = minOf(app.s(300f), w * 0.8f)
        val bh = app.s(50f)
        var y = h / 2f - app.s(190f)
        for (b in buttons) {
            b.layout((w - bw) / 2f, y, bw, bh)
            y += bh + app.s(13f)
        }
        backBtn.accent = true
    }

    private fun refreshLabels() {
        soundBtn.label = "Sound: ${onOff(app.profile.sound)}"
        musicBtn.label = "Music: ${onOff(app.profile.music)}"
        hapticsBtn.label = "Haptics: ${onOff(app.profile.haptics)}"
        minimapBtn.label = "Minimap: ${onOff(app.profile.minimap)}"
        resetBtn.label = if (confirmReset) "Tap again to confirm!" else "Reset All Progress"
    }

    private fun onOff(b: Boolean) = if (b) "ON" else "OFF"

    override fun render(c: Canvas) {
        val w = app.width.toFloat()
        val h = app.height.toFloat()
        Draw.fill.color = Palette.alpha(0xFF000000.toInt(), 200)
        c.drawRect(0f, 0f, w, h, Draw.fill)
        Draw.label(
            c, "Settings", w / 2f, h / 2f - app.s(216f), app.s(28f),
            Palette.TEXT, Paint.Align.CENTER, Draw.serifBold
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
