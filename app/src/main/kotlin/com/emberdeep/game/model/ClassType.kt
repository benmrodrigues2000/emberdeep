package com.emberdeep.game.model

/** The three playable adventurer classes. */
enum class ClassType(
    val display: String,
    val blurb: String,
    val baseHp: Int,
    val hitDie: Int,          // extra max HP per level
    val baseAtk: Int,         // to-hit bonus
    val baseAc: Int,
    val dmgN: Int,            // unarmed/class weapon dice: N d S + B
    val dmgS: Int,
    val dmgB: Int,
    val abilityName: String,
    val abilityDesc: String,
    val abilityCooldown: Int, // in turns
    val unlockCost: Int,      // treasury gold; 0 = unlocked from the start
    val tint: Int
) {
    FIGHTER(
        "Fighter",
        "A wall of steel. High health and heavy armor.",
        30, 6, 3, 14, 1, 8, 1,
        "Whirlwind",
        "Strike every adjacent enemy with a bonus +4 damage.",
        6, 0, 0xFFB7C3D0.toInt()
    ),
    ROGUE(
        "Rogue",
        "A shadow with daggers. Deadly, but fragile.",
        22, 4, 5, 13, 2, 4, 1,
        "Shadowstep",
        "Teleport to any visible tile. Your next attack is a guaranteed critical.",
        7, 150, 0xFF8F7BD4.toInt()
    ),
    MAGE(
        "Mage",
        "Master of embers. Burns rooms from a distance.",
        18, 4, 4, 11, 1, 6, 0,
        "Firebolt",
        "Blast a visible enemy for 3d6 fire damage and set it burning.",
        5, 400, 0xFFE06A3C.toInt()
    );

    companion object {
        fun byName(n: String?): ClassType =
            entries.firstOrNull { it.name == n } ?: FIGHTER
    }
}
