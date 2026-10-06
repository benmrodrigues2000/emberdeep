package com.emberdeep.game.systems

import com.emberdeep.game.core.Rng
import com.emberdeep.game.model.Enemy
import com.emberdeep.game.model.Player

class AttackResult(val hit: Boolean, val crit: Boolean, val dmg: Int)

/** Classic d20 combat resolution: d20 + attack bonus vs armor class. */
object Combat {

    fun playerAttack(
        rng: Rng, player: Player, enemy: Enemy,
        autoCrit: Boolean = false, bonusDmg: Int = 0
    ): AttackResult {
        val roll = rng.dice(1, 20)
        val crit = autoCrit || roll == 20
        val miss = !crit && (roll == 1 || roll + player.atkBonus < enemy.ac)
        if (miss) return AttackResult(false, false, 0)
        var dmg = rng.dice(player.dmgN, player.dmgS, player.dmgB) + bonusDmg
        if (crit) dmg += rng.dice(player.dmgN, player.dmgS)
        dmg = dmg.coerceAtLeast(1)
        enemy.hp -= dmg
        return AttackResult(true, crit, dmg)
    }

    fun enemyAttack(rng: Rng, enemy: Enemy, player: Player): AttackResult {
        val roll = rng.dice(1, 20)
        val crit = roll == 20
        val miss = !crit && (roll == 1 || roll + enemy.atk < player.acTotal)
        if (miss) return AttackResult(false, false, 0)
        var dmg = rng.dice(enemy.type.dmgN, enemy.type.dmgS, enemy.dmgB)
        if (crit) dmg += rng.dice(enemy.type.dmgN, enemy.type.dmgS)
        dmg = dmg.coerceAtLeast(1)
        player.hp -= dmg
        return AttackResult(true, crit, dmg)
    }
}
