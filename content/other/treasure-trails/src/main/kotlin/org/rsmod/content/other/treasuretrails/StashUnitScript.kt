package org.rsmod.content.other.treasuretrails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.other.treasuretrails.TreasureTrailScript.Companion.objName
import org.rsmod.content.other.treasuretrails.TreasureTrailScript.Companion.outfitSlots
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * STASH units (OSRS Wiki: STASH) for the emote steps of the tiers this module runs, from the
 * `hidey_hole_loc` and outfit of Jagex's emote clue rows. Build (op1) needs the tier's Construction
 * level, a hammer, a saw, 2 of the tier's planks and 10 nails of one metal, and sets the loc's multiloc varbit
 * (`hh_constructed_*`), which RuneLite's Clue Scroll plugin checks (clientscript 1479). Search (op2)
 * stores the outfit or takes it back, with the messages the plugin tracks.
 *
 * Deviations: only the first item listed for each outfit slot is stored, from the inventory.
 */
class StashUnitScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (unit in UNITS) {
            val loc = RSCM.getReverseMapping(RSCMType.LOC, unit.loc)
            onOpLoc1(loc) { build(unit) }
            onOpLoc2(loc) { search(unit) }
        }
    }

    suspend fun ProtectedAccess.build(unit: StashUnit) {
        if (isBuilt(player, unit)) return
        if (stat(CONSTRUCTION) < unit.tier.stashLevel) {
            mes("You need a Construction level of ${unit.tier.stashLevel} to build this STASH unit.")
            return
        }
        val plank = unit.tier.stashPlank
        val nails = NAILS.firstOrNull { inv.count(it) >= NAILS_NEEDED }
        if (HAMMER !in inv || SAW !in inv || inv.count(plank) < PLANKS_NEEDED || nails == null) {
            val planks = ServerCacheManager.getItem(plank.asRSCM(RSCMType.OBJ))?.name?.lowercase() ?: "plank"
            mes("You need a hammer, a saw, $PLANKS_NEEDED ${planks}s and $NAILS_NEEDED nails to build this.")
            return
        }
        anim(BUILD_ANIM)
        delay(BUILD_TICKS)
        if (invDel(inv, plank, PLANKS_NEEDED).failure) return
        if (invDel(inv, nails, NAILS_NEEDED).failure) return
        statAdvance(CONSTRUCTION, unit.tier.stashXp)
        VarPlayerIntMapSetter.set(player, unit.varbit, 1)
    }

    fun ProtectedAccess.search(unit: StashUnit) {
        if (!isBuilt(player, unit)) return
        if (isFilled(player, unit)) {
            if (inv.freeSpace() < unit.items.size) {
                mes("You need ${unit.items.size} free inventory spaces to take your items out.")
                return
            }
            for (item in unit.items) invAdd(inv, item)
            setFilled(player, unit, false)
            mes("You withdraw your items from the STASH unit.")
            return
        }
        if (unit.items.any { it !in inv }) {
            mes("You don't have the items to store in this STASH unit.")
            return
        }
        for (item in unit.items) {
            if (invDel(inv, item).failure) return
        }
        setFilled(player, unit, true)
        mes("You deposit your items into the STASH unit.")
    }

    data class StashUnit(val loc: Int, val varbit: String, val tier: TrailTier, val bit: Int, val items: List<String>)

    companion object {
        const val CONSTRUCTION = "stat.construction"
        const val HAMMER = "obj.hammer"
        const val SAW = "obj.poh_saw"
        const val PLANKS_NEEDED = 2
        const val NAILS_NEEDED = 10
        const val BUILD_ANIM = "seq.human_poh_build"
        const val BUILD_TICKS = 3
        val NAILS =
            listOf(
                "obj.nails_bronze",
                "obj.nails_iron",
                "obj.nails",
                "obj.nails_black",
                "obj.nails_mithril",
                "obj.nails_adamant",
                "obj.nails_rune",
            )

        val UNITS: List<StashUnit> by lazy {
            TrailSteps.ALL.flatMap { (tier, steps) ->
                steps.filterIsInstance<TrailStep.Emote>()
                    .filter { it.stashLoc != null && it.outfits.isNotEmpty() }
                    .distinctBy { it.stashLoc }
                    .sortedBy { it.stashLoc }
                    .mapIndexedNotNull { bit, step ->
                        val loc = step.stashLoc!!
                        val varbit = ServerCacheManager.getObject(loc)?.multiVarBit ?: return@mapIndexedNotNull null
                        if (varbit < 0) return@mapIndexedNotNull null
                        val items = outfitSlots(step.outfits.first()).map { objName(it.first().id) }
                        StashUnit(loc, RSCM.getReverseMapping(RSCMType.VARBIT, varbit), tier, bit, items)
                    }
            }
        }

        fun isBuilt(player: Player, unit: StashUnit): Boolean = player.vars[unit.varbit] == 1

        fun isFilled(player: Player, unit: StashUnit): Boolean =
            (player.vars[unit.tier.stashFilledVarp] shr unit.bit) and 1 == 1

        private fun setFilled(player: Player, unit: StashUnit, filled: Boolean) {
            val bit = 1 shl unit.bit
            val varp = unit.tier.stashFilledVarp
            val value = player.vars[varp]
            VarPlayerIntMapSetter.set(player, varp, if (filled) value or bit else value and bit.inv())
        }
    }
}
