package org.rsmod.api.net.clan

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import net.rsprot.protocol.game.outgoing.clan.ClanChannelDelta
import net.rsprot.protocol.game.outgoing.clan.ClanChannelFull
import net.rsprot.protocol.game.outgoing.clan.ClanSettingsDelta
import net.rsprot.protocol.game.outgoing.clan.ClanSettingsFull
import net.rsprot.protocol.game.outgoing.clan.MessageClanChannel
import net.rsprot.protocol.game.outgoing.clan.MessageClanChannelSystem
import net.rsprot.protocol.game.outgoing.clan.VarClanDisable
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.db.gateway.model.isOk
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.events.EventBus
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

private const val CLAN_TYPE = 0
private const val GUEST_TYPE = -1
private const val MAX_MEMBERS = 500
private const val LAST_SEEN_RUNEDAY_BASE = 6880
private const val LAST_SEEN_END_BIT = 12
private const val HOME_WORLD_SETTING = 6
private const val OWNER_ACTIVE_DAY_SETTING = 8
private const val OWNER_INACTIVE_DAYS = 90
private const val RESIGN_MILLIS = 7L * 24 * 60 * 60 * 1000
private const val AFFINITY_VARBIT = "varbit.clan_affinitystatus"
private const val GUEST_AFFINITY_VARBIT = "varbit.clan_guest_affinitystatus"
private const val HOME_WORLD_VARBIT = "varbit.clan_transmit_homeworld"
private const val CHANNEL_LEFT_VARBIT = "varbit.clan_channel_left"
private const val DISABLE_LAST_SEEN_VARBIT = "varbit.clan_disable_lastseen"
private const val NO_CLAN = 1
private const val LOADED = 3
private const val TALK_HINT = "To talk in your clan's channel, start each line of chat with // or /c."
private const val GUEST_TALK_HINT = "To talk in your guest clan's channel, start each line of chat with ///."
private const val GUEST_CHANNEL_VARP = "varp.clan_guest_channel"
private const val GUESTS_BLOCKED = "Guests are no longer allowed in this channel."
private val RUNEDAY_EPOCH: LocalDate = LocalDate.of(2002, 2, 27)

public data class ClanFounderAccept(val player: Player, val leader: Player) : UnboundEvent

public data class ClanAccessChanged(val player: Player) : UnboundEvent

public fun runeDay(): Int = ChronoUnit.DAYS.between(RUNEDAY_EPOCH, LocalDate.now()).toInt()

