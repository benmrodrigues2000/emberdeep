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
import com.emberdeep.game.model.GameState
import com.emberdeep.game.model.Item
import com.emberdeep.game.model.ItemKind
import com.emberdeep.game.model.ItemType
import com.emberdeep.game.model.Player
import com.emberdeep.game.systems.TurnEngine

class InventoryOverlay(
    app: GameApp,
    private val engine: TurnEngine,
    private val state: GameState
) : Screen(app) {

    override val isOverlay = true

    private var selected: Item? = null

    private val useBtn = Btn("Use") { actOnSelected(use = true) }
    private val dropBtn = Btn("Drop") { actOnSelected(use = false) }
    private val closeBtn = Btn("Close") { app.pop() }
    private val buttons = listOf(useBtn, dropBtn, closeBtn)

    private var panelL = 0f
    private var panelT = 0f
    private var panelR = 0f
    private var panelB = 0f
    private var slotSize = 0f
    private var gridX = 0f
    private var gridY = 0f

    override fun onShow() = layout()
    override fun onResize() = layout()

    private fun layout() {
        val w = app.width.toFloat()
        val h = app.height.toFloat()
        val pw = minOf(app.s(350f), w * 0.94f)
        val ph = minOf(app.s(470f), h * 0.84f)
        panelL = (w - pw) / 2f
        panelT = (h - ph) / 2f
        panelR = panelL + pw
        panelB = panelT + ph

        slotSize = (pw - app.s(40f)) / COLS
        gridX = panelL + app.s(20f)
        gridY = panelT + app.s(128f)

        val bw = (pw - app.s(52f)) / 3f
        val by = panelB - app.s(62f)
        useBtn.layout(panelL + app.s(16f), by, bw, app.s(46f))
        dropBtn.layout(panelL + app.s(26f) + bw, by, bw, app.s(46f))
        closeBtn.layout(panelL + app.s(36f) + bw * 2f, by, bw, app.s(46f))
        useBtn.accent = true
    }

    private fun actOnSelected(use: Boolean) {
        val item = selected ?: return
        selected = null
        if (use) {
            val consumedTurn = engine.useItem(item)
            if (consumedTurn) app.pop()
        } else {
            engine.dropItem(item)
        }
    }

    override fun render(c: Canvas) {
        val w = app.width.toFloat()
        val h = app.height.toFloat()
        Draw.fill.color = Palette.alpha(0xFF000000.toInt(), 170)
        c.drawRect(0f, 0f, w, h, Draw.fill)

        Draw.panel(c, panelL, panelT, panelR, panelB, app.s(18f), Palette.PANEL_SOLID)

        val p = state.player
        Draw.label(
            c, "Pack  (${p.inventory.size}/${Player.MAX_SLOTS})",
            (panelL + panelR) / 2f, panelT + app.s(32f), app.s(20f),
            Palette.TEXT, Paint.Align.CENTER, Draw.serifBold
        )

        // Equipment summary.
        val wpn = p.weapon?.display ?: "${p.classType.display}'s arms"
        val arm = p.armor?.display ?: "Traveling clothes"
        Draw.label(
            c, "Weapon: $wpn  (${p.damageLabel})", panelL + app.s(20f),
            panelT + app.s(60f), app.s(13f), Palette.TEXT
        )
        Draw.label(
            c, "Armor: $arm  (AC ${p.acTotal})", panelL + app.s(20f),
            panelT + app.s(80f), app.s(13f), Palette.TEXT
        )
        Draw.label(
            c, "HP ${p.hp}/${p.maxHp}   ATK +${p.atkBonus}   Gold ${p.gold}",
            panelL + app.s(20f), panelT + app.s(100f), app.s(13f), Palette.TEXT_DIM
        )

        // Grid slots.
        for (i in 0 until Player.MAX_SLOTS) {
            val col = i % COLS
            val row = i / COLS
            val x = gridX + col * slotSize
            val y = gridY + row * slotSize
            val item = p.inventory.getOrNull(i)
            val isSel = item != null && item === selected
            Draw.panel(
                c, x + app.s(3f), y + app.s(3f), x + slotSize - app.s(3f), y + slotSize - app.s(3f),
                app.s(9f),
                if (isSel) Palette.mix(Palette.PANEL_LIGHT, Palette.EMBER, 0.3f) else Palette.PANEL_LIGHT,
                if (isSel) Palette.EMBER else Palette.PANEL_STROKE, app.s(1.4f)
            )
            if (item != null) {
                val spr = when (item.type.kind) {
                    ItemKind.WEAPON -> Spr.SWORD
                    ItemKind.ARMOR -> Spr.ARMOR
                    ItemKind.POTION -> Spr.POTION
                    ItemKind.SCROLL -> Spr.SCROLL
                    ItemKind.GOLD -> Spr.GOLD
                }
                val size = (slotSize * 0.68f).toInt()
                val bmp = app.sprites.get(spr, size, tint = item.type.color and 0xFFFFFF)
                c.drawBitmap(bmp, x + (slotSize - size) / 2f, y + (slotSize - size) / 2f, null)
                if (item.count > 1) {
                    Draw.label(
                        c, "${item.count}", x + slotSize - app.s(10f), y + slotSize - app.s(9f),
                        app.s(12f), Palette.GOLD, Paint.Align.RIGHT, Draw.sansBold
                    )
                }
            }
        }

        // Selected item description.
        val descY = gridY + ROWS * slotSize + app.s(26f)
        val sel = selected
        if (sel != null) {
            Draw.label(
                c, sel.type.display, panelL + app.s(20f), descY, app.s(15f),
                Palette.EMBER_BRIGHT, font = Draw.sansBold
            )
            Draw.label(
                c, sel.type.desc, panelL + app.s(20f), descY + app.s(19f),
                app.s(12f), Palette.TEXT_DIM
            )
            useBtn.label = when (sel.type.kind) {
                ItemKind.WEAPON, ItemKind.ARMOR -> "Equip"
                ItemKind.POTION -> "Drink"
                ItemKind.SCROLL -> "Read"
                else -> "Use"
            }
            useBtn.enabled = true
            dropBtn.enabled = true
        } else {
            Draw.label(
                c, "Select an item...", panelL + app.s(20f), descY, app.s(13f),
                Palette.TEXT_FAINT
            )
            useBtn.enabled = false
            dropBtn.enabled = false
        }

        for (b in buttons) b.render(c, app)
    }

    override fun onTouch(e: TouchEvent) {
        for (b in buttons) if (b.touch(e, app)) return
        if (e.type != TouchEvent.UP) return
        // Slot taps.
        if (e.x >= gridX && e.x < gridX + COLS * slotSize &&
            e.y >= gridY && e.y < gridY + ROWS * slotSize
        ) {
            val col = ((e.x - gridX) / slotSize).toInt()
            val row = ((e.y - gridY) / slotSize).toInt()
            val idx = row * COLS + col
            val item = state.player.inventory.getOrNull(idx)
            if (item != null) {
                selected = item
                app.audio.play(Sfx.CLICK, 0.5f)
            }
            return
        }
        // Tap outside the panel closes.
        if (e.x < panelL || e.x > panelR || e.y < panelT || e.y > panelB) app.pop()
    }

    override fun onBack(): Boolean {
        app.pop()
        return true
    }

    private companion object {
        const val COLS = 5
        const val ROWS = 2
    }
}
