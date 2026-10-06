package com.emberdeep.game.data

import com.emberdeep.game.model.ClassType
import com.emberdeep.game.model.ItemType
import com.emberdeep.game.systems.RunSetup
import com.emberdeep.game.testutil.A
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun manager() = SaveManager(tmp.root)

    private fun run(seed: Long = 4242L) = RunSetup.newRun(ClassType.FIGHTER, seed)

    private fun file(name: String) = File(tmp.root, name)

    @Test
    fun `a run survives a save and load cycle intact`() {
        val manager = manager()
        val state = run()
        state.turn = 42
        state.player.gold = 123
        state.player.level = 4
        state.player.xp = 7
        state.player.hp = state.player.maxHp - 9
        state.player.addItem(ItemType.POTION_GREATER_HEAL)
        state.player.weapon = ItemType.WAR_AXE
        state.player.armor = ItemType.CHAIN_MAIL
        state.addLog("hello emberdeep", 0xFFAA33)
        state.enemies[0].hp -= 3
        manager.saveRun(state)

        val loaded = manager.loadRun()
        A.notNull(loaded, "loaded run")
        val l = loaded!!
        A.eq(state.floor, l.floor, "floor")
        A.eq(state.turn, l.turn, "turn")
        A.eqAny(state.player.classType, l.player.classType, "class")
        A.eq(state.player.gold, l.player.gold, "gold")
        A.eq(state.player.level, l.player.level, "level")
        A.eq(state.player.xp, l.player.xp, "xp")
        A.eq(state.player.hp, l.player.hp, "hp")
        A.eq(state.player.maxHp, l.player.maxHp, "max hp")
        A.eq(state.player.x, l.player.x, "hero x")
        A.eq(state.player.y, l.player.y, "hero y")
        A.eqAny(state.player.weapon, l.player.weapon, "weapon")
        A.eqAny(state.player.armor, l.player.armor, "armour")
        A.isTrue(l.player.inventory.any { it.type == ItemType.POTION_GREATER_HEAL }, "inventory")
        A.eq(state.map.width, l.map.width, "map width")
        A.eq(state.map.height, l.map.height, "map height")
        A.eq(state.map.stairsX, l.map.stairsX, "stairs x")
        A.eq(state.map.stairsY, l.map.stairsY, "stairs y")
        A.eq(state.enemies.size, l.enemies.size, "monster count")
        A.eq(state.enemies[0].hp, l.enemies[0].hp, "monster hp")
        A.eq(state.map.groundItems.size, l.map.groundItems.size, "ground items")
        A.isTrue(state.map.tiles.contentEquals(l.map.tiles), "tiles round trip")
        A.isTrue(state.map.variant.contentEquals(l.map.variant), "variants round trip")
        A.isTrue(state.map.explored.contentEquals(l.map.explored), "explored round trip")
        A.isTrue(l.log.any { it.text == "hello emberdeep" }, "log line")
        A.isTrue(l.map.isWalkable(l.player.x, l.player.y), "the hero stands on walkable ground")
        A.isTrue(l.player.hp > 0, "a loaded hero is alive")
    }

    @Test
    fun `saving leaves no temporary file behind`() {
        val manager = manager()
        manager.saveRun(run())
        A.isFalse(
            file(SaveManager.RUN_FILE + SaveManager.TMP_SUFFIX).exists(),
            "temp files must be renamed away"
        )
        manager.saveProfile(Profile())
        A.isFalse(
            file(SaveManager.PROFILE_FILE + SaveManager.TMP_SUFFIX).exists(),
            "profile temp file cleaned up"
        )
    }

    @Test
    fun `a corrupt save falls back to the previous good one`() {
        val manager = manager()
        val first = run(1L).also { it.turn = 11 }
        manager.saveRun(first)
        val second = run(2L).also { it.turn = 22 }
        manager.saveRun(second)
        file(SaveManager.RUN_FILE).writeText("{ this is not json")

        val recovered = manager.loadRun()
        A.notNull(recovered, "recovered run")
        A.eq(11, recovered!!.turn, "the previous good write must be recovered")
        A.isTrue(
            recovered.map.isWalkable(recovered.player.x, recovered.player.y),
            "the recovered run is playable"
        )
    }

    @Test
    fun `unreadable saves are discarded instead of crashing`() {
        val manager = manager()
        file(SaveManager.RUN_FILE).writeText("total garbage")
        file(SaveManager.RUN_FILE + SaveManager.BACKUP_SUFFIX).writeText("also garbage")
        A.isTrue(manager.loadRun() == null, "an unreadable run must report as absent")
        A.isFalse(file(SaveManager.RUN_FILE).exists(), "the corrupt primary is deleted")
        A.isFalse(
            file(SaveManager.RUN_FILE + SaveManager.BACKUP_SUFFIX).exists(),
            "the corrupt backup is deleted"
        )
        A.isFalse(manager.hasRun(), "no run is offered once both copies are gone")
    }

    @Test
    fun `missing files are simply empty`() {
        val manager = manager()
        A.isTrue(manager.loadRun() == null, "no run yet")
        A.isFalse(manager.hasRun(), "nothing to continue")
        val profile = manager.loadProfile()
        A.isTrue(profile.sound, "default settings")
        A.eq(0, profile.treasury, "empty treasury")
        A.isTrue(profile.isUnlocked(ClassType.FIGHTER), "the fighter is available from the start")
    }

    @Test
    fun `the profile round trips and drops unknown unlocks`() {
        val manager = manager()
        val profile = Profile()
        profile.sound = false
        profile.music = false
        profile.haptics = false
        profile.minimap = false
        profile.treasury = 777
        profile.bestFloor = 9
        profile.victories = 2
        profile.runs = 5
        profile.totalKills = 88
        profile.unlock(ClassType.MAGE)
        manager.saveProfile(profile)

        val loaded = manager.loadProfile()
        A.isFalse(loaded.sound, "sound setting")
        A.isFalse(loaded.minimap, "minimap setting")
        A.eq(777, loaded.treasury, "treasury")
        A.eq(9, loaded.bestFloor, "best floor")
        A.eq(88, loaded.totalKills, "kills")
        A.isTrue(loaded.isUnlocked(ClassType.MAGE), "the mage stays unlocked")

        // A profile written by a newer build must not leak unknown classes in.
        file(SaveManager.PROFILE_FILE).writeText("""{"unlocked":["FIGHTER","DRAGONLORD"]}""")
        val sanitised = manager.loadProfile()
        A.eq(1, sanitised.unlocked.size, "unknown unlocks are dropped")
        A.isFalse(sanitised.isUnlocked(ClassType.ROGUE), "the rogue is still locked")
    }

    @Test
    fun `a corrupt profile falls back to defaults`() {
        val manager = manager()
        file(SaveManager.PROFILE_FILE).writeText("{ broken")
        val profile = manager.loadProfile()
        A.isTrue(profile.sound, "sound defaults on")
        A.eq(0, profile.treasury, "treasury defaults to zero")
    }

    @Test
    fun `resetAll erases progression but keeps a working manager`() {
        val manager = manager()
        val profile = Profile().also { it.treasury = 500; it.unlock(ClassType.ROGUE) }
        manager.saveProfile(profile)
        manager.saveRun(run())

        manager.resetAll()
        A.isFalse(manager.hasRun(), "runs are gone")
        val fresh = manager.loadProfile()
        A.eq(0, fresh.treasury, "treasury reset")
        A.isFalse(fresh.isUnlocked(ClassType.ROGUE), "unlocks reset")
        A.isTrue(fresh.isUnlocked(ClassType.FIGHTER), "the starter class remains")
    }

    @Test
    fun `asynchronous autosaves reach the disk once flushed`() {
        val manager = manager()
        val state = run(9L).also { it.turn = 5 }
        manager.saveRunAsync(state)
        manager.flush()
        A.isTrue(file(SaveManager.RUN_FILE).exists(), "the autosave reached the disk")
        val loaded = manager.loadRun()
        A.notNull(loaded, "the autosave is readable")
        A.eq(5, loaded!!.turn, "autosave contents")

        // After closing, writes must still land (pause/destroy ordering).
        manager.close()
        val later = run(10L).also { it.turn = 6 }
        manager.saveRunAsync(later)
        A.eq(6, manager.loadRun()!!.turn, "saving after close still works")
    }
}
