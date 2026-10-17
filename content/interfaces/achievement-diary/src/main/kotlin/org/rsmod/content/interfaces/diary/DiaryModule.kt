package org.rsmod.content.interfaces.diary

import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.plugin.module.PluginModule

public class DiaryModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(DiaryKillHook::class.java)
    }
}

internal class DiaryKillHook : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        DiaryHooks.npcKilled(context.hero, context.npc.type.name, context.dropCoords)
    }
}
