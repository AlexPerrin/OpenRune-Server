package org.rsmod.content.travel.fairyrings

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.table.FairyringRow
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Fairy rings on Jagex's `fairyring` dbtable (code, destination, travel log component and text).
 * Configure opens the dial (398) with the travel log (381) beside it; the dials are
 * `fairyring_1..3`, which the dial buttons' clientscript also turns on the client. The travel log
 * lists the codes the player has travelled to (server varps), and Last-destination uses
 * `fairyring_lastloc_opvis` (64 + the row's multiloc state), which the loc's op text reads.
 */
class FairyRingScript : PluginScript() {
    private var Player.dial1 by intVarBit("varbit.fairyring_1")
    private var Player.dial2 by intVarBit("varbit.fairyring_2")
    private var Player.dial3 by intVarBit("varbit.fairyring_3")
    private var Player.lastDestination by intVarBit("varbit.fairyring_lastloc_opvis")

    override fun ScriptContext.startup() {
        onOpLoc1(MINOR_HUB) { zanaris() }
        for (hub in listOf(MINOR_HUB, HOME_HUB)) {
            onOpLoc2(hub) { configure() }
            onOpLoc3(hub) { lastDestination() }
        }
        for ((dial, button) in DIAL_BUTTONS) {
            onIfModalButton("component.fairyrings:${button.first}") { turn(dial, 1) }
            onIfModalButton("component.fairyrings:${button.second}") { turn(dial, 3) }
        }
        onIfModalButton("component.fairyrings:confirm") { confirm() }
        for (row in FairyringRow.all()) {
            onIfModalButton(logComponent(row)) { if (it.op == IfButtonOp.Op1) useCode(row) }
        }
    }

    private fun ProtectedAccess.configure() {
        if (!wieldsFairyMagic(player)) {
            mes(NO_STAFF)
            return
        }
        ifOpenMainSidePair(DIAL_INTERFACE, LOG_INTERFACE)
        for (row in FairyringRow.all()) {
            val desc = row.desc ?: continue
            if (hasVisited(player, row)) ifSetText(logComponent(row), desc)
        }
        runClientScript(
            SORT_UPDATE_SCRIPT,
            comp("component.fairyrings_log:contents"),
            comp("component.fairyrings_log:scrollbar"),
            1,
            1,
            comp("component.fairyrings_log:divider"),
            comp("component.fairyrings_log:faves"),
        )
    }

    private fun ProtectedAccess.turn(dial: Int, steps: Int) {
        when (dial) {
            1 -> player.dial1 = (player.dial1 + steps) % 4
            2 -> player.dial2 = (player.dial2 + steps) % 4
            else -> player.dial3 = (player.dial3 + steps) % 4
        }
    }

    private fun ProtectedAccess.useCode(row: FairyringRow) {
        player.dial1 = row.id / 100
        player.dial2 = row.id / 10 % 10
        player.dial3 = row.id % 10
    }

    private suspend fun ProtectedAccess.confirm() {
        val id = player.dial1 * 100 + player.dial2 * 10 + player.dial3
        val row = FairyringRow.all().firstOrNull { it.id == id }
        ifClose()
        ClientScripts.mesLayerClose(player, MESLAYER_MODE_SEARCH)
        travel(row?.takeIf { isDestination(it) })
    }

    private suspend fun ProtectedAccess.zanaris() {
        val here = ringAt(player.coords)
        if (!wieldsFairyMagic(player) && here?.noStaffReturn != true) {
            mes(NO_STAFF)
            return
        }
        travel(FairyringRow.all().firstOrNull { it.code == ZANARIS_CODE })
    }

    private suspend fun ProtectedAccess.lastDestination() {
        if (!wieldsFairyMagic(player)) {
            mes(NO_STAFF)
            return
        }
        val state = player.lastDestination - LAST_DESTINATION_BASE
        val row = FairyringRow.all().firstOrNull { it.multilocState == state && isDestination(it) } ?: return
        travel(row)
    }

    /**
     * [row] null (a code with no ring) or the ring the player stands in: no teleport, and the
     * player remarks on it (OSRS Wiki, Fairy ring).
     */
    private suspend fun ProtectedAccess.travel(row: FairyringRow?) {
        arriveDelay()
        anim(VANISH_SEQ)
        spotanim(RING_SPOTANIM)
        delay(TRAVEL_DELAY)
        val here = ringAt(player.coords)
        if (row != null && row.id != here?.id) {
            telejump(row.destCoord)
            player.lastDestination = LAST_DESTINATION_BASE + row.multilocState
            markVisited(player, row)
            anim(APPEAR_SEQ)
            return
        }
        anim(APPEAR_SEQ)
        say(HARDLY_MOVED)
    }

    private fun ringAt(coords: CoordGrid): FairyringRow? =
        FairyringRow.all().firstOrNull {
            isDestination(it) && it.destCoord.level == coords.level && it.destCoord.chebyshevDistance(coords) <= 3
        }

    private fun comp(name: String): Int = name.asRSCM(RSCMType.COMPONENT)

    companion object {
        const val MINOR_HUB = "loc.fairyring_minorhub"
        const val HOME_HUB = "loc.fairyring_homehub"
        const val DIAL_INTERFACE = "interface.fairyrings"
        const val LOG_INTERFACE = "interface.fairyrings_log"
        const val SORT_UPDATE_SCRIPT = 402
        const val ZANARIS_CODE = "B K S"
        const val LAST_DESTINATION_BASE = 64
        const val TRAVEL_DELAY = 3
        const val MESLAYER_MODE_SEARCH = 28
        const val VANISH_SEQ = "seq.human_fairy_vanish"
        const val APPEAR_SEQ = "seq.human_fairy_appear"
        const val RING_SPOTANIM = "spotanim.fairy_flower_ring"
        const val HARDLY_MOVED = "Wow, fairy magic sure is useful, I hardly moved at all!"
        const val NO_STAFF = "The fairy rings only work for those who wield fairy magic."
        const val PLACEHOLDER_X = 65
        const val LOG_A = "varp.fairyring_travel_log_a"
        const val LOG_B = "varp.fairyring_travel_log_b"

        val DIAL_BUTTONS =
            mapOf(
                1 to ("1_clockwise" to "1_anticlockwise"),
                2 to ("2_clockwise" to "2_anticlockwise"),
                3 to ("3_clockwise" to "3_anticlockwise"),
            )
        val STAVES =
            setOf("obj.dramen_staff", "obj.dramen_staff_air", "obj.dramen_staff_fire", "obj.dramen_staff_water")

        fun logComponent(row: FairyringRow): String = "component.fairyrings_log:${row.textComponent.internalName}"

        /** Unused codes have no destination: Jagex fills them with the 1_1_1_1_1 placeholder. */
        fun isDestination(row: FairyringRow): Boolean = row.desc != null && row.destCoord.x > PLACEHOLDER_X

        fun wieldsFairyMagic(player: Player): Boolean {
            val weapon = player.righthand ?: return false
            return STAVES.any { it.asRSCM(RSCMType.OBJ) == weapon.id }
        }

        fun hasVisited(player: Player, row: FairyringRow): Boolean {
            val state = row.multilocState
            val varp = if (state < 32) LOG_A else LOG_B
            return player.vars[varp] and (1 shl (state % 32)) != 0
        }

        fun markVisited(player: Player, row: FairyringRow) {
            val state = row.multilocState
            val varp = if (state < 32) LOG_A else LOG_B
            VarPlayerIntMapSetter.set(player, varp, player.vars[varp] or (1 shl (state % 32)))
        }
    }
}
