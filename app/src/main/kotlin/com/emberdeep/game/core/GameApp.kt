package com.emberdeep.game.core

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.emberdeep.game.data.Profile
import com.emberdeep.game.data.SaveManager
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Application-level game context: screen stack, services (audio, haptics,
 * sprites, saves) and the update/render pump driven by [GameView].
 */
class GameApp(val context: Context) {

    var width = 1
        private set
    var height = 1
        private set

    /** UI scale: 1.0 on a 392dp-wide baseline phone. */
    private var scale = 1f

    fun s(v: Float): Float = v * scale

    val sprites = Sprites()
    val saves = SaveManager(context)
    val profile: Profile = saves.loadProfile()
    val audio = Audio(context)
    val input = ConcurrentLinkedQueue<TouchEvent>()

    private val screens = ArrayList<Screen>()
    private val vibrator: Vibrator? =
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    init {
        audio.soundEnabled = profile.sound
        audio.musicEnabled = profile.music
    }

    // ------------------------------------------------------------------ //

    fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val changed = w != width || h != height
        width = w
        height = h
        scale = minOf(w / 392f, h / 700f)
        if (changed) {
            sprites.clear()
            synchronized(screens) {
                for (sc in screens) sc.onResize()
            }
        }
    }

    fun update(dt: Float) {
        // Drain touch input to the top screen only.
        var e = input.poll()
        val top = topScreen()
        while (e != null) {
            top?.onTouch(e)
            e = input.poll()
        }
        topScreen()?.update(dt)
    }

    fun render(c: Canvas) {
        c.drawColor(Palette.BG)
        val snapshot: List<Screen> = synchronized(screens) { ArrayList(screens) }
        if (snapshot.isEmpty()) return
        var start = snapshot.size - 1
        while (start > 0 && snapshot[start].isOverlay) start--
        for (i in start until snapshot.size) {
            snapshot[i].render(c)
        }
    }

    // ------------------------------------------------------------------ //

    fun topScreen(): Screen? = synchronized(screens) { screens.lastOrNull() }

    fun push(screen: Screen) {
        synchronized(screens) { screens.add(screen) }
        screen.onShow()
    }

    fun pop() {
        val removed = synchronized(screens) {
            if (screens.isNotEmpty()) screens.removeAt(screens.size - 1) else null
        }
        removed?.onHide()
    }

    /** Replace the whole stack with one screen. */
    fun setScreen(screen: Screen) {
        val old = synchronized(screens) {
            val copy = ArrayList(screens)
            screens.clear()
            screens.add(screen)
            copy
        }
        for (sc in old) sc.onHide()
        screen.onShow()
    }

    /** @return true when consumed; false means the activity may exit. */
    fun back(): Boolean {
        val top = topScreen() ?: return false
        if (top.onBack()) return true
        val count = synchronized(screens) { screens.size }
        if (count > 1) {
            pop()
            return true
        }
        return false
    }

    // ------------------------------------------------------------------ //

    fun vibrate(ms: Long, strong: Boolean = false) {
        if (!profile.haptics) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amp = if (strong) 255 else 80
                v.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (e: Exception) {
            // Some devices have no vibrator service despite reporting one.
        }
    }

    fun saveProfile() = saves.saveProfile(profile)

    /** Called from the activity lifecycle. */
    fun onAppPause() {
        audio.onPause()
        topScreen()?.let { if (it is PausableScreen) it.onAppPause() }
        saveProfile()
    }

    fun onAppResume() {
        audio.onResume()
    }

    fun onDestroy() {
        audio.release()
    }
}

/** Screens that must persist state when the app goes to the background. */
interface PausableScreen {
    fun onAppPause()
}
