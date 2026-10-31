package org.rsmod.content.other.special.attacks.melee

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.defenceLvl
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/** Smash: 1.5x max hit; a hit that deals damage lowers the target's defence by 30% (50% energy). */
class DragonWarhammerSpecialAttack : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val smash = DragonWarhammer(manager)
        for (hammer in HAMMERS) {
            registerMelee(hammer, smash)
        }
    }

    private class DragonWarhammer(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            val damage = smash(target, attack)
            if (damage > 0) {
                target.defenceLvl -= target.defenceLvl * DRAIN_PERCENT / 100
            }
            manager.continueCombat(this, target)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            val damage = smash(target, attack)
            if (damage > 0) {
                target.statSub("stat.defence", target.defenceLvl * DRAIN_PERCENT / 100, percent = 0)
            }
            manager.continueCombat(this, target)
            return true
        }

        private fun ProtectedAccess.smash(target: PathingEntity, attack: CombatAttack.Melee): Int {
            anim("seq.dragon_warhammer_sa_player")
            spotanim(
                spot = "spotanim.dragon_warhammer_sa_spotanim",
                slot = constants.spotanim_slot_combat,
            )
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.0,
                    maxHitMultiplier = 1.5,
                    blockType = MeleeAttackType.Crush,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            return damage
        }
    }

    private companion object {
        const val DRAIN_PERCENT = 30
        val HAMMERS = listOf("obj.dragon_warhammer", "obj.dragon_warhammer_ornament")
    }
}
