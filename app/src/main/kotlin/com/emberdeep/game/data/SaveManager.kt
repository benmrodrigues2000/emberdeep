package com.emberdeep.game.data

import android.content.Context
import com.emberdeep.game.model.ClassType
import com.emberdeep.game.model.GameState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
            p.treasury = o.optInt("treasury", 0)
            p.bestFloor = o.optInt("bestFloor", 0)
            p.victories = o.optInt("victories", 0)
            p.runs = o.optInt("runs", 0)
            p.totalKills = o.optInt("totalKills", 0)
            val arr = o.optJSONArray("unlocked")
            if (arr != null) {
                for (i in 0 until arr.length()) p.unlocked.add(arr.optString(i))
            }
            return p
        }
    }
}

/**
 * Error-safe persistence. All writes are atomic (temp file + rename) and all
 * reads fall back to sane defaults when files are missing or corrupt.
 */
class SaveManager(context: Context) {

    private val dir: File = context.filesDir
    private val profileFile get() = File(dir, "profile.json")
    private val runFile get() = File(dir, "run.json")

    fun loadProfile(): Profile = try {
        if (profileFile.exists()) {
            Profile.fromJson(JSONObject(profileFile.readText()))
        } else Profile()
    } catch (e: Exception) {
        Profile()
    }

    fun saveProfile(p: Profile) {
        atomicWrite(profileFile, p.toJson().toString())
    }

    fun hasRun(): Boolean = runFile.exists()

    fun loadRun(): GameState? = try {
        if (!runFile.exists()) null
        else GameState.fromJson(JSONObject(runFile.readText()))
    } catch (e: Exception) {
        // Corrupt save: discard it rather than crash-looping.
        deleteRun()
        null
    }

    fun saveRun(state: GameState) {
        try {
            atomicWrite(runFile, state.toJson().toString())
        } catch (e: Exception) {
            // Never let a failed save crash gameplay.
        }
    }

    fun deleteRun() {
        try {
            runFile.delete()
        } catch (e: Exception) {
            // Ignore.
        }
    }

    fun resetAll() {
        deleteRun()
        try {
            profileFile.delete()
        } catch (e: Exception) {
            // Ignore.
        }
    }

    private fun atomicWrite(target: File, content: String) {
        try {
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(target)) {
                target.writeText(content)
                tmp.delete()
            }
        } catch (e: Exception) {
            // Storage full or inaccessible — fail quietly.
        }
    }
}
