package com.emberdeep.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.emberdeep.game.core.Btn
import com.emberdeep.game.core.Draw
import com.emberdeep.game.core.GameApp
import com.emberdeep.game.core.Palette
import com.emberdeep.game.core.Particles
import com.emberdeep.game.core.PausableScreen
import com.emberdeep.game.core.Rng
import com.emberdeep.game.core.Screen
import com.emberdeep.game.core.Sfx
import com.emberdeep.game.core.Spr
import com.emberdeep.game.core.TouchEvent
import com.emberdeep.game.gen.DungeonGenerator
import com.emberdeep.game.model.ClassType
import com.emberdeep.game.model.DungeonMap
import com.emberdeep.game.model.Enemy
import com.emberdeep.game.model.EnemyType
import com.emberdeep.game.model.GameState
import com.emberdeep.game.model.ItemKind
import com.emberdeep.game.model.ItemType
import com.emberdeep.game.model.Player
import com.emberdeep.game.systems.Fov
import com.emberdeep.game.systems.Pathfinder
import com.emberdeep.game.systems.TurnEngine
import com.emberdeep.game.systems.TurnEvents

class GameScreen(
    app: GameApp,
    private val state: GameState
) : Screen(app), TurnEvents, PausableScreen {

    private val turnEngine = TurnEngine(state, this) { app.saves.saveRun(state) }

    private var tile = 48f
    private var hudTop = 0f
    private var hudBottom = 0f

    private var camX = 0f
    private var camY = 0f
    private var camSnap = true

    private val particles = Particles(320)
    private val fxRng = Rng()

    // Floating combat text pool.
    private val ftText = arrayOfNulls<String>(MAX_FLOATS)
    private val ftX = FloatArray(MAX_FLOATS)
    private val ftY = FloatArray(MAX_FLOATS)
    private val ftLife = FloatArray(MAX_FLOATS)
    private val ftColor = IntArray(MAX_FLOATS)
    private val ftBig = BooleanArray(MAX_FLOATS)
    private var ftCursor = 0

    private var shake = 0f
    private var autoPath: MutableList<IntArray> = ArrayList()
    private var stepTimer = 0f
    private var targetMode = TARGET_NONE
    private var endTimer = -1f
    private var endVictory = false
    private var floorFlash = 0f

    private var lightShader: RadialGradient? = null
    private var minimapBmp: Bitmap? = null
    private var minimapTimer = 0f

    private var downX = 0f
    private var downY = 0f

    private val abilityBtn = Btn("") { onAbilityPressed() }
    private val waitBtn = Btn("") { playerAct { turnEngine.waitTurn() } }
    private val bagBtn = Btn("") { app.push(InventoryOverlay(app, turnEngine, state)) }
    private val menuBtn = Btn("") { app.push(PauseOverlay(app, state)) }
    private val descendBtn = Btn("Descend the Stairs") {
        playerAct { turnEngine.descend() }
    }
    private val cancelTargetBtn = Btn("Cancel") { targetMode = TARGET_NONE }
    private val buttons get() = listOf(abilityBtn, waitBtn, bagBtn, menuBtn, descendBtn, cancelTargetBtn)

    init {
        turnEngine.refreshFov()
        layout()
    }

    // ------------------------------------------------------------------ //

    override fun onResize() = layout()

    private fun layout() {
        val w = app.width.toFloat()
        val h = app.height.toFloat()
        tile = minOf(w / 9f, h / 13f)
        hudTop = app.s(92f)
        hudBottom = h - app.s(96f)

        val bh = app.s(62f)
        val bw = (w - app.s(48f)) / 4f
        val by = h - app.s(78f)
        var bx = app.s(10f)
        for (b in listOf(abilityBtn, waitBtn, bagBtn, menuBtn)) {
            b.layout(bx, by, bw, bh)
            bx += bw + app.s(9f)
        }
        abilityBtn.icon = when (state.player.classType) {
            ClassType.FIGHTER -> Spr.ICON_WHIRLWIND
            ClassType.ROGUE -> Spr.ICON_SHADOW
            ClassType.MAGE -> Spr.ICON_FIREBOLT
        }
        waitBtn.icon = Spr.ICON_WAIT
        bagBtn.icon = Spr.ICON_BAG
        menuBtn.label = "| |"
        menuBtn.textSize = app.s(18f)

        val dw = minOf(app.s(240f), w * 0.7f)
        descendBtn.layout((w - dw) / 2f, hudBottom - app.s(56f), dw, app.s(46f))
        descendBtn.accent = true
        descendBtn.icon = Spr.ICON_DESCEND

        cancelTargetBtn.layout(w - app.s(110f), hudTop + app.s(10f), app.s(96f), app.s(40f))

        lightShader = RadialGradient(
            0f, 0f, tile * (TurnEngine.FOV_RADIUS + 1.5f),
            intArrayOf(0, 0, Palette.alpha(Palette.BG_DEEP, 235)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        camSnap = true
        rebuildMinimap()
    }

    companion object {
        private const val MAX_FLOATS = 24
        private const val TARGET_NONE = 0
        private const val TARGET_TILE = 1
        private const val TARGET_ENEMY = 2

        /** Create a brand-new run starting on floor 1. */
        fun newRun(app: GameApp, cls: ClassType): GameState {
            val rng = Rng()
            val data = DungeonGenerator.generate(1, rng)
            val player = Player(cls)
            player.x = data.startX
            player.y = data.startY
            player.snapDraw()
            player.addItem(ItemType.POTION_HEAL)
            val st = GameState(1, player, data.map, rng)
            st.enemies.addAll(data.enemies)
            st.addLog("You enter the Emberdeep. Find the stairs down.", Palette.EMBER_BRIGHT)
            st.addLog("Tap a tile to move. Tap an adjacent enemy to attack.", Palette.TEXT_DIM)
            Fov.compute(st.map, player.x, player.y, TurnEngine.FOV_RADIUS)
            app.profile.runs++
            app.saveProfile()
            app.saves.saveRun(st)
            return st
        }
    }

    // ------------------------------------------------------------------ //
    // Update
    // ------------------------------------------------------------------ //

    override fun update(dt: Float) {
        val p = state.player
        p.updateAnim(dt)
        for (e in state.enemies) e.updateAnim(dt)
        particles.update(dt)
        updateFloats(dt)
        if (shake > 0f) shake = (shake - dt * 26f).coerceAtLeast(0f)
        if (floorFlash > 0f) floorFlash = (floorFlash - dt * 1.4f).coerceAtLeast(0f)
        for (l in state.log) l.age += dt

        // Camera follow.
        val tx = (p.drawX + 0.5f) * tile
        val ty = (p.drawY + 0.5f) * tile
        if (camSnap) {
            camX = tx; camY = ty; camSnap = false
        } else {
            val k = (dt * 6f).coerceAtMost(1f)
            camX += (tx - camX) * k
            camY += (ty - camY) * k
        }

        // Ambient embers from vents.
        spawnVentEmbers(dt)

        // Auto-walk along the queued path.
        if (autoPath.isNotEmpty() && endTimer < 0f) {
            stepTimer -= dt
            if (stepTimer <= 0f && !p.animating) {
                val step = autoPath.removeAt(0)
                val moved = turnEngine.tryMove(step[0] - p.x, step[1] - p.y)
                stepTimer = 0.1f
                if (!moved || anyVisibleAwakeEnemy() || turnEngine.gameEnded || p.burnTurns > 0) {
                    autoPath.clear()
                }
            }
        }

        // Contextual descend button.
        descendBtn.visible = !turnEngine.gameEnded &&
            p.x == state.map.stairsX && p.y == state.map.stairsY &&
            state.floor < DungeonGenerator.FINAL_FLOOR + 1
        cancelTargetBtn.visible = targetMode != TARGET_NONE

        // Ability button state.
        val cls = p.classType
        abilityBtn.enabled = turnEngine.abilityReady()
        abilityBtn.cooldownFraction =
            if (p.abilityCd <= 0) 0f else p.abilityCd.toFloat() / cls.abilityCooldown

        // Minimap refresh.
        minimapTimer -= dt
        if (minimapTimer <= 0f) {
            rebuildMinimap()
            minimapTimer = 0.6f
        }

        // End-of-run transition after a short dramatic pause.
        if (endTimer > 0f) {
            endTimer -= dt
            if (endTimer <= 0f) {
                if (endVictory) {
                    app.setScreen(VictoryScreen(app, state))
                } else {
                    app.setScreen(GameOverScreen(app, state))
                }
            }
        }
    }

    private fun anyVisibleAwakeEnemy(): Boolean {
        for (e in state.enemies) {
            if (e.hp > 0 && e.awake && state.map.isVisible(e.x, e.y)) return true
        }
        return false
    }

    private fun spawnVentEmbers(dt: Float) {
        val map = state.map
        if (!fxRng.chance(dt * 6f)) return
        val range = visibleTileRange()
        for (attempt in 0 until 6) {
            val x = fxRng.range(range[0], range[2])
            val y = fxRng.range(range[1], range[3])
            if (map.inBounds(x, y) && map.tile(x, y) == DungeonMap.VENT && map.isVisible(x, y)) {
                particles.emberRise(
                    (x + 0.5f) * tile, (y + 0.6f) * tile, tile * 0.5f, tile * 0.8f, tile * 0.06f
                )
                return
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Player intent
    // ------------------------------------------------------------------ //

    private fun playerAct(action: () -> Boolean) {
        if (turnEngine.gameEnded || endTimer >= 0f) return
        autoPath.clear()
        action()
    }

    private fun onAbilityPressed() {
        if (!turnEngine.abilityReady()) return
        when (state.player.classType) {
            ClassType.FIGHTER -> playerAct { turnEngine.useWhirlwind() }
            ClassType.ROGUE -> {
                targetMode = TARGET_TILE
                state.addLog("Choose a visible tile to shadowstep to.", Palette.INFO)
            }
            ClassType.MAGE -> {
                val any = state.enemies.any { it.hp > 0 && state.map.isVisible(it.x, it.y) }
                if (any) {
                    targetMode = TARGET_ENEMY
                    state.addLog("Choose a visible enemy to firebolt.", Palette.INFO)
                } else {
                    state.addLog("No enemies in sight.", Palette.TEXT_DIM)
                }
            }
        }
    }

    private fun handleWorldTap(sx: Float, sy: Float) {
        if (turnEngine.gameEnded || endTimer >= 0f) return
        val p = state.player
        val wx = sx - app.width / 2f + camX
        val wy = sy - worldCenterY() + camY
        val txF = wx / tile
        val tyF = wy / tile
        if (txF < 0 || tyF < 0) return
        val tx = txF.toInt()
        val ty = tyF.toInt()
        val map = state.map
        if (!map.inBounds(tx, ty)) return

        if (targetMode == TARGET_TILE) {
            targetMode = TARGET_NONE
            playerAct { turnEngine.useShadowstep(tx, ty) }
            return
        }
        if (targetMode == TARGET_ENEMY) {
            val enemy = state.enemyAt(tx, ty)
            targetMode = TARGET_NONE
            if (enemy != null && map.isVisible(tx, ty)) {
                playerAct { turnEngine.useFirebolt(enemy) }
            } else {
                state.addLog("Firebolt fizzles - no target chosen.", Palette.TEXT_DIM)
            }
            return
        }

        // Tap self: wait (or descend via button).
        if (tx == p.x && ty == p.y) {
            playerAct { turnEngine.waitTurn() }
            return
        }

        val enemy = state.enemyAt(tx, ty)
        val dist = Pathfinder.dist(p.x, p.y, tx, ty)
        if (enemy != null && map.isVisible(tx, ty)) {
            if (dist == 1) {
                playerAct { turnEngine.tryMove(tx - p.x, ty - p.y) }
            } else {
                // Walk toward the enemy; stop adjacent automatically.
                val path = Pathfinder.find(state, p.x, p.y, tx, ty, exploredOnly = true)
                if (path.isNotEmpty()) {
                    autoPath = path.toMutableList()
                    stepTimer = 0f
                }
            }
            return
        }

        if (!map.isExplored(tx, ty)) return
        if (!map.isWalkable(tx, ty)) {
            // Tapping a wall adjacent to the player does nothing; ignore.
            return
        }

        if (anyVisibleAwakeEnemy()) {
            // In combat: move exactly one careful step.
            val path = Pathfinder.find(state, p.x, p.y, tx, ty, exploredOnly = true)
            if (path.isNotEmpty()) {
                val step = path.first()
                playerAct { turnEngine.tryMove(step[0] - p.x, step[1] - p.y) }
            }
            return
        }

        val path = Pathfinder.find(state, p.x, p.y, tx, ty, exploredOnly = true)
        if (path.isNotEmpty()) {
            autoPath = path.toMutableList()
            stepTimer = 0f
        }
    }

    override fun onTouch(e: TouchEvent) {
        for (b in buttons) if (b.visible && b.touch(e, app)) return
        when (e.type) {
            TouchEvent.DOWN -> { downX = e.x; downY = e.y }
            TouchEvent.UP -> {
                if (Math.abs(e.x - downX) < app.s(20f) && Math.abs(e.y - downY) < app.s(20f)) {
                    if (e.y > hudTop && e.y < hudBottom) handleWorldTap(e.x, e.y)
                }
            }
        }
    }

    override fun onBack(): Boolean {
        if (targetMode != TARGET_NONE) {
            targetMode = TARGET_NONE
            return true
        }
        if (endTimer >= 0f) return true
        app.push(PauseOverlay(app, state))
        return true
    }

    override fun onAppPause() {
        if (!turnEngine.gameEnded) app.saves.saveRun(state)
    }

    // ------------------------------------------------------------------ //
    // Rendering
    // ------------------------------------------------------------------ //

    private fun worldCenterY(): Float = hudTop + (hudBottom - hudTop) / 2f

    private fun visibleTileRange(): IntArray {
        val halfW = app.width / 2f
        val halfH = maxOf(worldCenterY(), app.height - worldCenterY())
        val x0 = ((camX - halfW) / tile).toInt() - 1
        val y0 = ((camY - halfH) / tile).toInt() - 1
        val x1 = ((camX + halfW) / tile).toInt() + 1
        val y1 = ((camY + halfH) / tile).toInt() + 1
        return intArrayOf(
            x0.coerceAtLeast(0), y0.coerceAtLeast(0),
            x1.coerceAtMost(state.map.width - 1), y1.coerceAtMost(state.map.height - 1)
        )
    }

    override fun render(c: Canvas) {
        val w = app.width.toFloat()
        c.drawColor(Palette.BG_DEEP)

        val shakeX = if (shake > 0f) (fxRng.nextFloat() - 0.5f) * shake else 0f
        val shakeY = if (shake > 0f) (fxRng.nextFloat() - 0.5f) * shake else 0f

        c.save()
        c.translate(w / 2f - camX + shakeX, worldCenterY() - camY + shakeY)
        renderWorld(c)
        c.restore()

        renderLightVignette(c, shakeX, shakeY)
        renderHud(c)
    }

    private fun renderWorld(c: Canvas) {
        val map = state.map
        val r = visibleTileRange()
        val ts = tile.toInt() + 1
        val paint = Draw.fill

        for (y in r[1]..r[3]) {
            for (x in r[0]..r[2]) {
                val i = map.idx(x, y)
                if (!map.explored[i]) continue
                val t = map.tiles[i]
                val spr = when (t) {
                    DungeonMap.WALL -> Spr.WALL
                    DungeonMap.DOOR -> Spr.DOOR
                    DungeonMap.STAIRS -> Spr.STAIRS
                    DungeonMap.VENT -> Spr.VENT
                    else -> Spr.FLOOR
                }
                val bmp = app.sprites.get(spr, ts, map.variant[i].toInt())
                c.drawBitmap(bmp, x * tile, y * tile, null)
                if (!map.visible[i]) {
                    paint.color = Palette.alpha(Palette.BG_DEEP, 165)
                    paint.shader = null
                    c.drawRect(x * tile, y * tile, x * tile + ts, y * tile + ts, paint)
                }
            }
        }

        // Ground items.
        for (gi in map.groundItems) {
            if (!map.isVisible(gi.x, gi.y)) continue
            val spr = sprFor(gi.item.type)
            val size = (tile * 0.72f).toInt()
            val bmp = app.sprites.get(spr, size, tint = gi.item.type.color and 0xFFFFFF)
            c.drawBitmap(
                bmp,
                gi.x * tile + (tile - size) / 2f,
                gi.y * tile + (tile - size) / 2f,
                null
            )
        }

        // Enemies.
        for (e in state.enemies) {
            if (e.hp <= 0) continue
            if (!map.isVisible(e.x, e.y)) continue
            renderEntity(c, e)
        }

        // Player.
        renderEntity(c, null)

        particles.render(c)
        renderFloats(c)
    }

    private fun sprFor(t: ItemType): Spr = when (t.kind) {
        ItemKind.WEAPON -> Spr.SWORD
        ItemKind.ARMOR -> Spr.ARMOR
        ItemKind.POTION -> Spr.POTION
        ItemKind.SCROLL -> Spr.SCROLL
        ItemKind.GOLD -> Spr.GOLD
    }

    /** Renders an enemy, or the player when [enemy] is null. */
    private fun renderEntity(c: Canvas, enemy: Enemy?) {
        val ent = enemy ?: state.player
        val isDragon = enemy?.type == EnemyType.EMBER_DRAGON
        val scale = if (isDragon) 1.55f else 1f
        val size = (tile * 0.92f * scale).toInt()
        val lunge = tile * 0.3f
        val ex = ent.drawX * tile + (tile - size) / 2f + ent.lungeX * ent.lungeT * lunge
        val ey = ent.drawY * tile + (tile - size) / 2f + ent.lungeY * ent.lungeT * lunge -
            (if (isDragon) tile * 0.3f else 0f)

        if (ent.hitFlash > 0f) {
            Draw.fill.color = Palette.alpha(Palette.BAD, (ent.hitFlash * 150).toInt())
            c.drawCircle(
                ent.drawX * tile + tile / 2f, ent.drawY * tile + tile / 2f,
                tile * 0.55f * scale, Draw.fill
            )
        }

        val spr = if (enemy == null) {
            when (state.player.classType) {
                ClassType.FIGHTER -> Spr.HERO_FIGHTER
                ClassType.ROGUE -> Spr.HERO_ROGUE
                ClassType.MAGE -> Spr.HERO_MAGE
            }
        } else {
            when (enemy.type) {
                EnemyType.RAT -> Spr.RAT
                EnemyType.GOBLIN -> Spr.GOBLIN
                EnemyType.SKELETON -> Spr.SKELETON
                EnemyType.SKELETON_ARCHER -> Spr.SKELETON_ARCHER
                EnemyType.ORC -> Spr.ORC
                EnemyType.CULTIST -> Spr.CULTIST
                EnemyType.WRAITH -> Spr.WRAITH
                EnemyType.TROLL -> Spr.TROLL
                EnemyType.EMBER_DRAGON -> Spr.DRAGON
            }
        }
        c.drawBitmap(app.sprites.get(spr, size), ex, ey, null)

        // Status pips.
        if (enemy != null) {
            if (enemy.hp < enemy.maxHp) {
                Draw.bar(
                    c, ent.drawX * tile + tile * 0.12f, ent.drawY * tile - tile * 0.07f,
                    tile * 0.76f, tile * 0.08f,
                    enemy.hp.toFloat() / enemy.maxHp, Palette.HP, Palette.HP_DARK
                )
            }
            if (enemy.burnTurns > 0 && fxRng.chance(0.15f)) {
                particles.emberRise(
                    ent.drawX * tile + tile * 0.5f, ent.drawY * tile + tile * 0.3f,
                    tile * 0.4f, tile * 0.7f, tile * 0.05f
                )
            }
            if (!enemy.awake) {
                Draw.label(
                    c, "z", ent.drawX * tile + tile * 0.82f, ent.drawY * tile + tile * 0.16f,
                    tile * 0.3f, Palette.TEXT_DIM, font = Draw.sansBold
                )
            }
        } else {
            val p = state.player
            if (p.shieldTurns > 0) {
                Draw.stroke.color = Palette.alpha(Palette.MANA, 160)
                Draw.stroke.strokeWidth = app.s(2.4f)
                c.drawCircle(
                    p.drawX * tile + tile / 2f, p.drawY * tile + tile / 2f,
                    tile * 0.58f, Draw.stroke
                )
            }
            if (p.strengthTurns > 0) {
                Draw.stroke.color = Palette.alpha(Palette.EMBER_BRIGHT, 140)
                Draw.stroke.strokeWidth = app.s(2f)
                c.drawCircle(
                    p.drawX * tile + tile / 2f, p.drawY * tile + tile / 2f,
                    tile * 0.66f, Draw.stroke
                )
            }
            if (p.burnTurns > 0 && fxRng.chance(0.2f)) {
                particles.emberRise(
                    p.drawX * tile + tile * 0.5f, p.drawY * tile + tile * 0.3f,
                    tile * 0.4f, tile * 0.7f, tile * 0.05f
                )
            }
        }
    }

    private fun renderLightVignette(c: Canvas, shakeX: Float, shakeY: Float) {
        val shader = lightShader ?: return
        val px = app.width / 2f + (state.player.drawX * tile + tile / 2f - camX) + shakeX
        val py = worldCenterY() + (state.player.drawY * tile + tile / 2f - camY) + shakeY
        c.save()
        c.translate(px, py)
        Draw.fill.shader = shader
        c.drawRect(-px, -py, app.width - px, app.height - py, Draw.fill)
        Draw.fill.shader = null
        c.restore()

        if (floorFlash > 0f) {
            Draw.fill.color = Palette.alpha(0xFF000000.toInt(), (floorFlash * 255).toInt())
            c.drawRect(0f, 0f, app.width.toFloat(), app.height.toFloat(), Draw.fill)
        }
    }

    // ------------------------------------------------------------------ //
    // HUD
    // ------------------------------------------------------------------ //

    private fun renderHud(c: Canvas) {
        val w = app.width.toFloat()
        val p = state.player

        // Top panel.
        Draw.panel(c, app.s(6f), app.s(6f), w - app.s(6f), hudTop - app.s(6f), app.s(14f))
        val pad = app.s(16f)
        val barW = w * 0.44f

        Draw.label(
            c, "${p.classType.display}  Lv ${p.level}", pad, app.s(27f),
            app.s(14f), Palette.TEXT, font = Draw.sansBold
        )
        Draw.bar(c, pad, app.s(36f), barW, app.s(15f), p.hp.toFloat() / p.maxHp, Palette.HP, Palette.HP_DARK)
        Draw.label(
            c, "${p.hp}/${p.maxHp}", pad + barW / 2f, app.s(48.5f), app.s(11f),
            Palette.TEXT, Paint.Align.CENTER, Draw.sansBold
        )
        Draw.bar(
            c, pad, app.s(57f), barW, app.s(7f),
            p.xp.toFloat() / p.xpToNext, Palette.XP, Palette.XP_DARK
        )
        var statusY = app.s(76f)
        val statuses = ArrayList<String>()
        if (p.strengthTurns > 0) statuses.add("Might ${p.strengthTurns}")
        if (p.shieldTurns > 0) statuses.add("Stone ${p.shieldTurns}")
        if (p.burnTurns > 0) statuses.add("BURNING ${p.burnTurns}")
        if (p.autoCrit) statuses.add("Lethal")
        if (statuses.isNotEmpty()) {
            Draw.label(
                c, statuses.joinToString("  "), pad, statusY, app.s(11f),
                if (p.burnTurns > 0) Palette.EMBER else Palette.INFO, font = Draw.sansBold
            )
        } else {
            Draw.label(
                c, "ATK +${p.atkBonus}  AC ${p.acTotal}  DMG ${p.damageLabel}",
                pad, statusY, app.s(11f), Palette.TEXT_DIM
            )
        }

        Draw.label(
            c, "Floor ${state.floor}", w - pad, app.s(27f), app.s(16f),
            Palette.EMBER_BRIGHT, Paint.Align.RIGHT, Draw.sansBold
        )
        Draw.label(
            c, "${p.gold} g", w - pad, app.s(48f), app.s(14f),
            Palette.GOLD, Paint.Align.RIGHT, Draw.sansBold
        )

        // Boss bar.
        val dragon = state.enemies.firstOrNull { it.type == EnemyType.EMBER_DRAGON && it.hp > 0 }
        if (dragon != null && dragon.awake) {
            val bw2 = w * 0.76f
            val bx = (w - bw2) / 2f
            val by = hudTop + app.s(8f)
            Draw.label(
                c, "THE EMBER DRAGON", w / 2f, by + app.s(2f), app.s(12f),
                Palette.BAD, Paint.Align.CENTER, Draw.sansBold
            )
            Draw.bar(
                c, bx, by + app.s(7f), bw2, app.s(10f),
                dragon.hp.toFloat() / dragon.maxHp, Palette.BAD, Palette.HP_DARK
            )
        }

        // Minimap.
        if (app.profile.minimap) {
            minimapBmp?.let {
                val mx = w - it.width - app.s(10f)
                val my = hudTop + app.s(10f) + (if (dragon != null && dragon.awake) app.s(22f) else 0f)
                Draw.fill.color = Palette.alpha(0xFF000000.toInt(), 110)
                c.drawRoundRect(
                    mx - app.s(3f), my - app.s(3f),
                    mx + it.width + app.s(3f), my + it.height + app.s(3f),
                    app.s(4f), app.s(4f), Draw.fill
                )
                c.drawBitmap(it, mx, my, null)
            }
        }

        // Message log.
        val logLines = state.log.takeLast(5)
        var ly = hudBottom - app.s(14f)
        for (i in logLines.indices.reversed()) {
            val l = logLines[i]
            val alpha = when {
                l.age < 5f -> 255
                l.age < 7f -> ((7f - l.age) / 2f * 255).toInt()
                else -> 0
            }
            if (alpha <= 0) continue
            Draw.label(
                c, l.text, app.s(12f), ly, app.s(13f), l.color,
                alpha = (alpha * 0.92f).toInt()
            )
            ly -= app.s(18f)
        }

        // Targeting banner.
        if (targetMode != TARGET_NONE) {
            Draw.panel(
                c, w * 0.08f, hudTop + app.s(8f), w * 0.92f, hudTop + app.s(44f),
                app.s(10f), Palette.alpha(Palette.PANEL_SOLID, 230), Palette.EMBER, app.s(2f)
            )
            Draw.label(
                c,
                if (targetMode == TARGET_TILE) "Tap a visible tile to shadowstep" else "Tap an enemy to firebolt",
                w / 2f, hudTop + app.s(31f), app.s(14f), Palette.EMBER_BRIGHT,
                Paint.Align.CENTER, Draw.sansBold
            )
        }

        // Ability cooldown label.
        if (!abilityBtn.enabled && state.player.abilityCd > 0) {
            Draw.label(
                c, "${state.player.abilityCd}",
                abilityBtn.x + abilityBtn.w / 2f, abilityBtn.y + abilityBtn.h * 0.62f,
                app.s(18f), Palette.TEXT, Paint.Align.CENTER, Draw.sansBold
            )
        }

        for (b in buttons) b.render(c, app)
    }

    private fun rebuildMinimap() {
        val map = state.map
        val cell = 3
        val bmp = minimapBmp?.takeIf {
            it.width == map.width * cell && it.height == map.height * cell
        } ?: Bitmap.createBitmap(
            map.width * cell, map.height * cell, Bitmap.Config.ARGB_8888
        ).also { minimapBmp = it }

        bmp.eraseColor(0x00000000)
        val c = Canvas(bmp)
        val paint = Paint()
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val i = map.idx(x, y)
                if (!map.explored[i]) continue
                paint.color = when (map.tiles[i]) {
                    DungeonMap.WALL -> 0xFF3A3040.toInt()
                    DungeonMap.STAIRS -> Palette.EMBER
                    else -> 0xFF706068.toInt()
                }
                c.drawRect(
                    (x * cell).toFloat(), (y * cell).toFloat(),
                    ((x + 1) * cell).toFloat(), ((y + 1) * cell).toFloat(), paint
                )
            }
        }
        paint.color = Palette.GOOD
        c.drawRect(
            (state.player.x * cell - 1).toFloat(), (state.player.y * cell - 1).toFloat(),
            (state.player.x * cell + cell + 1).toFloat(),
            (state.player.y * cell + cell + 1).toFloat(), paint
        )
    }

    // ------------------------------------------------------------------ //
    // Floating text
    // ------------------------------------------------------------------ //

    private fun addFloat(x: Int, y: Int, text: String, color: Int, big: Boolean) {
        val i = ftCursor
        ftCursor = (ftCursor + 1) % MAX_FLOATS
        ftText[i] = text
        ftX[i] = (x + 0.5f) * tile + (fxRng.nextFloat() - 0.5f) * tile * 0.3f
        ftY[i] = y * tile + tile * 0.15f
        ftLife[i] = 1f
        ftColor[i] = color
        ftBig[i] = big
    }

    private fun updateFloats(dt: Float) {
        for (i in 0 until MAX_FLOATS) {
            if (ftLife[i] > 0f) {
                ftLife[i] -= dt * 0.9f
                ftY[i] -= dt * tile * 1.1f
            }
        }
    }

    private fun renderFloats(c: Canvas) {
        for (i in 0 until MAX_FLOATS) {
            val t = ftText[i] ?: continue
            if (ftLife[i] <= 0f) continue
            val alpha = (ftLife[i].coerceAtMost(0.7f) / 0.7f * 255).toInt()
            Draw.label(
                c, t, ftX[i], ftY[i],
                if (ftBig[i]) tile * 0.52f else tile * 0.38f,
                ftColor[i], Paint.Align.CENTER, Draw.sansBold, alpha
            )
        }
    }

    // ------------------------------------------------------------------ //
    // TurnEvents — gameplay → juice
    // ------------------------------------------------------------------ //

    override fun onFloatText(x: Int, y: Int, text: String, color: Int, big: Boolean) {
        addFloat(x, y, text, color, big)
    }

    override fun onStruck(x: Int, y: Int, color: Int, heavy: Boolean) {
        particles.burst(
            (x + 0.5f) * tile, (y + 0.5f) * tile, color,
            if (heavy) 18 else 10, tile * 3f, tile * 0.07f, tile * 4f
        )
        app.audio.play(if (heavy) Sfx.CRIT else Sfx.HIT)
        app.vibrate(if (heavy) 40 else 18, heavy)
        if (heavy) shake = tile * 0.25f
    }

    override fun onMissEffect(x: Int, y: Int) {
        app.audio.play(Sfx.MISS, 0.6f)
        addFloat(x, y, "miss", Palette.TEXT_FAINT, false)
    }

    override fun onFire(x: Int, y: Int) {
        particles.burst(
            (x + 0.5f) * tile, (y + 0.5f) * tile, Palette.EMBER,
            16, tile * 2.6f, tile * 0.09f, -tile * 1.5f
        )
        particles.burst(
            (x + 0.5f) * tile, (y + 0.5f) * tile, Palette.EMBER_BRIGHT,
            8, tile * 1.8f, tile * 0.07f, -tile * 2f
        )
        app.audio.play(Sfx.FIRE)
    }

    override fun onHealEffect(x: Int, y: Int) {
        particles.burst(
            (x + 0.5f) * tile, (y + 0.4f) * tile, Palette.GOOD,
            12, tile * 1.4f, tile * 0.07f, -tile * 2.5f
        )
        app.audio.play(Sfx.HEAL)
    }

    override fun onEnemyDied(e: Enemy) {
        particles.burst(
            (e.x + 0.5f) * tile, (e.y + 0.5f) * tile, e.type.tint,
            22, tile * 3.2f, tile * 0.09f, tile * 3f
        )
        app.vibrate(25)
        if (e.type == EnemyType.EMBER_DRAGON) shake = tile * 0.5f
    }

    override fun onGoldPicked(amount: Int) {
        app.audio.play(Sfx.GOLD)
    }

    override fun onItemPicked(name: String) {
        app.audio.play(Sfx.PICKUP)
    }

    override fun onLevelUp() {
        app.audio.play(Sfx.LEVELUP)
        app.vibrate(60, strong = true)
        particles.burst(
            (state.player.x + 0.5f) * tile, (state.player.y + 0.5f) * tile,
            Palette.GOLD, 26, tile * 3f, tile * 0.08f, -tile * 2f
        )
    }

    override fun onPlayerDied() {
        autoPath.clear()
        targetMode = TARGET_NONE
        app.audio.play(Sfx.DEATH)
        app.vibrate(220, strong = true)
        shake = tile * 0.4f
        endVictory = false
        endTimer = 1.4f
        finalizeRun(victory = false)
    }

    override fun onVictory() {
        autoPath.clear()
        targetMode = TARGET_NONE
        app.audio.play(Sfx.VICTORY)
        app.vibrate(160, strong = true)
        endVictory = true
        endTimer = 1.6f
        finalizeRun(victory = true)
    }

    private fun finalizeRun(victory: Boolean) {
        val p = app.profile
        val player = state.player
        p.totalKills += player.kills
        if (state.floor > p.bestFloor) p.bestFloor = state.floor
        if (victory) {
            p.victories++
            p.treasury += player.gold + 300
        } else {
            p.treasury += player.gold / 2
        }
        app.saveProfile()
        app.saves.deleteRun()
    }

    override fun onDescended() {
        autoPath.clear()
        camSnap = true
        floorFlash = 1f
        particles.clear()
        app.audio.play(Sfx.STAIRS)
        app.vibrate(30)
        rebuildMinimap()
    }

    override fun onTeleport() {
        camSnap = true
        app.audio.play(Sfx.TELEPORT)
        particles.burst(
            (state.player.x + 0.5f) * tile, (state.player.y + 0.5f) * tile,
            Palette.INFO, 16, tile * 2f, tile * 0.07f
        )
    }

    override fun onDoorOpened() {
        app.audio.play(Sfx.DOOR)
    }

    override fun onPlayerHurt(heavy: Boolean) {
        app.audio.play(Sfx.HURT, if (heavy) 1f else 0.8f)
        app.vibrate(if (heavy) 60 else 25, heavy)
        shake = maxOf(shake, tile * (if (heavy) 0.3f else 0.14f))
    }

    override fun onAttackSwing(crit: Boolean, hit: Boolean) {
        // Covered by onStruck / onMissEffect sounds.
    }
}
