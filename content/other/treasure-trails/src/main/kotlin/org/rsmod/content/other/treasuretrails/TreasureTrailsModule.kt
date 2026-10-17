package org.rsmod.content.other.treasuretrails

import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.plugin.module.PluginModule

class TreasureTrailsModule : PluginModule() {
    override fun bind() {
        bindInstance<TreasureTrailScript>()
        addSetBinding<SpadeDigHook>(TreasureTrailScript::class.java)
        addSetBinding<NpcDeathKillHook>(TrailKeyDropHook::class.java)
    }
}
