package com.emberdeep.game.systems

import com.emberdeep.game.core.Rng
import com.emberdeep.game.model.ClassType
import com.emberdeep.game.model.Enemy
import com.emberdeep.game.model.EnemyType
import com.emberdeep.game.model.ItemType
import com.emberdeep.game.model.Player
import com.emberdeep.game.testutil.A
import org.junit.Test

class CombatTest {

    private fun hero(): Player = Player(ClassType.FIGHTER)

    private fun monster(ac: Int = 10, hp: Int = 10_000): Enemy {
        val e = Enemy(EnemyType.RAT, 2, 2)
        e.ac = ac
        e.maxHp = hp
        e.hp = hp
        return e
    }

    @Test
    fun `player attacks always deal at least one damage when they land`() {
        val rng = Rng(5150L)
        var hits = 0
        var misses = 0
        var crits = 0
        repeat(4000) {
            val target = monster()
            val result = Combat.playerAttack(rng, hero(), target)
            if (result.hit) {
                hits++
                A.within(result.dmg, 1, 14, "damage roll")
                A.eq(target.hp, 10_000 - result.dmg, "damage must be applied to the target")
                if (result.crit) crits++
            } else {
                misses++
                A.eq(0, result.dmg, "misses deal no damage")
            }
        }
        A.isTrue(hits > 0, "the fighter never hit at all")
        A.isTrue(misses > 0, "the fighter never missed: d20 resolution is broken")
        A.isTrue(crits > 0, "natural 20s never happened")
        A.near(0.05, crits.toDouble() / hits.coerceAtLeast(1), 0.03, "crit frequency")
    }

    @Test
    fun `natural 20 hits even against an impossible armour class`() {
        val rng = Rng(77L)
        var hits = 0
        var crits = 0
        repeat(2000) {
            val target = monster(ac = 99)
            val result = Combat.playerAttack(rng, hero(), target)
            if (result.hit) {
                hits++
                if (result.crit) crits++
            }
        }
        A.isTrue(hits > 0, "even a natural 20 failed to hit")
        A.eq(hits, crits, "against AC 99 only natural 20s may land")
    }

    @Test
    fun `armour class reduces the hit rate`() {
        val easyHits = hitRate(ac = 10, seed = 1L, draws = 3000)
        val hardHits = hitRate(ac = 19, seed = 1L, draws = 3000)
        A.isTrue(easyHits > hardHits + 0.15, "AC 19 ($hardHits) should be much harder than AC 10 ($easyHits)")
    }

    private fun hitRate(ac: Int, seed: Long, draws: Int): Double {
        val rng = Rng(seed)
        val hero = hero()
        var hits = 0
        repeat(draws) { if (Combat.playerAttack(rng, hero, monster(ac = ac)).hit) hits++ }
        return hits.toDouble() / draws
    }

    @Test
    fun `a better weapon and higher level deal more damage`() {
        val baseline = averageDamage(level = 1, weapon = null)
        val armed = averageDamage(level = 1, weapon = ItemType.EMBERFANG)
        val veteran = averageDamage(level = 9, weapon = ItemType.EMBERFANG)
        A.isTrue(armed > baseline, "Emberfang ($armed) should outdamage bare hands ($baseline)")
        A.isTrue(veteran > armed, "levels ($veteran) should add damage over gear alone ($armed)")
    }

    private fun averageDamage(level: Int, weapon: ItemType?): Double {
        val rng = Rng(4242L)
        val hero = hero()
        hero.level = level
        hero.weapon = weapon
        var total = 0
        var hits = 0
        repeat(3000) {
            val target = monster(ac = 12)
            val r = Combat.playerAttack(rng, hero, target)
            if (r.hit) { total += r.dmg; hits++ }
        }
        return total.toDouble() / hits.coerceAtLeast(1)
    }

    @Test
    fun `monster attacks respect the hero's armour`() {
        val rng = Rng(9L)
        val hero = hero()
        hero.armor = ItemType.PLATE_ARMOR
        var hits = 0
        repeat(3000) {
            val full = hero.maxHp
            hero.hp = full
            val orc = Enemy(EnemyType.ORC, 1, 1)
            val r = Combat.enemyAttack(rng, orc, hero)
            if (r.hit) {
                hits++
                A.within(r.dmg, 1, 20, "orc damage")
                A.isTrue(hero.hp < full, "a landed hit must reduce hero hp")
            } else {
                A.eq(full, hero.hp, "a miss must not change hero hp")
            }
        }
        A.isTrue(hits > 0, "the orc never landed a hit")
        A.isTrue(hits < 3000, "the orc never missed")
    }
}
