package org.rsmod.content.other.special.attacks.melee

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/** Puncture: two quick stabs, each with +15% accuracy and +15% max hit (25% energy). */
class DragonDaggerSpecialAttack : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val puncture = DragonDagger(manager)
        for (dagger in DAGGERS) {
            registerMelee(dagger, puncture)
        }
    }

    private class DragonDagger(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            puncture(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            puncture(target, attack)
            return true
        }

        private fun ProtectedAccess.puncture(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.puncture")
            spotanim(
                spot = "spotanim.sp_attack_puncture_spotanim",
                slot = constants.spotanim_slot_combat,
                height = 100,
            )
            repeat(2) {
                val damage =
                    manager.rollMeleeDamage(
                        source = this,
                        target = target,
                        attack = attack,
                        accuracyMultiplier = 1.15,
                        maxHitMultiplier = 1.15,
                        blockType = MeleeAttackType.Stab,
                    )
                manager.giveCombatXp(this, target, attack, damage)
                manager.queueMeleeHit(this, target, damage)
            }
            manager.continueCombat(this, target)
        }
    }

    private companion object {
        val DAGGERS =
            listOf(
                "obj.dragon_dagger",
                "obj.dragon_dagger_p",
                "obj.dragon_dagger_p+",
                "obj.dragon_dagger_p++",
            )
    }
}
