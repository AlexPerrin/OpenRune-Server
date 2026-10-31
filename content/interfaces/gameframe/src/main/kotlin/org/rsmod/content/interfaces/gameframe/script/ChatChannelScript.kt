package org.rsmod.content.interfaces.gameframe.script

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.net.friendchat.FRIEND_CHAT_ENTER_RANK
import org.rsmod.api.net.friendchat.FRIEND_CHAT_KICK_RANK
import org.rsmod.api.net.friendchat.FRIEND_CHAT_NAME
import org.rsmod.api.net.friendchat.FRIEND_CHAT_TALK_RANK
import org.rsmod.api.net.friendchat.FriendChatRank
import org.rsmod.api.net.friendchat.FriendChats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private const val CHANNEL_NAME_MAX_LENGTH = 12

private val RANK_EVENTS =
    arrayOf(
        IfEvent.Op1,
        IfEvent.Op2,
        IfEvent.Op3,
        IfEvent.Op4,
        IfEvent.Op5,
        IfEvent.Op6,
        IfEvent.Op7,
        IfEvent.Op8,
        IfEvent.Op9,
    )

private val RANK_OPTIONS =
    listOf(
        "Anyone",
        "Any friends",
        "Recruit+",
        "Corporal+",
        "Sergeant+",
        "Lieutenant+",
        "Captain+",
        "General+",
        "Only me",
    )

private val CHANNEL_TABS =
    listOf(
        "interface.chatchannel_current",
        "interface.clans_sidepanel",
        "interface.clans_guest_sidepanel",
        "interface.grouping",
    )

class ChatChannelScript
@Inject
constructor(private val friendChats: FriendChats, private val eventBus: EventBus) : PluginScript() {
    private var Player.channelTab by intVarBit("varbit.side_channels_tab_selected")

    override fun ScriptContext.startup() {
        onIfOpen("interface.side_channels") {
            for (tab in CHANNEL_TABS.indices) {
                player.ifSetEvents("component.side_channels:tab_$tab", -1..-1, IfEvent.Op1)
            }
            player.openChannelTab(player.channelTab)
        }
        for (tab in CHANNEL_TABS.indices) {
            onIfOverlayButton("component.side_channels:tab_$tab") {
                player.channelTab = tab
                player.openChannelTab(tab)
            }
        }
        onEvent<SessionStateEvent.Login> { friendChats.login(player) }
        onEvent<SessionStateEvent.Logout> { friendChats.logout(player) }
        onEvent<SessionStateEvent.Delete> { friendChats.disconnected(player) }
        onIfOpen("interface.chatchannel_current") {
            player.ifSetEvents("component.chatchannel_current:setup", -1..-1, IfEvent.Op1)
        }
        onIfOpen("interface.chatchannel_setup") {
            VarPlayerIntMapSetter.set(player, "varbit.busy", 1)
            player.enableSetupOps()
        }
        onIfClose("interface.chatchannel_setup") { VarPlayerIntMapSetter.set(player, "varbit.busy", 0) }
        onIfOverlayButton("component.chatchannel_current:setup") { openSetup() }
        onIfModalButton("component.chatchannel_setup:opt_prefix") {
            if (it.op.slot == 1) setName() else disable()
        }
        onIfModalButton("component.chatchannel_setup:opt_enter") {
            setRank(FRIEND_CHAT_ENTER_RANK, it.op.slot)
        }
        onIfModalButton("component.chatchannel_setup:opt_talk") {
            setRank(FRIEND_CHAT_TALK_RANK, it.op.slot)
        }
        onIfModalButton("component.chatchannel_setup:opt_kick") {
            setRank(FRIEND_CHAT_KICK_RANK, it.op.slot)
        }
    }

    private fun Player.openChannelTab(tab: Int) {
        val interf = CHANNEL_TABS.getOrNull(tab) ?: return
        ifOpenOverlay(interf, "component.side_channels:contents", eventBus)
    }

    private fun Player.enableSetupOps() {
        ifSetEvents("component.chatchannel_setup:opt_prefix", -1..-1, IfEvent.Op1, IfEvent.Op2)
        for (option in listOf("opt_enter", "opt_talk")) {
            ifSetEvents("component.chatchannel_setup:$option", -1..-1, *RANK_EVENTS)
        }
        ifSetEvents("component.chatchannel_setup:opt_kick", -1..-1, *RANK_EVENTS.copyOfRange(3, 9))
    }

    private fun ProtectedAccess.openSetup() {
        updateSetupTexts()
        ifOpenMainModal("interface.chatchannel_setup")
    }

    private suspend fun ProtectedAccess.setName() {
        val name = stringDialog("Enter chat prefix:").trim()
        if (name.isEmpty()) {
            return
        }
        player.attr[FRIEND_CHAT_NAME] = name.take(CHANNEL_NAME_MAX_LENGTH)
        changed()
    }

    private fun ProtectedAccess.disable() {
        player.attr.remove(FRIEND_CHAT_NAME)
        changed()
    }

    private fun ProtectedAccess.setRank(key: AttributeKey<Int>, opSlot: Int) {
        player.attr[key] = opSlot + FriendChatRank.GUEST - 1
        changed()
    }

    private fun ProtectedAccess.changed() {
        updateSetupTexts()
        friendChats.settingsChanged(player)
    }

    private fun ProtectedAccess.updateSetupTexts() {
        ifSetText("component.chatchannel_setup:opt_enter", player.rankText(FRIEND_CHAT_ENTER_RANK, FriendChatRank.GUEST))
        ifSetText("component.chatchannel_setup:opt_talk", player.rankText(FRIEND_CHAT_TALK_RANK, FriendChatRank.GUEST))
        ifSetText("component.chatchannel_setup:opt_kick", player.rankText(FRIEND_CHAT_KICK_RANK, FriendChatRank.OWNER))
        ifSetText("component.chatchannel_setup:opt_prefix", player.attr[FRIEND_CHAT_NAME] ?: "Chat disabled")
    }

    private fun Player.rankText(key: AttributeKey<Int>, default: Int): String =
        RANK_OPTIONS[(attr[key] ?: default) - FriendChatRank.GUEST]
}
