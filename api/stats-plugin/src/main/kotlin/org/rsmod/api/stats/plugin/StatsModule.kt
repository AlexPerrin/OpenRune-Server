package org.rsmod.api.stats.plugin

import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.stats.plugin.levelup.LevelUpScript
import org.rsmod.plugin.module.PluginModule

public class StatsModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerPostTickHook>(LevelUpScript::class.java)
    }
}
