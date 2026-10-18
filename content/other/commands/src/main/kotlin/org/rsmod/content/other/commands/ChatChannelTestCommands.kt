package org.rsmod.content.other.commands

import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.clan.ClanChannelDelta
import net.rsprot.protocol.game.outgoing.clan.ClanChannelFull
import net.rsprot.protocol.game.outgoing.clan.ClanSettingsFull
import net.rsprot.protocol.game.outgoing.clan.MessageClanChannel
import net.rsprot.protocol.game.outgoing.friendchat.MessageFriendChannel
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelFull.FriendChatEntry
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelFullV2
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelSingleUser
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * osrs-plugins-reverse: a fake friends chat and clan channel for the plugin host's
 * getFriendsChatManager / getClanChannel / getGuestClanChannel readers. OpenRune has no friends
 * chat or clan gameplay; this only pushes the packets (UPDATE_FRIENDCHAT_CHANNEL_FULL_V2,
 * UPDATE_FRIENDCHAT_CHANNEL_SINGLEUSER, CLANCHANNEL_FULL, CLANCHANNEL_DELTA, MESSAGE_FRIENDCHANNEL,
 * MESSAGE_CLANCHANNEL) to the player's own client. Nothing is shared with other players or kept
 * over a relog.
 *
 * `::fakeclan [join]` joins both, `::fakeclan add|del <name>` adds or removes a member in both
 * (single-user update / channel delta), `::fakeclan say <name> <text>` posts a line in both,
 * `::fakeclan guest` joins a guest clan channel (clan type -1), `::fakeclan leave` leaves all.
 *
 * Ranks are guesses from RuneLite's FriendsChatRank / ClanRank: friends chat 7 owner, 0 friend,
 * 4 lieutenant, kick rank 7; clan 126 owner, 100 administrator, 10 the first clan rank, -1 guest.
 * CLANSETTINGS_FULL goes with each clan channel (the clan tab waits for it); rune days count from
 * 2002-02-27 (guess). The clan tab (`clan_sidepanel_draw`) also needs varbit clan_affinitystatus 3
 * ("Connecting..." at 0, "No current clan" at 1).
 */
