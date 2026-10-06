package com.emberdeep.game.core

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/** Shared draw helpers. Only used from the single game thread. */
object Draw {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    val serifBold: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val sans: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    val sansBold: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    fun label(
        c: Canvas, str: String, x: Float, y: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.LEFT, font: Typeface = sans, alpha: Int = 255
    ) {
        text.typeface = font
        text.textSize = size
        text.color = if (alpha >= 255) color else Palette.alpha(color, alpha)
        text.textAlign = align
        c.drawText(str, x, y, text)
    }

    fun measure(str: String, size: Float, font: Typeface = sans): Float {
        text.typeface = font
        text.textSize = size
        return text.measureText(str)
    }

    fun panel(
        c: Canvas, l: Float, t: Float, r: Float, b: Float,
        radius: Float, bg: Int = Palette.PANEL, strokeColor: Int = Palette.PANEL_STROKE,
        strokeWidth: Float = 2f
    ) {
        rect.set(l, t, r, b)
        fill.color = bg
        fill.shader = null
        c.drawRoundRect(rect, radius, radius, fill)
        if (strokeWidth > 0f) {
            stroke.color = strokeColor
            stroke.strokeWidth = strokeWidth
            c.drawRoundRect(rect, radius, radius, stroke)
        }
    }

    fun bar(
        c: Canvas, l: Float, t: Float, w: Float, h: Float,
        fraction: Float, fg: Int, bg: Int
    ) {
        rect.set(l, t, l + w, t + h)
        fill.color = bg
        c.drawRoundRect(rect, h / 2f, h / 2f, fill)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0.01f) {
            rect.set(l, t, l + w * f, t + h)
            fill.color = fg
            c.drawRoundRect(rect, h / 2f, h / 2f, fill)
        }
    }
}

/** A chunky, thumb-friendly touch button rendered on the game canvas. */
class Btn(
    var label: String,
    val onClick: () -> Unit
) {
    var x = 0f; var y = 0f; var w = 0f; var h = 0f
    var visible = true
    var enabled = true
    var accent = false
    var subLabel: String? = null
    var icon: Spr? = null
    var iconTint = 0
    var cooldownFraction = 0f   // 0 = ready; >0 draws a dimmed sweep
    var textSize = 0f           // 0 = auto (40% of height)
    private var pressed = false

    fun layout(x: Float, y: Float, w: Float, h: Float) {
        this.x = x; this.y = y; this.w = w; this.h = h
    }

    fun contains(px: Float, py: Float): Boolean =
        px >= x - TOUCH_PAD && px <= x + w + TOUCH_PAD &&
            py >= y - TOUCH_PAD && py <= y + h + TOUCH_PAD

    /** @return true when the event was consumed. */
    fun touch(e: TouchEvent, app: GameApp): Boolean {
        if (!visible) return false
        when (e.type) {
            TouchEvent.DOWN -> {
                if (contains(e.x, e.y)) { pressed = true; return true }
            }
            TouchEvent.MOVE -> {
                if (pressed && !contains(e.x, e.y)) pressed = false
            }
            TouchEvent.UP -> {
                if (pressed) {
                    pressed = false
                    if (contains(e.x, e.y)) {
                        if (enabled) {
                            app.audio.play(Sfx.CLICK, 0.7f)
                            app.vibrate(12)
                            onClick()
                        }
                        return true
                    }
                }
            }
            TouchEvent.CANCEL -> pressed = false
        }
        return false
    }

    fun render(c: Canvas, app: GameApp) {
        if (!visible) return
        val bg = when {
            !enabled -> Palette.alpha(Palette.PANEL_LIGHT, 140)
            pressed -> Palette.mix(Palette.PANEL_LIGHT, Palette.EMBER, 0.35f)
            accent -> Palette.mix(Palette.PANEL_LIGHT, Palette.EMBER, 0.22f)
            else -> Palette.PANEL_LIGHT
        }
        val strokeColor = when {
            !enabled -> Palette.alpha(Palette.PANEL_STROKE, 120)
            accent || pressed -> Palette.EMBER
            else -> Palette.PANEL_STROKE
        }
        val r = h * 0.28f
        Draw.panel(c, x, y, x + w, y + h, r, bg, strokeColor, app.s(1.6f))

        val textColor = if (enabled) Palette.TEXT else Palette.TEXT_FAINT
        val icn = icon
        if (icn != null) {
            val iconSize = (h * 0.56f).toInt()
            val bmp = app.sprites.get(icn, iconSize, tint = iconTint)
            val hasText = label.isNotEmpty()
            val ix = if (hasText) x + h * 0.22f else x + (w - iconSize) / 2f
            val iy = y + (h - iconSize) / 2f
            Draw.fill.alpha = 255
            c.drawBitmap(bmp, ix, iy, null)
            if (hasText) {
                val ts = if (textSize > 0f) textSize else h * 0.34f
                Draw.label(
                    c, label, ix + iconSize + h * 0.16f, y + h / 2f + ts * 0.35f,
                    ts, textColor, font = Draw.sansBold
                )
            }
        } else {
            val ts = if (textSize > 0f) textSize else h * 0.38f
            val cy = if (subLabel == null) y + h / 2f + ts * 0.35f else y + h * 0.44f + ts * 0.3f
            Draw.label(
                c, label, x + w / 2f, cy, ts, textColor,
                Paint.Align.CENTER, Draw.sansBold
            )
            val sub = subLabel
            if (sub != null) {
                Draw.label(
                    c, sub, x + w / 2f, y + h * 0.8f, ts * 0.62f,
                    if (enabled) Palette.TEXT_DIM else Palette.TEXT_FAINT,
                    Paint.Align.CENTER
                )
            }
        }

        if (cooldownFraction > 0.001f) {
            Draw.fill.color = Palette.alpha(0xFF000000.toInt(), 150)
            c.drawRoundRect(
                RectF(x, y + h * (1f - cooldownFraction), x + w, y + h), r, r, Draw.fill
            )
        }
    }

    companion object {
        private const val TOUCH_PAD = 6f
    }
}
