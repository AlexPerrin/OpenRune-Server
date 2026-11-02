package org.rsmod.content.interfaces.clans

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import org.rsmod.api.net.clan.Clan
import org.rsmod.api.net.clan.Clans
import org.rsmod.api.player.input.ResumePStringDialogInput
import org.rsmod.api.player.ironman.isAnyIronman
import org.rsmod.api.player.ironman.isHardcoreIronman
import org.rsmod.api.player.ironman.isUltimateIronman
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.ClientScripts.mesLayerMode9
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private const val CLAN_SEARCH_MODE = 7
private const val TIMEOUT_CYCLES = 5
private const val JOIN_DELAY = 2

class ClanGuestScript @Inject constructor(private val clans: Clans) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.Login> { player.rejoin() }
        onIfOpen("interface.clans_guest_sidepanel") {
            for (button in listOf("chat", "info", "search", "refresh")) {
                player.ifSetEvents("component.clans_guest_sidepanel:$button", -1..-1, IfEvent.Op1)
            }
        }
        onIfOverlayButton("component.clans_guest_sidepanel:search") { search() }
        onIfOverlayButton("component.clans_guest_sidepanel:chat") { toggleChannel() }
        onIfOverlayButton("component.clans_guest_sidepanel:refresh") {
            clans.resendSettings(player, guest = true)
        }
    }

    private suspend fun ProtectedAccess.search() {
        if (clans.guestOf(player) != null) {
            clans.discardGuest(player)
            return
        }
        mesLayerMode9(player, "Enter the name of the clan to chat in:", CLAN_SEARCH_MODE)
        val name = coroutine.pause(ResumePStringDialogInput::class).text.trim()
        if (name.isEmpty()) {
            return
        }
        val player = player
        clans.findClan(name) { clan ->
            if (clan == null) {
                player.mes("Could not find a clan named $name.", ChatType.ClanMessage)
            } else {
                player.join(clan)
            }
        }
    }

    private suspend fun ProtectedAccess.toggleChannel() {
        val clan = clans.guestOf(player) ?: return
        player.setVar("varbit.clan_transmit_guest_timeout", 1)
        if (player in clan.channel) {
            clans.leaveGuestChannel(player)
            delay(TIMEOUT_CYCLES)
        } else {
            delay(JOIN_DELAY)
            clans.guestOf(player)?.let { player.join(it) }
            delay(TIMEOUT_CYCLES - JOIN_DELAY)
        }
        player.setVar("varbit.clan_transmit_guest_timeout", 0)
    }

    private fun Player.rejoin() {
        val clanId = vars["varp.clan_guest_channel"]
        if (clanId == 0) {
            return
        }
        val player = this
        clans.findClan(clanId) { clan ->
            if (clan == null || !clans.isLoggedIn(player)) {
                if (clan == null) player.setVar("varp.clan_guest_channel", 0)
                return@findClan
            }
            player.join(clan)
        }
    }

    private fun Player.join(clan: Clan) {
        val refusal = refusal(clan)
        if (refusal != null) {
            setVar("varp.clan_guest_channel", 0)
            mes(refusal, ChatType.ClanMessage)
            return
        }
        clans.joinAsGuest(this, clan)
    }

    private fun Player.refusal(clan: Clan): String? {
        val requirements = clan.guestRequirements()
        return when {
            clan.memberOf(this) != null -> "You are a member of that clan. Use the clan tab to chat in it."
            !clan.allowUnaffined() -> "That clan does not allow guests in its channel."
            clan.bannedFrom(characterId) -> "You are banned from that clan's channel."
            combatLevel < GUEST_COMBAT.of(requirements) ||
                totalLevel() < GUEST_TOTAL.of(requirements) ||
                vars["varp.qp"] < GUEST_QP.of(requirements) ||
                !ironStatusMet(GUEST_IRON.of(requirements)) ->
                "You do not meet that clan's requirements for guests."
            else -> null
        }
    }

    private fun Player.ironStatusMet(status: Int): Boolean =
        when (status) {
            IRON_ANY -> isAnyIronman
            IRON_ULTIMATE -> isUltimateIronman
            IRON_HARDCORE -> isHardcoreIronman
            else -> true
        }

    private fun Player.setVar(name: String, value: Int) = VarPlayerIntMapSetter.set(this, name, value)
}