class ChatChannelTestCommands @Inject constructor(private val config: ServerConfig) :
    PluginScript() {
    private class State(
        val fcMembers: MutableList<String>,
        val clanMembers: MutableList<String>,
        var clanUpdateNum: Long,
        var guestMembers: MutableList<String>? = null,
        var guestUpdateNum: Long = 0,
    )

    private val states = HashMap<String, State>()
    private var messageCounter = ((System.currentTimeMillis() / 3_600_000L) % 300L).toInt() * 50_000

    override fun ScriptContext.startup() {
        onCommand(
            "fakeclan",
            "Fake friends chat + clan channel (::fakeclan [join|add n|del n|say n text|guest|leave])",
            ::fakeClan,
        )
    }

    private fun fakeClan(cheat: Cheat) =
        with(cheat) {
            when (args.firstOrNull()?.lowercase() ?: "join") {
                "join" -> join(player)
                "add" -> add(player, args.getOrNull(1) ?: return player.mes("Use as ::fakeclan add name"))
                "del" -> del(player, args.getOrNull(1) ?: return player.mes("Use as ::fakeclan del name"))
                "say" -> say(player, args.getOrNull(1), args.drop(2).joinToString(" "))
                "guest" -> guest(player)
                "leave" -> leave(player)
                else -> player.mes("Use as ::fakeclan [join|add n|del n|say n text|guest|leave]")
            }
        }

    private fun join(player: Player) {
        val self = player.displayName
        val state = State((listOf(self) + FAKE_MEMBERS).toMutableList(), (listOf(self) + FAKE_MEMBERS).toMutableList(), 1000L)
        states[self] = state
        player.client.write(
            UpdateFriendChatChannelFullV2(
                UpdateFriendChatChannelFullV2.JoinUpdate(
                    channelOwner = self,
                    channelName = FC_NAME,
                    kickRank = FC_RANK_OWNER,
                    entries = state.fcMembers.map { FriendChatEntry(it, config.world, fcRank(self, it), "") },
                ),
            ),
        )
        player.client.write(
            ClanChannelFull(
                CLAN_TYPE_MAIN,
                ClanChannelFull.JoinUpdate(
                    clanHash = CLAN_HASH,
                    updateNum = state.clanUpdateNum,
                    clanName = CLAN_NAME,
                    discardedBoolean = false,
                    kickRank = CLAN_RANK_ADMIN,
                    talkRank = CLAN_RANK_GUEST,
                    members = state.clanMembers.map { ClanChannelFull.ClanMember(it, clanRank(self, it), config.world) },
                ),
            ),
        )
        player.client.write(
            clanSettings(CLAN_TYPE_MAIN, CLAN_NAME, state.clanMembers.map { it to clanRank(self, it) }),
        )
        VarPlayerIntMapSetter.set(player, "varbit.clan_affinitystatus", AFFINITY_IN_CLAN)
        player.mes("Joined friends chat '$FC_NAME' and clan '$CLAN_NAME' (${state.fcMembers.size} members).")
    }

    private fun guest(player: Player) {
        val state = states[player.displayName] ?: return player.mes("Use ::fakeclan join first.")
        val members = mutableListOf("Fake Guest1", "Fake Guest2")
        state.guestMembers = members
        state.guestUpdateNum = 5000L
        player.client.write(
            ClanChannelFull(
                CLAN_TYPE_GUEST,
                ClanChannelFull.JoinUpdate(
                    clanHash = GUEST_HASH,
                    updateNum = state.guestUpdateNum,
                    clanName = GUEST_NAME,
                    discardedBoolean = false,
                    kickRank = CLAN_RANK_ADMIN,
                    talkRank = CLAN_RANK_GUEST,
                    members = members.map { ClanChannelFull.ClanMember(it, CLAN_RANK_FIRST, config.world) } +
                        ClanChannelFull.ClanMember(player.displayName, CLAN_RANK_GUEST, config.world),
                ),
            ),
        )
        player.client.write(
            clanSettings(CLAN_TYPE_GUEST, GUEST_NAME, members.map { it to CLAN_RANK_FIRST }),
        )
        VarPlayerIntMapSetter.set(player, "varbit.clan_guest_affinitystatus", AFFINITY_IN_CLAN)
        members += player.displayName
        player.mes("Joined guest clan channel '$GUEST_NAME' (${members.size} members).")
    }

    private fun add(player: Player, name: String) {
        val state = states[player.displayName] ?: return player.mes("Use ::fakeclan join first.")
        if (name in state.fcMembers) return player.mes("$name is already in the channels.")
        state.fcMembers += name
        state.clanMembers += name
        player.client.write(
            UpdateFriendChatChannelSingleUser(
                UpdateFriendChatChannelSingleUser.AddedFriendChatUser(name, config.world, FC_RANK_FRIEND, ""),
            ),
        )
        player.client.write(
            ClanChannelDelta(
                CLAN_TYPE_MAIN,
                CLAN_HASH,
                state.clanUpdateNum,
                listOf(ClanChannelDelta.AddUserEvent(name, config.world, CLAN_RANK_FIRST)),
            ),
        )
        // The client applies a delta whose updateNum equals its own and then increments it (guess,
        // from the desktop client's ClanChannelDelta handling).
        state.clanUpdateNum++
        player.mes("$name joined (friends chat ${state.fcMembers.size}, clan ${state.clanMembers.size}).")
    }

    private fun del(player: Player, name: String) {
        val state = states[player.displayName] ?: return player.mes("Use ::fakeclan join first.")
        val clanIndex = state.clanMembers.indexOfFirst { it.equals(name, ignoreCase = true) }
        val fcName = state.fcMembers.firstOrNull { it.equals(name, ignoreCase = true) }
        if (clanIndex < 0 || fcName == null) return player.mes("$name is not in the channels.")
        state.fcMembers -= fcName
        state.clanMembers.removeAt(clanIndex)
        player.client.write(
            UpdateFriendChatChannelSingleUser(
                UpdateFriendChatChannelSingleUser.RemovedFriendChatUser(fcName, config.world),
            ),
        )
        player.client.write(
            ClanChannelDelta(
                CLAN_TYPE_MAIN,
                CLAN_HASH,
                state.clanUpdateNum,
                listOf(ClanChannelDelta.DeleteUserEvent(clanIndex)),
            ),
        )
        state.clanUpdateNum++
        player.mes("$fcName left (friends chat ${state.fcMembers.size}, clan ${state.clanMembers.size}).")
    }

    private fun say(player: Player, name: String?, text: String) {
        if (states[player.displayName] == null) return player.mes("Use ::fakeclan join first.")
        if (name == null || text.isBlank()) return player.mes("Use as ::fakeclan say name text")
        player.client.write(MessageFriendChannel(name, FC_NAME, config.world, nextCounter(), 0, text))
        player.client.write(MessageClanChannel(CLAN_TYPE_MAIN, name, config.world, nextCounter(), 0, text))
    }

    private fun leave(player: Player) {
        val state = states.remove(player.displayName)
        player.client.write(UpdateFriendChatChannelFullV2(UpdateFriendChatChannelFullV2.LeaveUpdate))
        player.client.write(ClanChannelFull(CLAN_TYPE_MAIN, ClanChannelFull.LeaveUpdate))
        player.client.write(ClanSettingsFull(CLAN_TYPE_MAIN, ClanSettingsFull.LeaveUpdate))
        if (state?.guestMembers != null) {
            player.client.write(ClanChannelFull(CLAN_TYPE_GUEST, ClanChannelFull.LeaveUpdate))
            player.client.write(ClanSettingsFull(CLAN_TYPE_GUEST, ClanSettingsFull.LeaveUpdate))
        }
        VarPlayerIntMapSetter.set(player, "varbit.clan_affinitystatus", AFFINITY_NO_CLAN)
        VarPlayerIntMapSetter.set(player, "varbit.clan_guest_affinitystatus", AFFINITY_NO_CLAN)
        player.mes("Left the fake friends chat and clan channels.")
    }

    private fun clanSettings(clanType: Int, name: String, affined: List<Pair<String, Int>>): ClanSettingsFull {
        val runeDay = ((System.currentTimeMillis() - RUNE_DAY_EPOCH_MILLIS) / 86_400_000L).toInt()
        return ClanSettingsFull(
            clanType,
            ClanSettingsFull.JoinUpdate(
                updateNum = 1,
                creationTime = (System.currentTimeMillis() / 60_000L).toInt(),
                clanName = name,
                allowUnaffined = true,
                talkRank = CLAN_RANK_GUEST,
                kickRank = CLAN_RANK_ADMIN,
                lootshareRank = 0,
                coinshareRank = 0,
                affinedMembers =
                    affined.map { (member, rank) ->
                        ClanSettingsFull.AffinedClanMember(member, rank, 0, runeDay, false)
                    },
                bannedMembers = emptyList(),
                settings = emptyList(),
            ),
        )
    }

    private fun nextCounter(): Int {
        messageCounter = (messageCounter + 1) and 0xFFFFFF
        return messageCounter
    }

    private fun fcRank(owner: String, name: String): Int =
        when (name) {
            owner -> FC_RANK_OWNER
            "Fake Member2" -> FC_RANK_LIEUTENANT
            else -> FC_RANK_FRIEND
        }

    private fun clanRank(owner: String, name: String): Int =
        when (name) {
            owner -> CLAN_RANK_OWNER
            "Fake Member2" -> CLAN_RANK_ADMIN
            else -> CLAN_RANK_FIRST
        }

    private companion object {
        val FAKE_MEMBERS = listOf("Fake Member1", "Fake Member2")
        const val FC_NAME = "Test FC"
        const val CLAN_NAME = "Test Clan"
        const val GUEST_NAME = "Guest Clan"
        const val CLAN_HASH = 0x0BAD_C1A0_0000_0001L
        const val GUEST_HASH = 0x0BAD_C1A0_0000_0002L
        const val CLAN_TYPE_MAIN = 0
        const val CLAN_TYPE_GUEST = -1
        const val FC_RANK_FRIEND = 0
        const val FC_RANK_LIEUTENANT = 4
        const val FC_RANK_OWNER = 7
        const val CLAN_RANK_GUEST = -1
        const val CLAN_RANK_FIRST = 10
        const val CLAN_RANK_ADMIN = 100
        const val CLAN_RANK_OWNER = 126
        const val AFFINITY_NO_CLAN = 1
        const val AFFINITY_IN_CLAN = 3
        const val RUNE_DAY_EPOCH_MILLIS = 1_014_768_000_000L
    }
}
