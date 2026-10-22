package org.rsmod.content.skills.hunter

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.config.constants
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.table.hunter.HunterButterfliesRow
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Butterfly netting (OSRS Wiki: Butterfly (Hunter) and the creature pages): Catch with a wielded
 * butterfly net or magic butterfly net, or barehanded. A butterfly jar in the inventory keeps the
 * catch; without one the butterfly is released at once and its effect applies. Either way the
 * catch gives the wiki's Hunter XP. Jarred butterflies have a Release op (effect, empty jar back).
 *
 * Below the level (or 10 levels above it barehanded) every swing fails, as Jagex's "Did you know"
 * tip says for most Hunter creatures (no message; guess for butterflies). Guesses: one swing per
 * click (2 ticks), the impling net animation for barehanded catches too, the messages, the respawn
 * (7 ticks, the wiki's 4.2 s at the Great Conch).
 */
class ButterflyScript @Inject constructor(private val npcRepo: NpcRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        val rows = HunterButterfliesRow.all()
        for (row in rows) onOpNpc1(row.npc) { catch(it.npc, row) }
        for (row in rows.distinctBy { it.jar.id }) onOpHeld4(row.jar) { release(row) }
    }

    suspend fun ProtectedAccess.catch(npc: Npc, row: HunterButterfliesRow) {
        if (!npc.isCatchable()) return
        faceEntitySquare(npc)
        anim(CATCH_SEQ)
        delay(ATTEMPT_TICKS)
        if (!npc.isCatchable() || !rollCatch(row)) return
        if (npc.respawns) npcRepo.despawn(npc, RESPAWN_TICKS) else npcRepo.del(npc, Int.MAX_VALUE)
        statAdvance(HUNTER, row.xp.toDouble())
        if (BUTTERFLY_JAR in inv && invReplace(inv, BUTTERFLY_JAR, 1, row.jar.internalName).success) {
            mes("You manage to catch the ${row.kind}.")
        } else {
            applyEffect(row)
            // Catching a sunlight moth without a jar also restores 5% run energy (wiki).
            if (row.creature.internalName == SUNLIGHT_MOTH) restoreRunEnergy(SUNLIGHT_RUN_PERCENT)
            mes("You catch the ${row.kind} and release it.")
        }
    }

    private fun ProtectedAccess.rollCatch(row: HunterButterfliesRow): Boolean {
        val net = wieldedNet()
        val required = if (net == null) row.level + BAREHANDED_EXTRA_LEVELS else row.level
        if (stat(HUNTER) < required) return false
        val (low, high) =
            when (net) {
                NET -> row.netLow to row.netHigh
                MAGIC_NET -> row.magicLow to row.magicHigh
                else -> row.bareLow to row.bareHigh
            }
        return statRandom(HUNTER, low, high, invisibleBoost = 0)
    }

    suspend fun ProtectedAccess.release(row: HunterButterfliesRow) {
        if (invReplace(inv, row.jar.internalName, 1, BUTTERFLY_JAR).failure) return
        anim(RELEASE_SEQ)
        applyEffect(row)
        mes("You release the ${row.kind}.")
    }

    private fun ProtectedAccess.applyEffect(row: HunterButterfliesRow) {
        val stat = row.effectStat.internalName
        when (row.effect) {
            EFFECT_BOOST -> statBoost(stat, row.effectConstant, row.effectPercent)
            EFFECT_HEAL -> statHeal(stat, row.effectConstant, row.effectPercent)
            EFFECT_RESTORE ->
                for (restored in RESTORED_STATS) statHeal(restored, row.effectConstant, row.effectPercent)
        }
        if (row.hitpointsHeal > 0) statHeal(HITPOINTS, row.hitpointsHeal, 0)
    }

    private fun ProtectedAccess.restoreRunEnergy(percent: Int) {
        val restored = min(constants.run_max_energy, player.runEnergy + percent * constants.run_max_energy / 100)
        if (restored == player.runEnergy) return
        player.runEnergy = restored
        UpdateRun.energy(player, restored)
    }

    private fun Npc.isCatchable(): Boolean = isSlotAssigned && isVisible

    private fun ProtectedAccess.wieldedNet(): String? {
        val held = player.righthand ?: return null
        return listOf(NET, MAGIC_NET).firstOrNull { it.asRSCM(RSCMType.OBJ) == held.id }
    }

    companion object {
        const val HUNTER = "stat.hunter"
        const val HITPOINTS = "stat.hitpoints"
        const val NET = "obj.hunting_butterfly_net"
        const val MAGIC_NET = "obj.ii_magic_butterfly_net"
        const val BUTTERFLY_JAR = "obj.butterfly_jar"
        const val CATCH_SEQ = "seq.hunting_impling_catch"
        const val RELEASE_SEQ = "seq.hunting_butterfly_out_jar"
        const val BAREHANDED_EXTRA_LEVELS = 10
        const val ATTEMPT_TICKS = 2
        const val RESPAWN_TICKS = 7
        const val SUNLIGHT_MOTH = "npc.moth_sunlight"
        const val SUNLIGHT_RUN_PERCENT = 5

        /** `effect` column values (HunterButterfliesTable in the pack). */
        const val EFFECT_BOOST = 0
        const val EFFECT_HEAL = 1
        const val EFFECT_RESTORE = 2

        private val RESTORED_STATS =
            listOf(
                "attack", "defence", "strength", "ranged", "magic", "cooking", "woodcutting", "fletching",
                "fishing", "firemaking", "crafting", "smithing", "mining", "herblore", "agility", "thieving",
                "slayer", "farming", "runecrafting", "hunter", "construction",
            ).map { "stat.$it" }
    }
}
