package com.emberdeep.game.systems

import com.emberdeep.game.core.Palette
import com.emberdeep.game.core.Rng
import com.emberdeep.game.gen.DungeonGenerator
import com.emberdeep.game.model.ClassType
import com.emberdeep.game.model.GameState
import com.emberdeep.game.model.ItemType
import com.emberdeep.game.model.Player

/**
 * Builds a brand-new run.
 *
 * Deliberately free of Android types so the exact code path the player takes
 * when starting an expedition is also the one exercised by the headless
 * balance simulation (`app/src/test`).
 */
object RunSetup {

    /** Healing potions the hero starts with. */
    const val STARTING_POTIONS = 1

    fun newRun(cls: ClassType, seed: Long = System.nanoTime()): GameState {
        val rng = Rng(seed)
        val first = DungeonGenerator.generate(1, rng)
        val player = Player(cls).apply {
            x = first.startX
            y = first.startY
            snapDraw()
            repeat(STARTING_POTIONS) { addItem(ItemType.POTION_HEAL) }
        }
        val state = GameState(1, player, first.map, rng)
        state.enemies.addAll(first.enemies)
        state.addLog("You enter the Emberdeep. Find the stairs down.", Palette.EMBER_BRIGHT)
        state.addLog("Tap a tile to move. Tap an adjacent enemy to attack.", Palette.TEXT_DIM)
        Fov.compute(state.map, player.x, player.y, TurnEngine.FOV_RADIUS)
        return state
    }

    /** True when a run can no longer be continued (hero dead or dragon slain). */
    fun isFinished(state: GameState): Boolean =
        state.player.hp <= 0 || state.bossDefeated
}
