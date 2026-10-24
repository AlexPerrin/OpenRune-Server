package org.rsmod.content.other.treasuretrails

import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook

/**
 * Key steps: killing one of the step's npcs while holding the clue drops its key for the killer
 * (OSRS Wiki, e.g. Market Guard: the key drops only for a player on that step; that it drops on
 * every such kill, and the drop duration, are our choices).
 */
class TrailKeyDropHook @Inject constructor(private val script: TreasureTrailScript) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val key = script.keyDrop(context.hero, context.npc.id) ?: return
        script.dropKey(key, context.dropCoords, context.hero)
    }
}
