package com.emberdeep.game.model

import com.emberdeep.game.testutil.A
import org.junit.Test

class PlayerTest {

    @Test
    fun `levelling up costs more each level and fully heals`() {
        val hero = Player(ClassType.FIGHTER)
        hero.hp = 5
        val messages = hero.gainXp(20)
        A.eq(2, hero.level, "level after 20 xp")
        A.eq(1, messages.size, "level up message count")
        A.eq(hero.maxHp, hero.hp, "levelling up must restore health")
        A.isTrue(hero.maxHp > ClassType.FIGHTER.baseHp, "max hp must grow")
        A.isTrue(hero.xpToNext > 20, "the next level must cost more")
    }

    @Test
    fun `a huge xp payout levels several times without losing the remainder`() {
        val hero = Player(ClassType.MAGE)
        val messages = hero.gainXp(300)
        A.isTrue(messages.size >= 3, "300 xp should be several levels (was ${messages.size})")
        A.eq(messages.size, hero.level - 1, "one level up per message")
        A.isTrue(hero.xp in 0 until hero.xpToNext, "leftover xp stays inside the current level")
        A.eq(hero.maxHp, hero.hp, "health fully restored")
    }

    @Test
    fun `the inventory holds ten slots and stacks consumables`() {
        val hero = Player(ClassType.FIGHTER)
        hero.addItem(ItemType.POTION_HEAL)
        hero.addItem(ItemType.POTION_HEAL)
        A.eq(1, hero.inventory.size, "potions stack into one slot")
        A.eq(2, hero.inventory[0].count, "stack size")

        // One slot is already taken by the potion stack.
        repeat(Player.MAX_SLOTS - 1) { A.isTrue(hero.addItem(ItemType.RUSTY_DAGGER), "slot $it") }
        A.eq(Player.MAX_SLOTS, hero.inventory.size, "inventory is capped")
        A.isFalse(hero.addItem(ItemType.SHORT_SWORD), "an eleventh slot must be refused")

        hero.removeOne(hero.inventory.first { it.type == ItemType.POTION_HEAL })
        A.eq(1, hero.inventory.first { it.type == ItemType.POTION_HEAL }.count, "stack shrinks")
    }

    @Test
    fun `gear and buffs feed the derived stats`() {
        val hero = Player(ClassType.FIGHTER)
        val baseAc = hero.acTotal
        val baseAtk = hero.atkBonus
        hero.armor = ItemType.PLATE_ARMOR
        A.eq(baseAc + 3, hero.acTotal, "plate armour adds 3 AC")
        hero.shieldTurns = 5
        A.eq(baseAc + 3 + 4, hero.acTotal, "stoneskin adds 4 AC")
        hero.strengthTurns = 5
        A.eq(baseAtk + 3, hero.atkBonus, "might adds 3 to hit")
        hero.weapon = ItemType.WAR_AXE
        A.eq("1d8+2", hero.damageLabel, "war axe damage line")
        hero.level = 3
        A.eq("1d8+3", hero.damageLabel, "levels add a flat damage bonus")
    }

    @Test
    fun `unlocks follow the treasury cost`() {
        val profile = com.emberdeep.game.data.Profile()
        A.isTrue(profile.isUnlocked(ClassType.FIGHTER), "the fighter is free")
        A.isFalse(profile.isUnlocked(ClassType.ROGUE), "the rogue must be earned")
        profile.treasury = 150
        profile.unlock(ClassType.ROGUE)
        A.isTrue(profile.isUnlocked(ClassType.ROGUE), "unlocking grants access")
        A.isFalse(profile.isUnlocked(ClassType.MAGE), "the mage is still locked")
    }
}
