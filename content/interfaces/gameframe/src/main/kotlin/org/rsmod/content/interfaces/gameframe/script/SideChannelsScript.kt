package org.rsmod.content.interfaces.gameframe.script

import dev.openrune.types.aconverted.interf.IfSubType
import jakarta.inject.Inject
import org.rsmod.api.player.ui.ifOpenSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The chat-channel side tab (`side_channels`, 707) only draws its four sub-tab buttons; the
 * server opens the selected panel into `side_channels:contents` and keeps
 * `side_channels_tab_selected` in step: chat-channel (7), your clan (701), another clan (702),
 * grouping (76).
 */
class SideChannelsScript @Inject constructor(private val eventBus: EventBus) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOpen(SIDE_CHANNELS) { player.openChannelsTab(player.vars[SELECTED_TAB]) }
        for ((tab, _) in PANELS.withIndex()) {
            onIfOverlayButton("component.side_channels:tab_$tab") { player.openChannelsTab(tab) }
        }
    }

    private fun Player.openChannelsTab(tab: Int) {
        val panel = PANELS.getOrNull(tab) ?: return
        VarPlayerIntMapSetter.set(this, SELECTED_TAB, tab)
        ifOpenSub(panel, CONTENTS, IfSubType.Overlay, eventBus)
    }

    private companion object {
        const val SIDE_CHANNELS = "interface.side_channels"
        const val CONTENTS = "component.side_channels:contents"
        const val SELECTED_TAB = "varbit.side_channels_tab_selected"
        val PANELS =
            listOf(
                "interface.chatchannel_current",
                "interface.clans_sidepanel",
                "interface.clans_guest_sidepanel",
                "interface.grouping",
            )
    }
}
