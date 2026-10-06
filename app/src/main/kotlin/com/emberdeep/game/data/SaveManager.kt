package com.emberdeep.game.data

import com.emberdeep.game.model.ClassType
import com.emberdeep.game.model.GameState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Persistent cross-run data: settings, unlocks and lifetime statistics. */
class Profile {
    var sound = true
    var music = true
    var haptics = true
    var minimap = true

    val unlocked = HashSet<String>().apply { add(ClassType.FIGHTER.name) }
    var treasury = 0
    var bestFloor = 0
    var victories = 0
    var runs = 0
    var totalKills = 0

    fun isUnlocked(c: ClassType): Boolean = c.unlockCost == 0 || unlocked.contains(c.name)

    fun unlock(c: ClassType) { unlocked.add(c.name) }

    fun toJson(): JSONObject = JSONObject().apply {
        put("sound", sound); put("music", music)
        put("haptics", haptics); put("minimap", minimap)
        put("treasury", treasury); put("bestFloor", bestFloor)
        put("victories", victories); put("runs", runs)
        put("totalKills", totalKills)
        put("unlocked", JSONArray(unlocked.toList()))
    }

    companion object {
        fun fromJson(o: JSONObject): Profile {
            val p = Profile()
            p.sound = o.optBoolean("sound", true)
            p.music = o.optBoolean("music", true)
            p.haptics = o.optBoolean("haptics", true)
            p.minimap = o.optBoolean("minimap", true)
            p.treasury = o.optInt("treasury", 0).coerceAtLeast(0)
            p.bestFloor = o.optInt("bestFloor", 0).coerceAtLeast(0)
            p.victories = o.optInt("victories", 0).coerceAtLeast(0)
            p.runs = o.optInt("runs", 0).coerceAtLeast(0)
            p.totalKills = o.optInt("totalKills", 0).coerceAtLeast(0)
            val arr = o.optJSONArray("unlocked")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val name = arr.optString(i)
                    // Only trust names that still exist in the current build.
                    if (ClassType.entries.any { it.name == name }) p.unlocked.add(name)
                }
            }
            return p
        }
    }
}

/**
 * Error-safe persistence.
 *
 *  * Every write goes to a temp file that is then renamed, so a process kill
 *    mid-save can never leave a half-written file in place.
 *  * The previous good version of each file is kept as `<name>.bak` and used
 *    as a fallback when the primary file turns out to be corrupt.
 *  * Autosaves are serialized on the game thread (a consistent snapshot) and
 *    written by a background worker, so saving never stalls a frame. Call
 *    [flush] on lifecycle boundaries to make pending writes durable.
 *
 * Constructed with a plain [File] directory — no Android types — so the whole
 * save pipeline is unit-testable.
 */
class SaveManager(private val dir: File) {

    private val profileFile = File(dir, PROFILE_FILE)
    private val runFile = File(dir, RUN_FILE)

    private val queue = ArrayBlockingQueue<String>(1)
    /** Writes queued but not yet on disk; [flush] waits for this to reach zero. */
    private val pending = AtomicInteger(0)
    private var worker: Thread? = null

    @Volatile private var writing = false
    @Volatile private var closed = false

    // ------------------------------------------------------------------ //
    // Profile
    // ------------------------------------------------------------------ //

    fun loadProfile(): Profile {
        readJson(profileFile)?.let { return Profile.fromJson(it) }
        readJson(backupOf(profileFile))?.let { return Profile.fromJson(it) }
        return Profile()
    }

    fun saveProfile(profile: Profile) {
        val json = try {
            profile.toJson().toString()
        } catch (e: Exception) {
            return
        }
        atomicWrite(profileFile, json)
    }

    // ------------------------------------------------------------------ //
    // Run
    // ------------------------------------------------------------------ //

    fun hasRun(): Boolean = runFile.exists() || backupOf(runFile).exists()

