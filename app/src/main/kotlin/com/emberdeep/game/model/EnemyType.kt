package com.emberdeep.game.model

enum class EnemySpecial { NONE, REGEN, PHASE, BURNER, DRAIN, BOSS }

/** Monster bestiary. Stats are base values, scaled up with dungeon depth. */
enum class EnemyType(
    val display: String,
    val hp: Int,
    val atk: Int,
    val ac: Int,
    val dmgN: Int,
    val dmgS: Int,
    val dmgB: Int,
    val xp: Int,
    val ranged: Boolean = false,
    val attackRange: Int = 1,
    val special: EnemySpecial = EnemySpecial.NONE,
    val minFloor: Int,
    val maxFloor: Int,
    val weight: Int,          // spawn weight within its floor band
    val tint: Int
) {
    RAT("Giant Rat", 6, 2, 10, 1, 3, 0, 6,
        minFloor = 1, maxFloor = 3, weight = 30, tint = 0xFF8D8378.toInt()),
    GOBLIN("Goblin", 10, 3, 12, 1, 4, 1, 10,
        minFloor = 1, maxFloor = 5, weight = 30, tint = 0xFF7BA05B.toInt()),
    SKELETON("Skeleton", 14, 4, 12, 1, 6, 1, 14,
        minFloor = 2, maxFloor = 7, weight = 25, tint = 0xFFE8E0D0.toInt()),
    SKELETON_ARCHER("Skeleton Archer", 11, 4, 11, 1, 6, 0, 16,
        ranged = true, attackRange = 5,
        minFloor = 2, maxFloor = 8, weight = 15, tint = 0xFFD8CBB0.toInt()),
    ORC("Orc Brute", 24, 5, 13, 1, 8, 2, 24,
        minFloor = 3, maxFloor = 9, weight = 22, tint = 0xFF5B8A4A.toInt()),
    CULTIST("Ember Cultist", 16, 5, 12, 1, 6, 1, 26,
        ranged = true, attackRange = 4, special = EnemySpecial.BURNER,
        minFloor = 4, maxFloor = 10, weight = 15, tint = 0xFFC04A3C.toInt()),
    WRAITH("Deep Wraith", 20, 6, 14, 1, 6, 2, 34,
        special = EnemySpecial.DRAIN,
        minFloor = 5, maxFloor = 10, weight = 12, tint = 0xFF9FB8D8.toInt()),
    TROLL("Cave Troll", 40, 6, 13, 2, 6, 2, 48,
        special = EnemySpecial.REGEN,
        minFloor = 6, maxFloor = 10, weight = 10, tint = 0xFF6E7D5A.toInt()),
    EMBER_DRAGON("The Ember Dragon", 150, 8, 16, 2, 8, 3, 500,
        special = EnemySpecial.BOSS,
        minFloor = 99, maxFloor = 99, weight = 0, tint = 0xFFE0502B.toInt());

    companion object {
        fun byName(n: String?): EnemyType = entries.firstOrNull { it.name == n } ?: RAT

        /** Pool of regular monsters allowed on [floor]. */
        fun poolFor(floor: Int): List<EnemyType> =
            entries.filter { it.weight > 0 && floor >= it.minFloor && floor <= it.maxFloor }
    }
}
