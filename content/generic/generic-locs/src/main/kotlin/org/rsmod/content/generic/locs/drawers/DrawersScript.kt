package org.rsmod.content.generic.locs.drawers

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Plain drawers and wardrobes: Open turns the loc into its open variant, which has Search and
 * Shut/Close. [Furniture.searchOp] is the open variant's Search slot (2 for most, 1 for the Seers'
 * Village drawers); Shut/Close is the slot after it.
 */
class DrawersScript @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        for (f in FURNITURE) {
            onOpLoc1(f.closed) { open(it.loc, f) }
            if (f.searchOp == 1) {
                onOpLoc1(f.opened) { search(f) }
                onOpLoc2(f.opened) { shut(it.loc, f) }
            } else {
                onOpLoc2(f.opened) { search(f) }
                onOpLoc3(f.opened) { shut(it.loc, f) }
            }
        }
    }

    private suspend fun ProtectedAccess.open(loc: BoundLocInfo, f: Furniture) {
        arriveDelay()
        anim(OPEN_SEQ)
        soundSynth(OPEN_SYNTH)
        delay(1)
        locRepo.change(loc, f.opened, DURATION)
    }

    private suspend fun ProtectedAccess.search(f: Furniture) {
        arriveDelay()
        mes("You search the ${f.noun} but find nothing.")
    }

    private suspend fun ProtectedAccess.shut(loc: BoundLocInfo, f: Furniture) {
        arriveDelay()
        anim(OPEN_SEQ)
        delay(1)
        locRepo.change(loc, f.closed, DURATION)
    }

    data class Furniture(val closed: String, val opened: String, val noun: String, val searchOp: Int = 2)

    companion object {
        const val OPEN_SEQ = "seq.human_openchest"
        const val OPEN_SYNTH = "synth.drawer_open"
        const val DURATION = 500

        val FURNITURE =
            listOf(
                Furniture("loc.drawers1", "loc.drawers1open", "drawers"),
                Furniture("loc.drawers2", "loc.drawers2open", "drawers"),
                Furniture("loc.drawers3", "loc.drawers3open", "drawers"),
                Furniture("loc.draynor_wardrobe", "loc.draynor_wardrobe_open", "wardrobe"),
                Furniture(
                    "loc.kr_seers_village_drawers1",
                    "loc.kr_seers_village_drawers1open",
                    "drawers",
                    searchOp = 1,
                ),
            )
    }
}
