package com.emberdeep.game.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * The render surface and game loop. A dedicated thread runs update + render
 * at up to 60 fps with a clamped delta time, so gameplay never depends on
 * frame rate.
 */
class GameView(context: Context, private val app: GameApp) :
    SurfaceView(context), SurfaceHolder.Callback {

    @Volatile private var running = false
    @Volatile private var surfaceReady = false
    private var thread: Thread? = null

    init {
        holder.addCallback(this)
        isFocusable = true
        keepScreenOn = true
    }

    // --- Lifecycle ----------------------------------------------------- //

    fun resume() {
        running = true
        startThreadIfNeeded()
    }

    fun pause() {
        running = false
        joinThread()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startThreadIfNeeded()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        app.resize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        joinThread()
    }

    private fun startThreadIfNeeded() {
        if (!running || !surfaceReady || thread != null) return
        thread = Thread(::loop, "game-loop").also { it.start() }
    }

    private fun joinThread() {
        val t = thread ?: return
        thread = null
        try {
            t.join(1000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    // --- Game loop ------------------------------------------------------ //

    private fun loop() {
        var last = System.nanoTime()
        while (running && surfaceReady && thread === Thread.currentThread()) {
            val now = System.nanoTime()
            var dt = (now - last) / 1_000_000_000f
            last = now
            if (dt > MAX_DT) dt = MAX_DT

            app.update(dt)

            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
                if (canvas != null) app.render(canvas)
            } catch (e: Exception) {
                // Surface torn down mid-frame; loop exits via flags.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        // Ignore: surface already gone.
                    }
                }
            }

            // Frame cap ~60fps to save battery.
            val frameNs = System.nanoTime() - now
            val sleepMs = (TARGET_FRAME_NS - frameNs) / 1_000_000
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }
        // Allow a future resume to spawn a fresh thread.
        if (thread === Thread.currentThread()) thread = null
    }

    // --- Input ----------------------------------------------------------- //

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val type = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> TouchEvent.DOWN
            MotionEvent.ACTION_MOVE -> TouchEvent.MOVE
            MotionEvent.ACTION_UP -> TouchEvent.UP
            MotionEvent.ACTION_CANCEL -> TouchEvent.CANCEL
            else -> return true
        }
        // Single-pointer game: track pointer index 0 only.
        app.input.offer(TouchEvent(type, event.getX(0), event.getY(0)))
        if (type == TouchEvent.UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        const val MAX_DT = 1f / 15f
        const val TARGET_FRAME_NS = 16_666_666L
    }
}