    /** @return the stored run, or null when there is none / it is unreadable. */
    fun loadRun(): GameState? {
        readJson(runFile)?.let { json ->
            try {
                return GameState.fromJson(json)
            } catch (e: Exception) {
                // Fall through to the backup.
            }
        }
        if (!runFile.exists() && !backupOf(runFile).exists()) return null
        // Corrupt primary: drop it and recover the last good write instead of
        // crash-looping on every launch.
        deleteQuietly(runFile)
        val backupFile = backupOf(runFile)
        val backup = readJson(backupFile)
        if (backup == null) {
            deleteQuietly(backupFile)
            return null
        }
        return try {
            GameState.fromJson(backup)
        } catch (e: Exception) {
            deleteQuietly(backupFile)
            null
        }
    }

    /** Blocking save: used where a frame hitch is invisible (pause, floor change). */
    fun saveRun(state: GameState) {
        val json = try {
            state.toJson().toString()
        } catch (e: Exception) {
            return
        }
        writeRun(json)
    }

    /**
     * Autosave: takes a snapshot immediately on the calling (game) thread so
     * the write cannot race with gameplay, then writes it off the game thread.
     */
    fun saveRunAsync(state: GameState) {
        val json = try {
            state.toJson().toString()
        } catch (e: Exception) {
            return
        }
        if (closed) {
            writeRun(json)
            return
        }
        ensureWorker()
        if (queue.offer(json)) {
            pending.incrementAndGet()
        } else {
            // The single slot is busy: replace the queued snapshot (only the
            // newest state matters) and keep the pending count unchanged.
            queue.poll()
            queue.offer(json)
        }
    }

    fun deleteRun() {
        deleteQuietly(runFile)
        deleteQuietly(backupOf(runFile))
    }

    fun resetAll() {
        deleteRun()
        deleteQuietly(profileFile)
        deleteQuietly(backupOf(profileFile))
    }

    // ------------------------------------------------------------------ //
    // Lifecycle
    // ------------------------------------------------------------------ //

    /** Waits (briefly) for any queued write to reach disk. */
    fun flush(timeoutMs: Long = FLUSH_TIMEOUT_MS) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while ((pending.get() > 0 || writing || queue.isNotEmpty()) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(4L)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    /** Flushes and stops the writer thread. Safe to call more than once. */
    fun close() {
        flush()
        closed = true
        worker?.interrupt()
        worker = null
    }

    // ------------------------------------------------------------------ //
    // Internals
    // ------------------------------------------------------------------ //

    private fun ensureWorker() {
        if (worker != null || closed) return
        worker = Thread({
            while (!closed) {
                val json = try {
                    queue.poll(1L, TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    null
                }
                if (json != null) {
                    writeRun(json)
                    pending.decrementAndGet()
                }
            }
        }, "emberdeep-save").apply {
            isDaemon = true
            start()
        }
    }

    private fun writeRun(json: String) {
        writing = true
        try {
            atomicWrite(runFile, json)
        } finally {
            writing = false
        }
    }

    private fun readJson(file: File): JSONObject? = try {
        if (!file.exists() || file.length() == 0L) null else JSONObject(file.readText())
    } catch (e: Exception) {
        null
    }

    private fun backupOf(file: File): File = File(dir, file.name + BACKUP_SUFFIX)

    private fun deleteQuietly(file: File) {
        try {
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            // Nothing useful to do: the next save overwrites it anyway.
        }
    }

    private fun atomicWrite(target: File, content: String) {
        try {
            if (!dir.exists()) dir.mkdirs()
            val tmp = File(dir, target.name + TMP_SUFFIX)
            tmp.writeText(content)
            if (target.exists()) {
                val backup = backupOf(target)
                deleteQuietly(backup)
                if (!target.renameTo(backup)) target.copyTo(backup, overwrite = true)
            }
            if (!tmp.renameTo(target)) {
                // Some filesystems refuse renames across handles; fall back.
                target.writeText(content)
                deleteQuietly(tmp)
            }
        } catch (e: Exception) {
            // Storage full or inaccessible — never let saving break gameplay.
        }
    }

    companion object {
        const val RUN_FILE = "run.json"
        const val PROFILE_FILE = "profile.json"
        const val BACKUP_SUFFIX = ".bak"
        const val TMP_SUFFIX = ".tmp"
        const val FLUSH_TIMEOUT_MS = 500L
    }
}
