package org.rsmod.content.other.lootingbag

import org.rsmod.api.player.hook.PlayerObjTakeRedirectHook
import org.rsmod.plugin.module.PluginModule

public class LootingBagModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerObjTakeRedirectHook>(LootingBagTakeHook::class.java)
    }
}
