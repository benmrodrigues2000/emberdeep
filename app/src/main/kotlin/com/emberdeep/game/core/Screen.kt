package com.emberdeep.game.core

import android.graphics.Canvas

/** A single touch event, delivered on the game thread. */
class TouchEvent(val type: Int, val x: Float, val y: Float) {
    companion object {
        const val DOWN = 0
        const val MOVE = 1
        const val UP = 2
        const val CANCEL = 3
    }
}

/**
 * Base class for every screen / overlay. Screens live on a stack managed by
 * [GameApp]; overlays render on top of the screen beneath them.
 */
abstract class Screen(protected val app: GameApp) {

    open val isOverlay: Boolean get() = false

    open fun onShow() {}
    open fun onHide() {}
    open fun onResize() {}
    open fun update(dt: Float) {}
    abstract fun render(c: Canvas)
    open fun onTouch(e: TouchEvent) {}

    /** @return true when the back press was consumed. */
    open fun onBack(): Boolean = false
}
