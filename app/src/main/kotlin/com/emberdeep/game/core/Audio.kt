package com.emberdeep.game.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

enum class Sfx {
    CLICK, HIT, CRIT, MISS, HURT, PICKUP, GOLD, LEVELUP,
    STAIRS, DEATH, VICTORY, FIRE, HEAL, DOOR, TELEPORT
}

/**
 * Fully procedural audio: every sound effect and the ambient music loop are
 * synthesized at startup, so the game ships with zero audio assets while
 * remaining easy to swap for real files later (replace [buildSfx]).
 */
class Audio(private val context: Context) {

    @Volatile var soundEnabled = true
    @Volatile var musicEnabled = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<Sfx, Int>()
    private val loaded = HashSet<Int>()

    @Volatile private var music: AudioTrack? = null
    @Volatile private var musicPlaying = false
    @Volatile private var paused = false

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(loaded) { loaded.add(sampleId) }
        }
        Thread({
            try {
                generateAll()
            } catch (e: Exception) {
                // Audio is non-critical; the game keeps running silently.
            }
        }, "audio-synth").start()
    }

    fun play(sfx: Sfx, volume: Float = 1f) {
        if (!soundEnabled) return
        val id = ids[sfx] ?: return
        val ok = synchronized(loaded) { loaded.contains(id) }
        if (ok) pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun setMusic(enabled: Boolean) {
        musicEnabled = enabled
        val track = music ?: return
        try {
            if (enabled && !paused) {
                if (!musicPlaying) { track.play(); musicPlaying = true }
            } else if (musicPlaying) {
                track.pause(); musicPlaying = false
            }
        } catch (e: Exception) {
            // Ignore transient AudioTrack state errors.
        }
    }

    fun onPause() {
        paused = true
        try {
            if (musicPlaying) { music?.pause(); musicPlaying = false }
        } catch (e: Exception) { /* ignore */ }
    }

    fun onResume() {
        paused = false
        setMusic(musicEnabled)
    }

    fun release() {
        try {
            pool.release()
            music?.release()
        } catch (e: Exception) { /* ignore */ }
    }

    // ------------------------------------------------------------------ //
    // Synthesis
    // ------------------------------------------------------------------ //

    private fun generateAll() {
        val dir = File(context.cacheDir, "sfx")
        dir.mkdirs()
        for (sfx in Sfx.entries) {
            val pcm = buildSfx(sfx)
            val f = File(dir, "${sfx.name.lowercase()}.wav")
            writeWav(f, pcm)
            val id = pool.load(f.absolutePath, 1)
            synchronized(ids) { ids[sfx] = id }
        }
        buildMusicTrack()
    }

    private fun buildSfx(sfx: Sfx): ShortArray = when (sfx) {
        Sfx.CLICK -> render(0.05f) { t, d ->
            sin(tau * t * slide(1100f, 700f, t / d)) * env(t, d, 0.004f, 1.2f) * 0.5f
        }
        Sfx.HIT -> mix(
            render(0.12f) { t, d -> noise(t) * env(t, d, 0.002f, 2.2f) * 0.55f },
            render(0.16f) { t, d -> sin(tau * t * slide(170f, 55f, t / d)) * env(t, d, 0.002f, 1.6f) * 0.9f }
        )
        Sfx.CRIT -> mix(
            render(0.14f) { t, d -> noise(t) * env(t, d, 0.002f, 2.0f) * 0.6f },
            render(0.2f) { t, d -> sin(tau * t * slide(190f, 50f, t / d)) * env(t, d, 0.002f, 1.4f) },
            render(0.25f) { t, d -> sin(tau * t * 1560f) * env(t, d, 0.01f, 3f) * 0.35f }
        )
        Sfx.MISS -> render(0.16f) { t, d ->
            noise(t) * sin(tau * t * slide(900f, 250f, t / d)) * env(t, d, 0.03f, 1.6f) * 0.4f
        }
        Sfx.HURT -> render(0.18f) { t, d ->
            square(t * slide(240f, 110f, t / d)) * env(t, d, 0.004f, 1.8f) * 0.35f
        }
        Sfx.PICKUP -> notes(floatArrayOf(660f, 990f), 0.07f, 0.5f)
        Sfx.GOLD -> notes(floatArrayOf(1320f, 1760f), 0.06f, 0.4f)
        Sfx.LEVELUP -> notes(floatArrayOf(440f, 554f, 659f, 880f), 0.1f, 0.5f)
        Sfx.STAIRS -> notes(floatArrayOf(392f, 311f, 247f), 0.12f, 0.45f)
        Sfx.DEATH -> mix(
            render(1.1f) { t, d -> saw(t * slide(220f, 50f, t / d)) * env(t, d, 0.01f, 1.3f) * 0.4f },
            render(1.1f) { t, d -> noise(t) * env(t, d, 0.2f, 2f) * 0.2f }
        )
        Sfx.VICTORY -> notes(floatArrayOf(523f, 659f, 784f, 1047f, 1319f), 0.14f, 0.5f)
        Sfx.FIRE -> mix(
            render(0.35f) { t, d -> noise(t) * env(t, d, 0.02f, 1.8f) * 0.45f },
            render(0.35f) { t, d -> sin(tau * t * slide(120f, 60f, t / d)) * env(t, d, 0.02f, 1.5f) * 0.5f }
        )
        Sfx.HEAL -> mix(
            render(0.4f) { t, d -> sin(tau * t * 523f) * env(t, d, 0.08f, 1.8f) * 0.3f },
            render(0.4f) { t, d -> sin(tau * t * 784f) * env(t, d, 0.15f, 1.8f) * 0.25f }
        )
        Sfx.DOOR -> render(0.14f) { t, d ->
            noise(t) * env(t, d, 0.004f, 2.4f) * 0.3f +
                sin(tau * t * 90f) * env(t, d, 0.004f, 2f) * 0.4f
        }
        Sfx.TELEPORT -> render(0.3f) { t, d ->
            sin(tau * t * slide(300f, 1200f, t / d)) * env(t, d, 0.02f, 1.6f) * 0.35f
        }
    }

    private fun buildMusicTrack() {
        if (music != null) return
        val seconds = 16f
        val n = (SR * seconds).toInt()
        val data = ShortArray(n)
        val rng = Rng(0x0E3B35DEE9L)
        // Slow two-chord pad: A minor -> F major, with a deep drone and
        // an occasional distant "forge" thump.
        val chordA = floatArrayOf(220f, 261.63f, 329.63f)
        val chordF = floatArrayOf(174.61f, 220f, 261.63f)
        var sparkle = 0f
        var sparkleFreq = 0f
        for (i in 0 until n) {
            val t = i / SR
            val phase = (t % seconds) / seconds
            val chord = if (phase < 0.5f) chordA else chordF
            val swell = 0.5f - 0.5f * kotlin.math.cos((tau * ((t * 0.125f) % 1f).toDouble())).toFloat()
            var v = 0f
            v += sin(tau * t * 55f) * 0.16f * (0.8f + 0.2f * sin(tau * t * 0.07f))
            for (f in chord) v += sin(tau * t * f) * 0.05f * swell
            // Forge thump every 4 seconds.
            val beat = (t % 4f)
            if (beat < 0.5f) v += sin(tau * beat * 48f) * exp(-beat * 9f) * 0.35f
            // Rare high sparkle.
            if (sparkle <= 0f && rng.chance(0.00004f)) {
                sparkle = 0.5f
                sparkleFreq = 1047f * (1 shl rng.nextInt(2))
            }
            if (sparkle > 0f) {
                val st = 0.5f - sparkle
                v += sin(tau * st * sparkleFreq) * exp(-st * 7f) * 0.06f
                sparkle -= 1f / SR
            }
            data[i] = (v.coerceIn(-1f, 1f) * 32767f * 0.8f).toInt().toShort()
        }
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR.toInt())
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(data.size * 2)
                .build()
            track.write(data, 0, data.size)
            track.setLoopPoints(0, data.size, -1)
            track.setVolume(0.6f)
            music = track
            if (musicEnabled && !paused) {
                track.play()
                musicPlaying = true
            }
        } catch (e: Exception) {
            music = null
        }
    }

    // --- tiny DSP toolkit -------------------------------------------- //

    private val tau = (2.0 * PI).toFloat()
    private var noiseState = 12345L

    private fun noise(@Suppress("UNUSED_PARAMETER") t: Float): Float {
        noiseState = noiseState * 6364136223846793005L + 1442695040888963407L
        return ((noiseState ushr 40).toInt() % 2048) / 1024f - 1f
    }

    private fun square(cycles: Float): Float = if ((cycles % 1f) < 0.5f) 1f else -1f

    private fun saw(cycles: Float): Float = 2f * (cycles % 1f) - 1f

    private fun slide(from: Float, to: Float, k: Float): Float =
        from + (to - from) * k.coerceIn(0f, 1f)

    private fun env(t: Float, dur: Float, attack: Float, decayPow: Float): Float {
        val a = if (t < attack) t / attack else 1f
        val rel = (1f - t / dur).coerceIn(0f, 1f)
        return a * rel.pow(decayPow)
    }

    private fun render(dur: Float, gen: (t: Float, dur: Float) -> Float): ShortArray {
        val n = (SR * dur).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val v = gen(i / SR, dur).coerceIn(-1f, 1f)
            out[i] = (v * 32767f).toInt().toShort()
        }
        return out
    }

    private fun mix(vararg clips: ShortArray): ShortArray {
        val n = clips.maxOf { it.size }
        val out = ShortArray(n)
        for (i in 0 until n) {
            var v = 0
            for (c in clips) if (i < c.size) v += c[i].toInt()
            out[i] = v.coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun notes(freqs: FloatArray, noteDur: Float, vol: Float): ShortArray {
        val total = noteDur * freqs.size + 0.15f
        return render(total) { t, _ ->
            var v = 0f
            for ((i, f) in freqs.withIndex()) {
                val local = t - i * noteDur
                if (local >= 0f) {
                    val nd = noteDur + 0.15f
                    if (local < nd) {
                        v += (sin(tau * local * f) + 0.35f * sin(tau * local * f * 2f)) *
                            env(local, nd, 0.006f, 1.8f) * vol
                    }
                }
            }
            v
        }
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val dataLen = pcm.size * 2
        val bytes = ByteArray(44 + dataLen)
        fun putStr(off: Int, s: String) {
            for (i in s.indices) bytes[off + i] = s[i].code.toByte()
        }
        fun putInt(off: Int, v: Int) {
            bytes[off] = (v and 0xFF).toByte()
            bytes[off + 1] = ((v shr 8) and 0xFF).toByte()
            bytes[off + 2] = ((v shr 16) and 0xFF).toByte()
            bytes[off + 3] = ((v shr 24) and 0xFF).toByte()
        }
        fun putShort(off: Int, v: Int) {
            bytes[off] = (v and 0xFF).toByte()
            bytes[off + 1] = ((v shr 8) and 0xFF).toByte()
        }
        putStr(0, "RIFF"); putInt(4, 36 + dataLen); putStr(8, "WAVE")
        putStr(12, "fmt "); putInt(16, 16); putShort(20, 1); putShort(22, 1)
        putInt(24, SR.toInt()); putInt(28, SR.toInt() * 2)
        putShort(32, 2); putShort(34, 16)
        putStr(36, "data"); putInt(40, dataLen)
        for (i in pcm.indices) {
            bytes[44 + i * 2] = (pcm[i].toInt() and 0xFF).toByte()
            bytes[45 + i * 2] = ((pcm[i].toInt() shr 8) and 0xFF).toByte()
        }
        file.writeBytes(bytes)
    }

    private companion object {
        const val SR = 22050f
    }
}
