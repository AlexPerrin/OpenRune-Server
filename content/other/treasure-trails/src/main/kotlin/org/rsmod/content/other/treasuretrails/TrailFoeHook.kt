package org.rsmod.content.other.treasuretrails

import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook

class TrailFoeHook @Inject constructor(private val script: TreasureTrailScript) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        script.foeKilled(context.hero, context.npc)
    }
}
