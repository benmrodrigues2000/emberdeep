package com.emberdeep.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.emberdeep.game.core.Btn
import com.emberdeep.game.core.Draw
import com.emberdeep.game.core.GameApp
import com.emberdeep.game.core.Palette
import com.emberdeep.game.core.Screen
import com.emberdeep.game.core.Sfx
import com.emberdeep.game.core.Spr
import com.emberdeep.game.core.TouchEvent
import com.emberdeep.game.model.ClassType

class ClassSelectScreen(app: GameApp) : Screen(app) {

    private var selected: ClassType = ClassType.FIGHTER
    private var flashMsg = ""
    private var flashT = 0f

    private val startBtn = Btn("Begin the Descent") { startRun() }
    private val backBtn = Btn("Back") { app.pop() }

    private val cardRects = HashMap<ClassType, FloatArray>()
    private var downAt: FloatArray? = null

    override fun onShow() = layout()
    override fun onResize() = layout()

    private fun layout() {
        val w = app.width.toFloat()
        val cw = minOf(app.s(340f), w * 0.92f)
        val ch = app.s(104f)
        val gap = app.s(12f)
        var y = app.s(96f)
        for (cls in ClassType.entries) {
            cardRects[cls] = floatArrayOf((w - cw) / 2f, y, cw, ch)
            y += ch + gap
        }
        val bw = minOf(app.s(300f), w * 0.8f)
        startBtn.layout((w - bw) / 2f, y + app.s(10f), bw, app.s(54f))
        startBtn.accent = true
        backBtn.layout((w - bw) / 2f, y + app.s(76f), bw, app.s(44f))
    }

    private fun startRun() {
        if (!app.profile.isUnlocked(selected)) {
            tryUnlock(selected)
            return
        }
        app.setScreen(GameScreen(app, GameScreen.newRun(app, selected)))
    }

    private fun tryUnlock(cls: ClassType) {
        val p = app.profile
        if (p.isUnlocked(cls)) return
        if (p.treasury >= cls.unlockCost) {
            p.treasury -= cls.unlockCost
            p.unlock(cls)
            app.saveProfile()
            app.audio.play(Sfx.LEVELUP)
            flash("${cls.display} unlocked!")
        } else {
            app.audio.play(Sfx.MISS)
            flash("Need ${cls.unlockCost} gold (you have ${p.treasury}).")
        }
    }

    private fun flash(msg: String) {
        flashMsg = msg
        flashT = 2.5f
    }

    override fun update(dt: Float) {
        if (flashT > 0f) flashT -= dt
    }

    override fun render(c: Canvas) {
        val w = app.width.toFloat()
        c.drawColor(Palette.BG)
        Draw.label(
            c, "Choose Your Adventurer", w / 2f, app.s(52f), app.s(24f),
            Palette.TEXT, Paint.Align.CENTER, Draw.serifBold
        )
        Draw.label(
            c, "Treasury: ${app.profile.treasury} gold", w / 2f, app.s(76f),
            app.s(14f), Palette.GOLD, Paint.Align.CENTER
        )

        for (cls in ClassType.entries) renderCard(c, cls)

        startBtn.label = if (app.profile.isUnlocked(selected)) {
            "Begin the Descent"
        } else {
            "Unlock for ${selected.unlockCost} gold"
        }
        startBtn.render(c, app)
        backBtn.render(c, app)

        if (flashT > 0f) {
            val alpha = (255 * (flashT / 2.5f).coerceAtMost(1f)).toInt()
            Draw.label(
                c, flashMsg, w / 2f, app.height - app.s(24f), app.s(15f),
                Palette.alpha(Palette.GOLD, alpha), Paint.Align.CENTER, Draw.sansBold
            )
        }
    }

    private fun renderCard(c: Canvas, cls: ClassType) {
        val r = cardRects[cls] ?: return
        val (x, y, w, h) = r
        val unlocked = app.profile.isUnlocked(cls)
        val isSel = cls == selected
        Draw.panel(
            c, x, y, x + w, y + h, app.s(14f),
            if (isSel) Palette.mix(Palette.PANEL_SOLID, Palette.EMBER, 0.12f) else Palette.PANEL_SOLID,
            if (isSel) Palette.EMBER else Palette.PANEL_STROKE,
            app.s(if (isSel) 2.5f else 1.5f)
        )
        val spr = when (cls) {
            ClassType.FIGHTER -> Spr.HERO_FIGHTER
            ClassType.ROGUE -> Spr.HERO_ROGUE
            ClassType.MAGE -> Spr.HERO_MAGE
        }
        val iconSize = (h * 0.62f).toInt()
        val bmp = app.sprites.get(spr, iconSize)
        c.drawBitmap(bmp, x + app.s(14f), y + (h - iconSize) / 2f, null)

        val tx = x + app.s(14f) + iconSize + app.s(14f)
        val nameColor = if (unlocked) Palette.TEXT else Palette.TEXT_FAINT
        Draw.label(c, cls.display, tx, y + app.s(26f), app.s(19f), nameColor, font = Draw.sansBold)
        if (!unlocked) {
            Draw.label(
                c, "LOCKED - ${cls.unlockCost}g", x + w - app.s(12f), y + app.s(26f),
                app.s(12f), Palette.GOLD, Paint.Align.RIGHT, Draw.sansBold
            )
        }
        Draw.label(c, cls.blurb, tx, y + app.s(45f), app.s(12f), Palette.TEXT_DIM)
        Draw.label(
            c, "HP ${cls.baseHp}   ATK +${cls.baseAtk}   AC ${cls.baseAc}   DMG ${cls.dmgN}d${cls.dmgS}+${cls.dmgB}",
            tx, y + app.s(63f), app.s(12f), Palette.TEXT
        )
        Draw.label(
            c, "${cls.abilityName}: ${cls.abilityDesc}", tx, y + app.s(82f),
            app.s(11f), Palette.INFO
        )
    }

    override fun onTouch(e: TouchEvent) {
        if (startBtn.touch(e, app)) return
        if (backBtn.touch(e, app)) return
        when (e.type) {
            TouchEvent.DOWN -> downAt = floatArrayOf(e.x, e.y)
            TouchEvent.UP -> {
                val d = downAt ?: return
                downAt = null
                if (Math.abs(d[0] - e.x) > app.s(24f) || Math.abs(d[1] - e.y) > app.s(24f)) return
                for (cls in ClassType.entries) {
                    val r = cardRects[cls] ?: continue
                    if (e.x >= r[0] && e.x <= r[0] + r[2] && e.y >= r[1] && e.y <= r[1] + r[3]) {
                        selected = cls
                        app.audio.play(Sfx.CLICK, 0.6f)
                        if (!app.profile.isUnlocked(cls)) tryUnlock(cls)
                        return
                    }
                }
            }
        }
    }

    override fun onBack(): Boolean {
        app.pop()
        return true
    }
}