@Singleton
public class Clans
@Inject
constructor(
    private val db: GameDbManager,
    private val config: ServerConfig,
    private val eventBus: EventBus,
) {
    private val logger = InlineLogger()
    private val loaded = HashMap<Int, Clan>()
    private val clanOf = HashMap<Player, Clan>()
    private val guestOf = HashMap<Player, Clan>()
    private val sessions = HashSet<Player>()
    private val founderInvites = HashMap<Player, Player>()
    private val founding = HashSet<Player>()
    private val memberInvites = HashMap<Player, Player>()
    private var messageCounter = 0

    init {
        db.request(
            request = { connection -> GameDbResult.Ok(ClanStore.createTables(connection)) },
            response = { if (!it.isOk()) logger.error { "Could not create the clan tables: $it" } },
        )
    }

    public fun clanOf(player: Player): Clan? = clanOf[player]

    public fun guestOf(player: Player): Clan? = guestOf[player]

    private fun typeOf(clan: Clan, player: Player): Int =
        if (clanOf[player] === clan) CLAN_TYPE else GUEST_TYPE

    public fun onlineMember(clan: Clan, characterId: Int): Player? =
        clan.online.firstOrNull { it.characterId == characterId }

    public fun isLoggedIn(player: Player): Boolean = player in sessions

    public fun inviteMember(invitee: Player, recruiter: Player) {
        memberInvites[invitee] = recruiter
    }

    public fun takeMemberInvite(invitee: Player, recruiter: Player): Boolean =
        memberInvites.remove(invitee, recruiter)

    public fun isFounding(player: Player): Boolean = player in founding

    public fun setFounding(player: Player, founder: Boolean) {
        if (founder) founding += player else founding -= player
    }

    public fun inviteFounder(invitee: Player, leader: Player) {
        founderInvites[invitee] = leader
    }

    public fun acceptFounderInvite(player: Player, target: Player): Boolean {
        if (founderInvites[player] != target) {
            return false
        }
        founderInvites.remove(player)
        eventBus.publish(ClanFounderAccept(player, target))
        return true
    }

    public fun login(player: Player) {
        sessions += player
        player.client.write(VarClanDisable)
        VarPlayerIntMapSetter.set(player, GUEST_AFFINITY_VARBIT, NO_CLAN)
        val characterId = player.characterId
        db.request(
            request = { connection ->
                val clanId = ClanStore.clanIdOf(connection, characterId)
                val clan = clanId?.let { ClanStore.load(connection, it) }
                GameDbResult.Ok(clan)
            },
            response = { result ->
                val clan = if (result.isOk()) result.value else null
                if (player in sessions && guestOf[player] == null) {
                    player.client.write(ClanSettingsFull(GUEST_TYPE, ClanSettingsFull.LeaveUpdate))
                }
                if (clan == null || player !in sessions) {
                    if (result.isOk()) VarPlayerIntMapSetter.set(player, AFFINITY_VARBIT, NO_CLAN)
                    return@request
                }
                attach(player, loaded[clan.id] ?: clan.also { loaded[it.id] = it })
            },
        )
    }

    private fun attach(player: Player, clan: Clan) {
        val member = clan.memberOf(player) ?: return
        if (guestOf[player] === clan) {
            discardGuest(player)
        }
        clan.online += player
        clanOf[player] = clan
        sendSettingsFull(player, clan)
        VarPlayerIntMapSetter.set(player, AFFINITY_VARBIT, LOADED)
        val homeWorld = clan.intSettings[HOME_WORLD_SETTING] ?: 0
        if (homeWorld > 0) {
            VarPlayerIntMapSetter.set(player, HOME_WORLD_VARBIT, homeWorld)
        }
        val today = runeDay()
        val seen = player.vars[DISABLE_LAST_SEEN_VARBIT] == 0
        if (member.name != player.displayName || seen) {
            member.name = player.displayName
            if (seen) member.lastSeenDay = today
            saveMember(clan, member)
        }
        if (seen) {
            val slot = clan.members.indexOf(member)
            val updates =
                mutableListOf<ClanSettingsDelta.Update>(
                    ClanSettingsDelta.SetMemberExtraInfoUpdate(
                        slot,
                        today - LAST_SEEN_RUNEDAY_BASE,
                        0,
                        LAST_SEEN_END_BIT,
                    )
                )
            if (member.rank == ClanRank.OWNER) {
                clan.intSettings[OWNER_ACTIVE_DAY_SETTING] = today
                saveSetting(clan, OWNER_ACTIVE_DAY_SETTING)
                updates += ClanSettingsDelta.SetIntSettingUpdate(OWNER_ACTIVE_DAY_SETTING, today)
            }
            settingsDelta(clan, updates)
        }
        if (member.rank == ClanRank.OWNER) {
            clan.intSettings[Clan.OWNER_ACTIVE_DAY_KEY] = today
            saveSetting(clan, Clan.OWNER_ACTIVE_DAY_KEY)
        }
        applyResignation(clan)
        if (player.vars[CHANNEL_LEFT_VARBIT] == 0) {
            joinChannel(player, clan)
        }
        eventBus.publish(ClanAccessChanged(player))
    }

    public fun logout(player: Player) {
        sessions -= player
        founderInvites.remove(player)
        founderInvites.values.removeIf { it == player }
        founding -= player
        memberInvites.remove(player)
        memberInvites.values.removeIf { it == player }
        guestOf.remove(player)?.let { guest ->
            leaveChannel(player, guest, explicit = false)
            guest.listeners -= player
            release(guest)
        }
        val clan = clanOf.remove(player) ?: return
        leaveChannel(player, clan, explicit = false)
        clan.online -= player
        release(clan)
    }

    private fun release(clan: Clan) {
        if (clan.online.isEmpty() && clan.listeners.isEmpty() && loaded[clan.id] === clan) {
            loaded.remove(clan.id)
        }
    }

    public fun toggleChannel(player: Player) {
        val clan = clanOf[player] ?: return
        if (player in clan.channel) {
            leaveChannel(player, clan, explicit = true)
        } else {
            VarPlayerIntMapSetter.set(player, CHANNEL_LEFT_VARBIT, 0)
            joinChannel(player, clan)
        }
    }

    private fun joinChannel(player: Player, clan: Clan) {
        if (player in clan.channel) {
            return
        }
        val add = ClanChannelDelta.AddUserEvent(player.displayName, config.world, clan.rankOf(player))
        channelDelta(clan, listOf(add))
        clan.channel += player
        player.client.write(ClanChannelFull(typeOf(clan, player), channelJoinUpdate(clan)))
        player.softTimer("timer.clan_channel_hint", 1)
    }

    public fun channelHint(player: Player) {
        player.clearSoftTimer("timer.clan_channel_hint")
        if (clanOf[player]?.channel?.contains(player) == true) {
            player.mes(TALK_HINT, ChatType.ClanMessage)
        }
        if (guestOf[player]?.channel?.contains(player) == true) {
            player.mes(GUEST_TALK_HINT, ChatType.ClanMessage)
        }
    }

    private fun leaveChannel(player: Player, clan: Clan, explicit: Boolean) {
        val slot = clan.channel.indexOf(player)
        if (slot == -1) {
            return
        }
        val type = typeOf(clan, player)
        clan.channel.removeAt(slot)
        if (explicit) {
            if (type == CLAN_TYPE) VarPlayerIntMapSetter.set(player, CHANNEL_LEFT_VARBIT, 1)
            player.mes("You have left the channel.", ChatType.ClanMessage)
        }
        player.client.write(ClanChannelFull(type, ClanChannelFull.LeaveUpdate))
        channelDelta(clan, listOf(ClanChannelDelta.DeleteUserEvent(slot)))
    }

    public fun message(player: Player, text: String, guest: Boolean) {
        val clan = (if (guest) guestOf[player] else clanOf[player]) ?: return
        if (player !in clan.channel) {
            return
        }
        if (clan.rankOf(player) < clan.talkRank()) {
            player.mes("You do not have a high enough rank to talk in this channel.", ChatType.ClanMessage)
            return
        }
        val counter = nextMessageCounter()
        clan.channel.forEach {
            val message =
                MessageClanChannel(
                    typeOf(clan, it),
                    player.displayName,
                    config.world,
                    counter,
                    player.modLevel.clientCode,
                    text,
                )
            it.client.write(message)
        }
    }

    public fun systemMessage(clan: Clan, text: String) {
        val counter = nextMessageCounter()
        clan.channel.forEach {
            it.client.write(MessageClanChannelSystem(typeOf(clan, it), config.world, counter, text))
        }
    }

    public fun setRank(clan: Clan, slot: Int, rank: Int) {
        val member = clan.members.getOrNull(slot) ?: return
        if (member.rank == rank) {
            return
        }
        member.rank = rank
        saveMember(clan, member)
        val online = clan.channel.indexOfFirst { it.characterId == member.characterId }
        if (online != -1) {
            val user = clan.channel[online]
            val update =
                ClanChannelDelta.UpdateUserDetailsV2Event(online, user.displayName, rank, config.world)
            channelDelta(clan, listOf(update))
        }
        settingsDelta(clan, listOf(ClanSettingsDelta.SetMemberRankUpdate(slot, rank)))
        clan.online.firstOrNull { it.characterId == member.characterId }?.let {
            eventBus.publish(ClanAccessChanged(it))
        }
    }

    public fun addMember(clan: Clan, player: Player, rank: Int): Boolean {
        if (clan.members.size >= MAX_MEMBERS || clan.memberOf(player) != null) {
            return false
        }
        val member =
            ClanMember(
                characterId = player.characterId,
                name = player.displayName,
                rank = rank,
                joinRuneDay = runeDay(),
                joinedAt = System.currentTimeMillis(),
            )
        clan.members += member
        saveMember(clan, member)
        settingsDelta(clan, listOf(ClanSettingsDelta.AddMemberV2Update(member.name, member.joinRuneDay)))
        attach(player, clan)
        return true
    }

    public fun removeMember(clan: Clan, slot: Int) {
        val member = clan.members.getOrNull(slot) ?: return
        val player = clan.online.firstOrNull { it.characterId == member.characterId }
        if (player != null) {
            leaveChannel(player, clan, explicit = false)
            clan.online -= player
            clanOf.remove(player)
            player.client.write(ClanSettingsFull(CLAN_TYPE, ClanSettingsFull.LeaveUpdate))
            VarPlayerIntMapSetter.set(player, AFFINITY_VARBIT, NO_CLAN)
            VarPlayerIntMapSetter.set(player, CHANNEL_LEFT_VARBIT, 0)
            eventBus.publish(ClanAccessChanged(player))
        }
        clan.members.removeAt(slot)
        val clanId = clan.id
        val characterId = member.characterId
        if (clan.members.isEmpty()) {
            clan.listeners.toList().forEach { discardGuest(it) }
            loaded.remove(clanId)
            db.request(
                request = { connection -> GameDbResult.Ok(ClanStore.deleteClan(connection, clanId)) },
                response = { logError(it) },
            )
            return
        }
        db.request(
            request = { connection -> GameDbResult.Ok(ClanStore.deleteMember(connection, characterId)) },
            response = { logError(it) },
        )
        settingsDelta(clan, listOf(ClanSettingsDelta.DeleteMemberUpdate(slot)))
    }

    public fun setIntSetting(clan: Clan, setting: Int, value: Int) {
        clan.intSettings[setting] = value
        saveSetting(clan, setting)
        settingsDelta(clan, listOf(ClanSettingsDelta.SetIntSettingUpdate(setting, value)))
        if (setting == HOME_WORLD_SETTING) {
            clan.online.forEach { VarPlayerIntMapSetter.set(it, HOME_WORLD_VARBIT, value) }
        }
        clan.online.toList().forEach { eventBus.publish(ClanAccessChanged(it)) }
    }

    public fun setBaseSettings(clan: Clan, talkRank: Int, kickRank: Int, allowUnaffined: Boolean) {
        clan.intSettings[Clan.TALK_RANK_KEY] = talkRank
        clan.intSettings[Clan.KICK_RANK_KEY] = kickRank
        clan.intSettings[Clan.ALLOW_UNAFFINED_KEY] = if (allowUnaffined) 1 else 0
        listOf(Clan.TALK_RANK_KEY, Clan.KICK_RANK_KEY, Clan.ALLOW_UNAFFINED_KEY).forEach {
            saveSetting(clan, it)
        }
        settingsDelta(
            clan,
            listOf(ClanSettingsDelta.BaseSettingsUpdate(allowUnaffined, talkRank, kickRank, 0, 0)),
        )
        channelDelta(
            clan,
            listOf(ClanChannelDelta.UpdateBaseSettingsEvent(clan.name, talkRank, kickRank)),
        )
        if (!allowUnaffined) {
            clan.channel.filter { clanOf[it] !== clan }.forEach { removeGuest(it, clan, GUESTS_BLOCKED) }
        }
    }

    public fun findClan(name: String, onResult: (Clan?) -> Unit) {
        val key = nameKey(name)
        loaded.values.firstOrNull { nameKey(it.name) == key }?.let {
            onResult(it)
            return
        }
        loadClan({ connection -> ClanStore.clanIdByName(connection, name) }, onResult)
    }

    public fun findClan(clanId: Int, onResult: (Clan?) -> Unit) {
        loaded[clanId]?.let {
            onResult(it)
            return
        }
        loadClan({ clanId }, onResult)
    }

    private fun loadClan(id: (DatabaseConnection) -> Int?, onResult: (Clan?) -> Unit) {
        db.request(
            request = { connection -> GameDbResult.Ok(id(connection)?.let { ClanStore.load(connection, it) }) },
            response = { result ->
                val clan = if (result.isOk()) result.value else null
                onResult(clan?.let { loaded[it.id] ?: it })
            },
        )
    }

    public fun joinAsGuest(player: Player, clan: Clan) {
        if (player !in sessions) {
            return
        }
        if (guestOf[player] !== clan) {
            guestOf[player]?.let { discardGuest(player) }
            loaded.putIfAbsent(clan.id, clan)
            guestOf[player] = clan
            clan.listeners += player
            player.client.write(ClanSettingsFull(GUEST_TYPE, settingsJoinUpdate(clan)))
            VarPlayerIntMapSetter.set(player, GUEST_AFFINITY_VARBIT, LOADED)
        }
        VarPlayerIntMapSetter.set(player, GUEST_CHANNEL_VARP, clan.id)
        if (player !in clan.channel) {
            player.mes("Now talking in the clan channel ${clan.name}.", ChatType.ClanMessage)
            joinChannel(player, clan)
        }
    }

    public fun leaveGuestChannel(player: Player) {
        val clan = guestOf[player] ?: return
        VarPlayerIntMapSetter.set(player, GUEST_CHANNEL_VARP, 0)
        leaveChannel(player, clan, explicit = true)
    }

    public fun discardGuest(player: Player) {
        val clan = guestOf.remove(player) ?: return
        VarPlayerIntMapSetter.set(player, GUEST_CHANNEL_VARP, 0)
        leaveChannel(player, clan, explicit = false)
        clan.listeners -= player
        player.client.write(ClanSettingsFull(GUEST_TYPE, ClanSettingsFull.LeaveUpdate))
        VarPlayerIntMapSetter.set(player, GUEST_AFFINITY_VARBIT, NO_CLAN)
        release(clan)
    }

    private fun removeGuest(guest: Player, clan: Clan, text: String) {
        VarPlayerIntMapSetter.set(guest, GUEST_CHANNEL_VARP, 0)
        leaveChannel(guest, clan, explicit = false)
        guest.mes(text, ChatType.ClanMessage)
    }

    private fun channelGuest(player: Player, memberIndex: Int, name: String): Pair<Clan, Player>? {
        val clan = clanOf[player] ?: return null
        val target = clan.channel.getOrNull(memberIndex) ?: return null
        if (clanOf[target] === clan || !target.displayName.equals(name, ignoreCase = true)) {
            return null
        }
        return clan to target
    }

    public fun kickGuest(player: Player, memberIndex: Int, name: String) {
        val (clan, guest) = channelGuest(player, memberIndex, name) ?: return
        if (clan.rankOf(player) < clan.kickRank()) {
            player.mes("You do not have a high enough rank to kick guests from this channel.", ChatType.ClanMessage)
            return
        }
        removeGuest(guest, clan, "You have been kicked from the channel.")
    }

    public fun banGuest(player: Player, memberIndex: Int, name: String) {
        val (clan, guest) = channelGuest(player, memberIndex, name) ?: return
        if (clan.rankOf(player) < clan.banRank()) {
            player.mes("You do not have a high enough rank to ban guests from this channel.", ChatType.ClanMessage)
            return
        }
        ban(clan, ClanBan(guest.characterId, guest.displayName))
    }

    public fun banByName(clan: Clan, name: String, onResult: (String) -> Unit) {
        db.request(
            request = { connection -> GameDbResult.Ok(ClanStore.characterByName(connection, name)) },
            response = { result ->
                val ban = if (result.isOk()) result.value else null
                when {
                    ban == null -> onResult("Could not find a player named $name.")
                    clan.members.any { it.characterId == ban.characterId } ->
                        onResult("You cannot ban a member of the clan.")
                    clan.bannedFrom(ban.characterId) -> onResult("${ban.name} is already on the ban list.")
                    else -> ban(clan, ban)
                }
            },
        )
    }

    private fun ban(clan: Clan, ban: ClanBan) {
        if (clan.bannedFrom(ban.characterId)) {
            return
        }
        clan.banned += ban
        val clanId = clan.id
        db.request(
            request = { connection -> GameDbResult.Ok(ClanStore.saveBan(connection, clanId, ban)) },
            response = { logError(it) },
        )
        settingsDelta(clan, listOf(ClanSettingsDelta.AddBannedUpdate(ban.name)))
        val guest = clan.listeners.firstOrNull { it.characterId == ban.characterId } ?: return
        guest.mes("You have been banned from the channel.", ChatType.ClanMessage)
        discardGuest(guest)
    }

    public fun unban(clan: Clan, name: String) {
        val index = clan.banned.indexOfFirst { nameKey(it.name) == nameKey(name) }
        if (index == -1) {
            return
        }
        val ban = clan.banned.removeAt(index)
        val clanId = clan.id
        db.request(
            request = { connection -> GameDbResult.Ok(ClanStore.deleteBan(connection, clanId, ban.characterId)) },
            response = { logError(it) },
        )
        settingsDelta(clan, listOf(ClanSettingsDelta.DeleteBannedUpdate(index)))
    }

    public fun resign(clan: Clan) {
        clan.resignAt = System.currentTimeMillis() + RESIGN_MILLIS
        saveClan(clan)
    }

    public fun replacementOwner(clan: Clan): ClanMember? =
        clan.members
            .filter { it.rank != ClanRank.OWNER }
            .sortedWith(compareByDescending<ClanMember> { it.rank }.thenBy { it.joinedAt })
            .firstOrNull()

    private fun applyResignation(clan: Clan) {
        val owner = clan.owner ?: return
        val ownerActiveDay = clan.intSettings[Clan.OWNER_ACTIVE_DAY_KEY] ?: runeDay()
        val inactive = runeDay() - ownerActiveDay >= OWNER_INACTIVE_DAYS
        val resigned = clan.resignAt in 1..System.currentTimeMillis()
        if (!inactive && !resigned) {
            return
        }
        val next = replacementOwner(clan) ?: return
        clan.resignAt = 0
        saveClan(clan)
        clan.intSettings[Clan.OWNER_ACTIVE_DAY_KEY] = runeDay()
        saveSetting(clan, Clan.OWNER_ACTIVE_DAY_KEY)
        setRank(clan, clan.members.indexOf(owner), ClanRank.DEPUTY_OWNER)
        setRank(clan, clan.members.indexOf(next), ClanRank.OWNER)
        settingsDelta(clan, listOf(ClanSettingsDelta.SetClanOwnerUpdate(clan.members.indexOf(next))))
    }

    public fun create(
        name: String,
        founders: List<Player>,
        onCreated: (Clan?) -> Unit,
    ) {
        val createdMinute = (System.currentTimeMillis() / 60_000).toInt()
        db.request(
            request = { connection ->
                val id = ClanStore.create(connection, name, createdMinute)
                GameDbResult.Ok(id?.let { Clan(it, name, createdMinute, 0) })
            },
            response = { result ->
                val clan = if (result.isOk()) result.value else null
                if (clan == null) {
                    onCreated(null)
                    return@request
                }
                loaded[clan.id] = clan
                founders.forEachIndexed { index, founder ->
                    if (founder in sessions) {
                        addMember(clan, founder, if (index == 0) ClanRank.OWNER else ClanRank.MEMBER)
                    }
                }
                onCreated(clan)
            },
        )
    }

    public fun nameTaken(name: String, onResult: (Boolean) -> Unit) {
        db.request(
            request = { connection -> GameDbResult.Ok(ClanStore.nameTaken(connection, name)) },
            response = { onResult(!it.isOk() || it.value) },
        )
    }

    public fun hasClan(player: Player, onResult: (Boolean) -> Unit) {
        val characterId = player.characterId
        db.request(
            request = { connection -> GameDbResult.Ok(ClanStore.clanIdOf(connection, characterId)) },
            response = { onResult(!it.isOk() || it.value != null) },
        )
    }

    public fun rename(clan: Clan, name: String) {
        clan.name = name
        saveClan(clan)
        settingsDelta(clan, listOf(ClanSettingsDelta.SetClanNameUpdate(name)))
        channelDelta(
            clan,
            listOf(ClanChannelDelta.UpdateBaseSettingsEvent(name, clan.talkRank(), clan.kickRank())),
        )
    }

    public fun resendSettings(player: Player, guest: Boolean = false) {
        (if (guest) guestOf[player] else clanOf[player])?.let { sendSettingsFull(player, it) }
    }

    public fun resendChannel(player: Player, guest: Boolean = false) {
        val clan = (if (guest) guestOf[player] else clanOf[player]) ?: return
        if (player in clan.channel) {
            player.client.write(ClanChannelFull(typeOf(clan, player), channelJoinUpdate(clan)))
        }
    }

    private fun sendSettingsFull(player: Player, clan: Clan) {
        player.client.write(ClanSettingsFull(typeOf(clan, player), settingsJoinUpdate(clan)))
    }

    private fun settingsJoinUpdate(clan: Clan): ClanSettingsFull.JoinUpdate =
        ClanSettingsFull.JoinUpdate(
            updateNum = clan.settingsUpdateNum,
            creationTime = clan.createdMinute,
            clanName = clan.name,
            allowUnaffined = clan.allowUnaffined(),
            talkRank = clan.talkRank(),
            kickRank = clan.kickRank(),
            lootshareRank = 0,
            coinshareRank = 0,
            affinedMembers =
                clan.members.map {
                    ClanSettingsFull.AffinedClanMember(
                        name = it.name,
                        rank = it.rank,
                        extraInfo = if (it.lastSeenDay > 0) it.lastSeenDay - LAST_SEEN_RUNEDAY_BASE else 0,
                        joinRuneDay = it.joinRuneDay,
                        muted = false,
                    )
                },
            bannedMembers = clan.banned.map { ClanSettingsFull.BannedClanMember(it.name) },
            settings =
                clan.intSettings.filterKeys { it >= 0 }.map { ClanSettingsFull.IntClanSetting(it.key, it.value) } +
                    clan.stringSettings.map { ClanSettingsFull.StringClanSetting(it.key, it.value) },
        )

    private fun channelJoinUpdate(clan: Clan): ClanChannelFull.JoinUpdate =
        ClanChannelFull.JoinUpdate(
            clanHash = clan.id.toLong(),
            updateNum = clan.channelUpdateNum,
            clanName = clan.name,
            discardedBoolean = true,
            kickRank = clan.kickRank(),
            talkRank = clan.talkRank(),
            members =
                clan.channel.map {
                    ClanChannelFull.ClanMember(it.displayName, clan.rankOf(it), config.world)
                },
        )

    private fun settingsDelta(clan: Clan, updates: List<ClanSettingsDelta.Update>) {
        val updateNum = clan.settingsUpdateNum++
        val member = ClanSettingsDelta(CLAN_TYPE, 0, updateNum, updates)
        clan.online.forEach { it.client.write(member) }
        val guest = ClanSettingsDelta(GUEST_TYPE, 0, updateNum, updates)
        clan.listeners.forEach { it.client.write(guest) }
    }

    private fun channelDelta(clan: Clan, events: List<ClanChannelDelta.Event>) {
        val updateNum = clan.channelUpdateNum++
        clan.channel.forEach {
            it.client.write(ClanChannelDelta(typeOf(clan, it), clan.id.toLong(), updateNum, events))
        }
    }

    private fun nextMessageCounter(): Int {
        messageCounter = (messageCounter + 1) and 0xFFFFFF
        return messageCounter
    }

    private fun saveMember(clan: Clan, member: ClanMember) {
        val clanId = clan.id
        db.request(
            request = { connection -> GameDbResult.Ok(ClanStore.saveMember(connection, clanId, member)) },
            response = { logError(it) },
        )
    }

    private fun saveSetting(clan: Clan, setting: Int) {
        val clanId = clan.id
        val int = clan.intSettings[setting] ?: 0
        val string = clan.stringSettings[setting]
        db.request(
            request = { connection ->
                GameDbResult.Ok(ClanStore.saveSetting(connection, clanId, setting, int, string))
            },
            response = { logError(it) },
        )
    }

    private fun saveClan(clan: Clan) {
        db.request(
            request = { connection -> GameDbResult.Ok(ClanStore.saveClan(connection, clan)) },
            response = { logError(it) },
        )
    }

    private fun logError(result: GameDbResult<Unit>) {
        if (!result.isOk()) {
            logger.error { "Clan database request failed: $result" }
        }
    }
}
