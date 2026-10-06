package com.emberdeep.game.model

enum class ItemKind { WEAPON, ARMOR, POTION, SCROLL, GOLD }

/**
 * Every concrete item in the game. Weapons replace the class damage dice;
 * armor adds AC. Consumables carry their strength in [power].
 */
enum class ItemType(
    val kind: ItemKind,
    val display: String,
    val desc: String,
    val dmgN: Int = 0,
    val dmgS: Int = 0,
    val dmgB: Int = 0,
    val acBonus: Int = 0,
    val power: Int = 0,
    val tier: Int = 0,
    val minFloor: Int = 1,
    val color: Int = 0xFFCCCCCC.toInt()
) {
    // Weapons ---------------------------------------------------------------
    RUSTY_DAGGER(ItemKind.WEAPON, "Rusty Dagger", "A sad little blade. 1d4+1 damage.",
        1, 4, 1, tier = 1, minFloor = 1, color = 0xFF9AA0A6.toInt()),
    SHORT_SWORD(ItemKind.WEAPON, "Short Sword", "Reliable steel. 1d6+1 damage.",
        1, 6, 1, tier = 2, minFloor = 1, color = 0xFFC3CCD6.toInt()),
    WAR_AXE(ItemKind.WEAPON, "War Axe", "Heavy and mean. 1d8+2 damage.",
        1, 8, 2, tier = 3, minFloor = 3, color = 0xFFD7B98A.toInt()),
    FLAME_BRAND(ItemKind.WEAPON, "Flame Brand", "A blade wreathed in fire. 1d10+3 damage.",
        1, 10, 3, tier = 4, minFloor = 5, color = 0xFFFF8A4D.toInt()),
    EMBERFANG(ItemKind.WEAPON, "Emberfang", "Forged in the deep. 2d6+4 damage.",
        2, 6, 4, tier = 5, minFloor = 7, color = 0xFFFF5A2B.toInt()),

    // Armor -----------------------------------------------------------------
    LEATHER_ARMOR(ItemKind.ARMOR, "Leather Armor", "Boiled hide. +1 AC.",
        acBonus = 1, tier = 1, minFloor = 1, color = 0xFFA0794F.toInt()),
    CHAIN_MAIL(ItemKind.ARMOR, "Chain Mail", "Linked rings of steel. +2 AC.",
        acBonus = 2, tier = 2, minFloor = 3, color = 0xFFB7C3D0.toInt()),
    PLATE_ARMOR(ItemKind.ARMOR, "Plate Armor", "A fortress you can wear. +3 AC.",
        acBonus = 3, tier = 3, minFloor = 5, color = 0xFFDDE5EC.toInt()),
    EMBERPLATE(ItemKind.ARMOR, "Emberplate", "Dragon-forged plate. +4 AC.",
        acBonus = 4, tier = 4, minFloor = 7, color = 0xFFFF9A5D.toInt()),

    // Potions ---------------------------------------------------------------
    POTION_HEAL(ItemKind.POTION, "Healing Potion", "Restores 12 HP.",
        power = 12, color = 0xFFE0443C.toInt()),
    POTION_GREATER_HEAL(ItemKind.POTION, "Greater Healing", "Restores 30 HP.",
        power = 30, minFloor = 4, color = 0xFFFF7A6B.toInt()),
    POTION_STRENGTH(ItemKind.POTION, "Potion of Might", "+3 attack for 25 turns.",
        power = 25, color = 0xFFFFA14D.toInt()),
    POTION_SHIELD(ItemKind.POTION, "Stoneskin Potion", "+4 AC for 25 turns.",
        power = 25, color = 0xFF5A9BD4.toInt()),

    // Scrolls ---------------------------------------------------------------
    SCROLL_FIREBALL(ItemKind.SCROLL, "Scroll of Fireball", "4d6 fire damage to every visible enemy.",
        power = 0, color = 0xFFFFC94D.toInt()),
    SCROLL_TELEPORT(ItemKind.SCROLL, "Scroll of Blinking", "Teleport to a random spot on this floor.",
        power = 0, color = 0xFFB9A8E8.toInt()),

    // Gold ------------------------------------------------------------------
    GOLD_PILE(ItemKind.GOLD, "Gold", "Shiny. Spend it between runs.",
        color = 0xFFFFC94D.toInt());

    val stackable: Boolean get() = kind == ItemKind.POTION || kind == ItemKind.SCROLL

    companion object {
        fun byName(n: String?): ItemType? = entries.firstOrNull { it.name == n }
    }
}

/** An item stack in the inventory or on the ground. */
class Item(val type: ItemType, var count: Int = 1)

/** An item lying on the dungeon floor. */
class GroundItem(var x: Int, var y: Int, val item: Item, var gold: Int = 0)
