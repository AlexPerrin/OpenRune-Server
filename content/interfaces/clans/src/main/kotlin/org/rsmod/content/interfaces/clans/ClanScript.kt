package org.rsmod.content.interfaces.clans

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfSubType
import jakarta.inject.Inject
import org.rsmod.api.net.clan.Clan
import org.rsmod.api.net.clan.ClanRank
import org.rsmod.api.net.clan.Clans
import org.rsmod.api.player.input.DialogInput
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifCloseSub
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfModalDrag
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private const val PERMISSION_ACCESS = 2943
private const val EDIT_PUBLIC_PROFILE = 2947
private const val ASSIGN_RANKS = 2948
private const val EDIT_RANK_TITLES = 2949
private const val EXPEL_MEMBERS = 2950
private const val ICONS = 2952
private const val NOBODY = 128
private const val HOME_WORLD_SETTING = 6
private const val INTERESTS_SETTING = 10
private const val OUTFIT_SETTING = 11
private const val MEMBER_COLUMN_RANK = 0
private const val MEMBER_COLUMN_EXPEL = 6
private const val MEMBER_COLUMNS = 7
private const val INFO_STATIC = "clientscript.[clientscript,clan_info_static]"
private const val INFO_EDITABLE = "clientscript.[clientscript,clan_info_editable]"
private const val RANK_POPUP = "clientscript.[clientscript,clan_rankpopup]"
private const val INTERESTS_POPUP = "clientscript.[clientscript,clan_interests]"
private const val OUTFIT_POPUP = "clientscript.[clientscript,clan_outfit]"
private const val COMLEVELS = "clientscript.[clientscript,script4257]"
private const val SKILLTOTALS = "clientscript.[clientscript,script4258]"
private const val TIMEOUT_CYCLES = 5
private const val JOIN_DELAY = 2
private const val INFO_REFRESH_CYCLES = 6

class ClanScript @Inject constructor(private val clans: Clans, private val eventBus: EventBus) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.Login> { clans.login(player) }
        onEvent<SessionStateEvent.Logout> { clans.logout(player) }
        onEvent<SessionStateEvent.Delete> { clans.logout(player) }
        onPlayerSoftTimer("timer.clan_channel_hint") { clans.channelHint(player) }
        onPlayerSoftTimer("timer.clan_info_refresh") { player.refreshInfo() }

        onIfOpen("interface.clans_sidepanel") {
            for (button in listOf("chat", "info", "refresh")) {
                player.ifSetEvents("component.clans_sidepanel:$button", -1..-1, IfEvent.Op1)
            }
        }
        onIfOverlayButton("component.clans_sidepanel:chat") { toggleChannel() }
        onIfOverlayButton("component.clans_sidepanel:info") { openInfo() }
        onIfOverlayButton("component.clans_sidepanel:refresh") { clans.resendSettings(player) }

        onIfOpen("interface.clans_info") { player.enableInfoButtons() }
        onIfModalButton("component.clans_info:close") { ifClose() }
        onIfModalButton("component.clans_info:members") { openMembers() }
        onIfModalButton("component.clans_info:permissions") { openPermissions() }
        onIfModalButton("component.clans_info:titles") { openRankTitles() }
        onIfModalButton("component.clans_info:quit") { leaveClan() }
        onIfModalButton("component.clans_info:abdication") { resign() }
        onIfModalButton("component.clans_info:name") { rename() }
        onIfModalButton("component.clans_info:homeworld") { setHomeWorld() }
        onIfModalButton("component.clans_info:interests") { editInterests() }
        onIfModalButton("component.clans_info:talkrank") { editPermission(TALK_IN_CHAT) }
        onIfModalButton("component.clans_info:allowunaffined") { toggleGuestsListen() }
        onIfModalButton("component.clans_info:outfit") { editOutfit() }
        onIfOverlayButton("component.clans_interests:list") { player.toggleInterest(it.comsub) }
        onEvent<DialogInput> {
            if (player.ui.containsOverlay("interface.clans_interests")) player.submitInterests(count == 1)
        }

        onIfModalButton("component.clans_members:exit") { openInfo() }
        onIfModalButton("component.clans_members:options1") {
            player.setVar("varbit.clan_members_column_1", it.comsub - 1)
        }
        onIfModalButton("component.clans_members:options2") {
            player.setVar("varbit.clan_members_column_2", Math.floorMod(it.comsub - 2, MEMBER_COLUMNS))
        }
        onIfModalButton("component.clans_members:column1") {
            memberColumnOp(player.vars["varbit.clan_members_column_1"], it.comsub)
        }
        onIfModalButton("component.clans_members:column2") {
            val column = (player.vars["varbit.clan_members_column_2"] + 1) % MEMBER_COLUMNS
            memberColumnOp(column, it.comsub)
        }

        onIfModalButton("component.clans_permissions:exit") { openInfo() }
        onIfModalButton("component.clans_permissions:list_contents") {
            permissionAt(it.comsub)?.let { permission -> editPermission(permission) }
        }

        onIfModalButton("component.clans_ranktitles:cancel") { openInfo() }
        onIfModalButton("component.clans_ranktitles:save") { saveRankTitles() }
        onIfModalButton("component.clans_ranktitles:tiers_list") {
            if (it.op.slot == WIPE_OP) setTierTitle(it.comsub, 0, from = -1)
        }
        onIfModalDrag("component.clans_ranktitles:titles_list", "component.clans_ranktitles:tiers_list") {
            val title = (it.selectedSlot ?: return@onIfModalDrag) + 1
            setTierTitle(it.targetSlot ?: return@onIfModalDrag, title, from = -1)
        }
        onIfModalDrag("component.clans_ranktitles:tiers_list", "component.clans_ranktitles:tiers_list") {
            val from = it.selectedSlot ?: return@onIfModalDrag
            val rank = clanRanks().getOrNull(from) ?: return@onIfModalDrag
            setTierTitle(it.targetSlot ?: return@onIfModalDrag, editingTitle(rank), from)
        }
    }

    private fun ProtectedAccess.clan(): Clan? = clans.clanOf(player)

    private suspend fun ProtectedAccess.toggleChannel() {
        val clan = clan() ?: return
        player.setVar("varbit.clan_transmit_affined_timeout", 1)
        if (player in clan.channel) {
            clans.toggleChannel(player)
            delay(TIMEOUT_CYCLES)
        } else {
            delay(JOIN_DELAY)
            clans.toggleChannel(player)
            delay(TIMEOUT_CYCLES - JOIN_DELAY)
        }
        player.setVar("varbit.clan_transmit_affined_timeout", 0)
    }

    private fun ProtectedAccess.openInfo() {
        val clan = clan() ?: return
        ifOpenMainModal("interface.clans_info", -1, -2)
        player.drawInfo(clan)
        softTimer("timer.clan_info_refresh", INFO_REFRESH_CYCLES)
    }

    private fun Player.refreshInfo() {
        val clan = clans.clanOf(this)
        if (clan == null || !ui.containsModal("interface.clans_info")) {
            clearSoftTimer("timer.clan_info_refresh")
            return
        }
        drawInfo(clan)
    }

    private fun Player.drawInfo(clan: Clan) {
        val player = this
        val rank = clan.rankOf(player)
        val owner = rank == ClanRank.OWNER
        val profile = rank >= clan.permission(EDIT_PUBLIC_PROFILE)
        val access = rank >= clan.permission(PERMISSION_ACCESS)
        runClientScript(
            INFO_STATIC.script(),
            "Clan Size:<br>Owner:<br>Creation Date:",
            "$HIGHLIGHT${clan.members.size}$END<br>$HIGHLIGHT${clan.owner?.name.orEmpty()}$END<br>" +
                "$HIGHLIGHT${creationDate(clan.createdMinute)}$END",
        )
        val nameOp = if (owner) "Rename" else "Report"
        editable("Name:|$HIGHLIGHT${clan.name}$END|$nameOp", "name", true)
        val homeWorld = clan.intSettings[HOME_WORLD_SETTING] ?: 0
        editable("Home World:|" + if (homeWorld > 0) "$HIGHLIGHT$homeWorld$END" else "---", "homeworld", profile)
        ifSetHide("component.clans_info:abdication", !owner)
        if (owner) {
            val ownership =
                if (clan.resignAt > 0) {
                    "Ownership:|You have ${HIGHLIGHT}resigned$END. ${HIGHLIGHT}" +
                        "${clans.replacementOwner(clan)?.name.orEmpty()}$END will become the owner."
                } else {
                    "Ownership:|${HIGHLIGHT}Click here$END to <col=ff0000>resign</col> as clan owner.|Resignation"
                }
            editable(ownership, "abdication", clan.resignAt == 0L)
        }
        editable("Interests:|" + interestsText(clan.intSettings[INTERESTS_SETTING] ?: 0), "interests", profile)
        editable("Talk in chat:|$HIGHLIGHT${clan.rankName(clan.talkRank())}$END", "talkrank", access)
        val listen = if (clan.allowUnaffined()) "Permitted" else "Blocked"
        editable("Guests may listen in chat:|$HIGHLIGHT$listen$END", "allowunaffined", access, 0)
    }

    private fun Player.editable(text: String, row: String, editable: Boolean, option: Int = -1) {
        runClientScript(
            INFO_EDITABLE.script(),
            text,
            "component.clans_info:$row".asRSCM(RSCMType.COMPONENT),
            if (editable) 1 else 0,
            option,
        )
    }

    private fun Player.enableInfoButtons() {
        val buttons =
            listOf(
                "close", "outfit", "members", "permissions", "titles", "quit", "name",
                "homeworld", "abdication", "interests", "talkrank", "allowunaffined",
            )
        for (button in buttons) {
            ifSetEvents("component.clans_info:$button", -1..-1, IfEvent.Op1)
        }
    }

    private fun ProtectedAccess.openMembers() {
        val clan = clan() ?: return
        if (player.vars["varbit.clan_members_maxrankplus1"] == 0) {
            player.setVar("varbit.clan_members_maxrankplus1", ClanRank.OWNER + 1)
        }
        ifOpenMainModal("interface.clans_members", -1, -2)
        ifSetEvents("component.clans_members:options1", 1..MEMBER_COLUMNS, IfEvent.Op1)
        ifSetEvents("component.clans_members:options2", 1..MEMBER_COLUMNS, IfEvent.Op1)
        ifSetEvents("component.clans_members:column1", 0 until MAX_MEMBERS, IfEvent.Op1)
        ifSetEvents("component.clans_members:column2", 0 until MAX_MEMBERS, IfEvent.Op1)
        ifSetEvents("component.clans_members:exit", -1..-1, IfEvent.Op1)
        val online = clan.members.map { member -> clan.online.firstOrNull { it.characterId == member.characterId } }
        runClientScript(COMLEVELS.script(), online.joinToString("|") { it?.combatLevel?.toString().orEmpty() })
        runClientScript(SKILLTOTALS.script(), online.joinToString("|") { it?.totalLevel()?.toString().orEmpty() })
        ifSetText("component.clans_members:comment", "Use the drop-down menus above to choose what data to see.")
    }

    private suspend fun ProtectedAccess.memberColumnOp(column: Int, slot: Int) {
        when (column) {
            MEMBER_COLUMN_RANK -> editRank(slot)
            MEMBER_COLUMN_EXPEL -> expel(slot)
        }
    }

    private suspend fun ProtectedAccess.editRank(slot: Int) {
        val clan = clan() ?: return
        val target = clan.members.getOrNull(slot) ?: return
        val rank = clan.rankOf(player)
        if (rank < clan.permission(ASSIGN_RANKS)) {
            mes("You do not have permission to assign ranks in this clan.")
            return
        }
        if (target.rank >= rank) {
            mes("You can only edit the ranks of players whose ranks are lower than yours.")
            return
        }
        val max = if (rank == ClanRank.OWNER) ClanRank.DEPUTY_OWNER else rank
        val chosen =
            rankPopup("component.clans_members:popup", "Set rank for ${target.name}:", target.rank, 0, max, false)
        if (chosen == null || chosen == target.rank || chosen !in clanRanks() || chosen > max) {
            return
        }
        val current = clan() ?: return
        if (current.members.getOrNull(slot) === target) {
            clans.setRank(current, slot, chosen)
        }
    }

    private suspend fun ProtectedAccess.expel(slot: Int) {
        val clan = clan() ?: return
        val target = clan.members.getOrNull(slot) ?: return
        val rank = clan.rankOf(player)
        if (rank < clan.permission(EXPEL_MEMBERS) || target.rank >= rank) {
            mes("You can only expel members whose ranks are lower than yours.")
            return
        }
        val confirmed =
            confirmOverlay(
                "component.clans_members:popup",
                "Expel",
                "Are you sure you want to expel ${target.name} from the clan?",
                "Cancel",
                "Expel",
            )
        val current = clan() ?: return
        if (!confirmed || current.members.getOrNull(slot) !== target) {
            return
        }
        clans.onlineMember(current, target.characterId)?.mes("You have been expelled from ${current.name}.")
        clans.removeMember(current, slot)
        clans.systemMessage(current, "${target.name} has been expelled from the clan by ${player.displayName}.")
        openMembers()
    }

    private suspend fun ProtectedAccess.rankPopup(
        target: String,
        title: String,
        current: Int,
        min: Int,
        max: Int,
        allowNobody: Boolean,
    ): Int? {
        ifOpenSub("interface.popupoverlay", target, IfSubType.Overlay)
        runClientScript(
            RANK_POPUP.script(),
            title,
            current,
            min,
            max,
            if (allowNobody) 1 else 0,
            target.asRSCM(RSCMType.COMPONENT),
        )
        val input = coroutine.pause(ResumePCountDialogInput::class)
        ifCloseSub("interface.popupoverlay")
        return input.count - 1
    }

    private fun ProtectedAccess.openPermissions() {
        clan() ?: return
        ifOpenMainModal("interface.clans_permissions", -1, -2)
        ifSetEvents("component.clans_permissions:list_contents", 0..PERMISSION_SLOTS, IfEvent.Op1)
        ifSetEvents("component.clans_permissions:exit", -1..-1, IfEvent.Op1)
    }

    private fun permissionAt(slot: Int): Int? {
        if (slot == 0) {
            return PERMISSION_ACCESS
        }
        var index = 1
        for (category in cacheEnum("enum.clan_permission_categories").values) {
            val structs = cacheEnum(category as Int)
            val last = structs.size - 1
            for ((key, struct) in structs) {
                if (key < 0 || key >= last) {
                    continue
                }
                if (index == slot) {
                    return struct as Int
                }
                index++
            }
        }
        return null
    }

    private suspend fun ProtectedAccess.editPermission(permission: Int) {
        val clan = clan() ?: return
        val setting = PERMISSION_SETTINGS[permission] ?: return
        val rank = clan.rankOf(player)
        if (rank < clan.permission(PERMISSION_ACCESS)) {
            mes("You do not have permission to change this setting.")
            return
        }
        if (setting == GUESTS_LISTEN_KEY) {
            toggleGuestsListen()
            return
        }
        val struct = ServerCacheManager.getStruct(permission)
        val params = struct?.params.orEmpty()
        val name = params["param.clan_permission_name".asRSCM(RSCMType.PARAM)] as? String ?: ""
        val min = params["param.clan_permission_minrank".asRSCM(RSCMType.PARAM)] as? Int ?: ClanRank.GUEST
        val allowNobody = params["param.clan_permission_allownobody".asRSCM(RSCMType.PARAM)] == 1
        val current = clan.permissionRank(permission)
        val onInfo = player.ui.containsModal("interface.clans_info")
        val target = if (onInfo) "component.clans_info:popup" else "component.clans_permissions:popup"
        val title = name.replace("<br>", " ").replace(Regex("<[^>]*>"), "")
        val chosen = rankPopup(target, title, current, min, rank, allowNobody) ?: return
        val valid = chosen in clanRanks() && chosen in min..rank || allowNobody && chosen == NOBODY
        val live = clan() ?: return
        if (!valid || chosen == current) {
            return
        }
        when (setting) {
            TALK_RANK_KEY -> clans.setBaseSettings(live, chosen, live.kickRank(), live.allowUnaffined())
            KICK_RANK_KEY -> clans.setBaseSettings(live, live.talkRank(), chosen, live.allowUnaffined())
            else -> clans.setIntSetting(live, setting, chosen + 2)
        }
        if (onInfo) {
            player.drawInfo(live)
        }
    }

    private fun ProtectedAccess.toggleGuestsListen() {
        val clan = clan() ?: return
        if (clan.rankOf(player) < clan.permission(PERMISSION_ACCESS)) {
            return
        }
        clans.setBaseSettings(clan, clan.talkRank(), clan.kickRank(), !clan.allowUnaffined())
        if (player.ui.containsModal("interface.clans_info")) {
            player.drawInfo(clan)
        }
    }

    private suspend fun ProtectedAccess.rename() {
        val clan = clan() ?: return
        if (clan.rankOf(player) != ClanRank.OWNER) {
            mes("Thank you for your report.")
            return
        }
        val name = stringDialog("Enter a new name for your clan:").trim()
        if (!validClanName(name) || name == clan.name) {
            if (name.isNotEmpty() && name != clan.name) mes("That name is not allowed.")
            return
        }
        clans.nameTaken(name) { taken ->
            val live = clans.clanOf(player) ?: return@nameTaken
            if (taken) {
                player.mes("That clan name is already taken.")
                return@nameTaken
            }
            clans.rename(live, name)
            player.drawInfo(live)
        }
    }

    private suspend fun ProtectedAccess.setHomeWorld() {
        val clan = clan() ?: return
        if (clan.rankOf(player) < clan.permission(EDIT_PUBLIC_PROFILE)) {
            return
        }
        val world = countDialog("Enter the clan's home world:")
        val live = clan() ?: return
        if (world !in 1..MAX_WORLD) {
            mes("Please enter a valid world.")
            return
        }
        clans.setIntSetting(live, HOME_WORLD_SETTING, world)
        player.drawInfo(live)
    }

    private fun ProtectedAccess.editInterests() {
        val clan = clan() ?: return
        if (clan.rankOf(player) < clan.permission(EDIT_PUBLIC_PROFILE)) {
            return
        }
        player.setVar("varp.clan_editing_0", clan.intSettings[INTERESTS_SETTING] ?: 0)
        ifOpenSub("interface.clans_interests", "component.clans_info:popup", IfSubType.Overlay)
        ifSetEvents("component.clans_interests:list", 0 until INTEREST_BITS, IfEvent.Op1)
        runClientScript(INTERESTS_POPUP.script(), "component.clans_info:popup".asRSCM(RSCMType.COMPONENT))
    }

    private fun Player.toggleInterest(bit: Int) {
        if (bit in 0 until INTEREST_BITS) {
            setVar("varp.clan_editing_0", vars["varp.clan_editing_0"] xor (1 shl bit))
        }
    }

    private fun Player.submitInterests(save: Boolean) {
        ifCloseSub("interface.clans_interests", eventBus)
        val clan = clans.clanOf(this) ?: return
        if (save && clan.rankOf(this) >= clan.permission(EDIT_PUBLIC_PROFILE)) {
            clans.setIntSetting(clan, INTERESTS_SETTING, vars["varp.clan_editing_0"])
            drawInfo(clan)
        }
    }

    private suspend fun ProtectedAccess.editOutfit() {
        val clan = clan() ?: return
        if (clan.rankOf(player) < clan.permission(ICONS)) {
            return
        }
        player.setVar("varp.clan_editing_0", clan.intSettings[OUTFIT_SETTING] ?: 0)
        ifOpenSub("interface.clans_outfit", "component.clans_info:popup", IfSubType.Overlay)
        runClientScript(OUTFIT_POPUP.script(), "component.clans_info:popup".asRSCM(RSCMType.COMPONENT))
        val input = coroutine.pause(ResumePCountDialogInput::class)
        ifCloseSub("interface.clans_outfit")
        val live = clan() ?: return
        if (input.count != 0) {
            clans.setIntSetting(live, OUTFIT_SETTING, input.count)
        }
    }

    private suspend fun ProtectedAccess.resign() {
        val clan = clan() ?: return
        if (clan.rankOf(player) != ClanRank.OWNER || clan.resignAt > 0) {
            return
        }
        val next = clans.replacementOwner(clan)
        if (next == null) {
            mes("You are the only member of the clan. Leave the clan to delete it.")
            return
        }
        val confirmed =
            confirmOverlay(
                "component.clans_info:popup",
                "Resignation",
                "In 7 days ${next.name} will become the owner of the clan. Are you sure?",
                "Cancel",
                "Resign",
            )
        val live = clan() ?: return
        if (confirmed) {
            clans.resign(live)
            player.drawInfo(live)
        }
    }

    private suspend fun ProtectedAccess.leaveClan() {
        val clan = clan() ?: return
        val owner = clan.rankOf(player) == ClanRank.OWNER
        if (owner && clan.members.size > 1) {
            mes("You must resign as clan owner before you can leave the clan.")
            return
        }
        val text =
            if (owner) "You are the last member. Leaving will delete ${clan.name}. Are you sure?"
            else "Are you sure you want to leave ${clan.name}?"
        val confirmed = confirmOverlay("component.clans_info:popup", "Leave Clan", text, "Cancel", "Leave")
        val live = clan() ?: return
        val slot = live.members.indexOfFirst { it.characterId == player.characterId }
        if (!confirmed || slot == -1) {
            return
        }
        ifClose()
        clans.removeMember(live, slot)
        mes("You have left ${live.name}.")
        if (live.members.isNotEmpty()) {
            clans.systemMessage(live, "${player.displayName} has left the clan.")
        }
    }

    private fun ProtectedAccess.openRankTitles() {
        val clan = clan() ?: return
        ifOpenMainModal("interface.clans_ranktitles", -1, -2)
        for ((rank, _) in RANK_TITLE_FIELDS) {
            player.setVar(editingVarbit(rank), clan.titleOf(rank))
        }
        val permitted = clan.rankOf(player) >= clan.permission(EDIT_RANK_TITLES)
        player.setVar("varbit.clan_editing_permitted", if (permitted) 1 else 0)
        if (permitted) {
            val tiers = clanRanks().size
            ifSetEvents("component.clans_ranktitles:titles_list", 0 until TITLE_COUNT, IfEvent.Depth1, IfEvent.DragTarget)
            ifSetEvents(
                "component.clans_ranktitles:tiers_list",
                0 until tiers,
                IfEvent.Op10,
                IfEvent.Depth1,
                IfEvent.DragTarget,
            )
            ifSetEvents("component.clans_ranktitles:save", -1..-1, IfEvent.Op1)
        }
        ifSetEvents("component.clans_ranktitles:cancel", -1..-1, IfEvent.Op1)
    }

    private fun ProtectedAccess.editingTitle(rank: Int): Int =
        RANK_TITLE_FIELDS[rank]?.let { player.vars[editingVarbit(rank)] } ?: 0

    private fun ProtectedAccess.setTierTitle(tier: Int, title: Int, from: Int) {
        clan() ?: return
        if (player.vars["varbit.clan_editing_permitted"] != 1) {
            return
        }
        val ranks = clanRanks()
        val target = ranks.getOrNull(tier) ?: return
        if (lockedRank(target) || target !in RANK_TITLE_FIELDS || title !in 0..TITLE_COUNT) {
            return
        }
        val displaced = editingTitle(target)
        for ((index, rank) in ranks.withIndex()) {
            if (rank !in RANK_TITLE_FIELDS) {
                continue
            }
            when {
                index == tier -> player.setVar(editingVarbit(rank), title)
                title != 0 && editingTitle(rank) == title ->
                    player.setVar(editingVarbit(rank), if (index == from) displaced else 0)
            }
        }
    }

    private fun ProtectedAccess.saveRankTitles() {
        val clan = clan() ?: return
        if (clan.rankOf(player) < clan.permission(EDIT_RANK_TITLES)) {
            return
        }
        val titles = RANK_TITLE_FIELDS.keys.associateWith { editingTitle(it) }
        for ((setting, value) in clan.withTitle(titles)) {
            if ((clan.intSettings[setting] ?: 0) != value) {
                clans.setIntSetting(clan, setting, value)
            }
        }
        openInfo()
    }

    private fun Clan.permission(permission: Int): Int = permissionRank(permission)

    private fun Player.totalLevel(): Int =
        ServerCacheManager.getStats().values.sumOf {
            val stat = dev.openrune.rscm.RSCM.getReverseMapping(RSCMType.STAT, it.id)
            if (stat == "stat.unreleased") 0 else statBase(stat)
        }

    private fun Player.setVar(name: String, value: Int) = VarPlayerIntMapSetter.set(this, name, value)

    private fun String.script(): Int = asRSCM(RSCMType.CLIENTSCRIPT)

    private companion object {
        const val TALK_IN_CHAT = 2954
        const val GUESTS_LISTEN_KEY = -3
        const val WIPE_OP = 10
        const val MAX_MEMBERS = 500
        const val PERMISSION_SLOTS = 50
        const val INTEREST_BITS = 16
        const val TITLE_COUNT = 265
        const val MAX_WORLD = 2047

        fun editingVarbit(rank: Int): String = "varbit.clan_editing_ranktitle_%03d".format(rank)
    }
}

private const val TALK_RANK_KEY = -1
private const val KICK_RANK_KEY = -2

private val PERMISSION_SETTINGS: Map<Int, Int> by lazy {
    cacheEnum("enum.clan_permission_settings").mapValues { it.value as Int }
}

internal fun Clan.permissionRank(permission: Int): Int {
    val setting = PERMISSION_SETTINGS[permission] ?: return ClanRank.OWNER
    return when (setting) {
        TALK_RANK_KEY -> talkRank()
        KICK_RANK_KEY -> kickRank()
        else -> intSettings[setting]?.minus(2) ?: ClanRank.OWNER
    }
}

internal fun validClanName(name: String): Boolean =
    name.length in 1..MAX_CLAN_NAME && name.all { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }

private const val MAX_CLAN_NAME = 20
