package org.rsmod.api.net.friendchat

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.rsprot.protocol.game.outgoing.friendchat.MessageFriendChannel
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelFull.FriendChatEntry
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelFullV2
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelSingleUser
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.db.gateway.model.fold
import org.rsmod.api.net.central.OpenRuneCentralWorldLink
import org.rsmod.api.net.central.WorldLinkFrameSpecs
import org.rsmod.api.net.central.formatWorldName
import org.rsmod.api.net.central.nextPrivateMessageCounter
import org.rsmod.api.net.central.writeCentralSocialSnapshot
import org.rsmod.api.parsers.json.Json
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.vars.VarPlayerIntMap

public val FRIEND_CHAT_NAME: AttributeKey<String> = AttributeKey(persistenceKey = "friend_chat_name")
public val FRIEND_CHAT_RANKS: AttributeKey<MutableMap<String, Int>> =
    AttributeKey(persistenceKey = "friend_chat_ranks")
private val FRIEND_CHAT_LAST: AttributeKey<String> = AttributeKey(persistenceKey = "friend_chat_last")

public const val FRIEND_CHAT_ENTER_OPTION: String = "varbit.friendchat_enter_option"
public const val FRIEND_CHAT_TALK_OPTION: String = "varbit.friendchat_talk_option"
public const val FRIEND_CHAT_KICK_OPTION: String = "varbit.friendchat_kick_option"
public const val FRIEND_CHAT_CHANGES_TIMER: String = "timer.friendchat_changes"
private const val SETUP_VARP = "varp.friendchat_setup"
private const val CHANGES_PENDING_VARP = "varp.friendchat_changes_pending"

public object FriendChatRank {
    public const val GUEST: Int = -1
    public const val FRIEND: Int = 0
    public const val RECRUIT: Int = 1
    public const val GENERAL: Int = 6
    public const val OWNER: Int = 7
    public const val JMOD: Int = 127
}

public fun Player.friendChatRankOf(name: String): Int = attr[FRIEND_CHAT_RANKS]?.get(nameKey(name)) ?: 0

private fun friendChatOptionRank(option: Int): Int = option + FriendChatRank.GUEST

public fun VarPlayerIntMap.friendChatKickOption(): Int = this[FRIEND_CHAT_KICK_OPTION].takeIf { it != 0 } ?: ONLY_ME_OPTION

private const val CAPACITY = 500
private const val KICK_BAN_CYCLES = 6000
private const val JOIN_CHAT_LOGIN_SCRIPT = "clientscript.[clientscript,chatchannel_login]"
private const val CHANGES_DELAY_CYCLES = 101
private const val ONLY_ME_OPTION = 8
private const val MAX_RANKED = 400
private const val MAX_RANKED_FREE = 200
private const val MAX_BANS = 400
private const val MAX_BANS_FREE = 100
private const val KICK_NOTIFICATION = "-Attempting to kick player from chat-channel..."
private const val BLOCKED_VARBIT = "varbit.chatchannel_blocked"
private const val BLOCKED_BELOW_TOTAL_LEVEL = 150

private fun nameKey(name: String): String = name.trim().lowercase()

@Singleton
public class FriendChats
@Inject
constructor(
    private val playerList: PlayerList,
    private val db: GameDbManager,
    private val central: OpenRuneCentralWorldLink,
    private val config: ServerConfig,
    private val mapClock: MapClock,
    @Json private val objectMapper: ObjectMapper,
) {
    private val channels = HashMap<String, Channel>()
    private val joined = HashMap<Player, Channel>()
    private val joining = HashSet<Player>()

    public fun joinOrLeave(player: Player, ownerName: String?) {
        if (ownerName.isNullOrBlank()) {
            leave(player, explicit = true)
            return
        }
        if (player in joined || player.vars[BLOCKED_VARBIT] == 1 || !joining.add(player)) {
            return
        }
        player.mes("Attempting to join chat-channel...", ChatType.FriendsChatNotification)
        val active = channels[nameKey(ownerName)]
        if (active != null) {
            joining.remove(player)
            tryJoin(player, active)
            return
        }
        load(player, ownerName)
    }

    public fun updateBlocked(player: Player) {
        val total =
            ServerCacheManager.getStats().values.sumOf {
                val stat = RSCM.getReverseMapping(RSCMType.STAT, it.id)
                if (stat == "stat.unreleased") 0 else player.statBase(stat)
            }
        val blocked = !player.members && total < BLOCKED_BELOW_TOTAL_LEVEL
        VarPlayerIntMapSetter.set(player, BLOCKED_VARBIT, if (blocked) 1 else 0)
    }

    public fun login(player: Player) {
        updateBlocked(player)
        player.client.write(UpdateFriendChatChannelFullV2(UpdateFriendChatChannelFullV2.LeaveUpdate))
        val last = player.attr[FRIEND_CHAT_LAST] ?: return
        player.runClientScript(JOIN_CHAT_LOGIN_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT), last)
    }

    public fun logout(player: Player) {
        joining.remove(player)
        if (player.vars[CHANGES_PENDING_VARP] == 1) {
            applyChanges(player)
        }
        if (player in joined) {
            VarPlayerIntMapSetter.set(player, "varbit.chatchannel_on_logout", 1)
            leave(player, explicit = false)
            player.mes("You have left the channel.", ChatType.FriendsChatNotification)
        }
    }

    public fun disconnected(player: Player) {
        joining.remove(player)
        if (player.vars[CHANGES_PENDING_VARP] == 1) {
            applyChanges(player)
        }
        leave(player, explicit = false)
    }

    public fun leave(player: Player, explicit: Boolean) {
        val channel = joined.remove(player) ?: return
        channel.members.remove(player)
        if (explicit) {
            player.attr.remove(FRIEND_CHAT_LAST)
            player.mes("You have left the channel.", ChatType.FriendsChatNotification)
        }
        player.client.write(UpdateFriendChatChannelFullV2(UpdateFriendChatChannelFullV2.LeaveUpdate))
        if (channel.members.isEmpty()) {
            channels.remove(channel.ownerKey)
            return
        }
        val removed = UpdateFriendChatChannelSingleUser.RemovedFriendChatUser(player.chatName, config.world)
        channel.broadcast(UpdateFriendChatChannelSingleUser(removed))
    }

    public fun message(player: Player, text: String) {
        val channel = joined[player]
        if (channel == null) {
            player.mes("You are not currently in a chat-channel.", ChatType.FriendsChatNotification)
            return
        }
        if (channel.rankOf(player) < channel.talkRank) {
            player.mes(
                "You do not have a high enough rank to talk in this chat-channel.",
                ChatType.FriendsChatNotification,
            )
            return
        }
        val message = text.removePrefix("/").replaceFirstChar(Char::uppercaseChar)
        channel.broadcast(channel.message(player, message))
    }

    public fun kick(player: Player, targetName: String) {
        val channel = joined[player] ?: return
        val rank = channel.rankOf(player)
        if (rank < channel.kickRank) {
            return
        }
        val target = channel.members.firstOrNull { nameKey(it.chatName) == nameKey(targetName) }
        if (target == null || channel.rankOf(target) >= rank) {
            return
        }
        channel.bans.values.removeIf { it <= mapClock.cycle }
        if (channel.bans.size < if (channel.ownerMembers) MAX_BANS else MAX_BANS_FREE) {
            channel.bans[nameKey(target.chatName)] = mapClock.cycle + KICK_BAN_CYCLES
        }
        channel.broadcast(channel.message(player, KICK_NOTIFICATION))
        target.attr.remove(FRIEND_CHAT_LAST)
        target.mes("You have been kicked from the channel.", ChatType.FriendsChatNotification)
        leave(target, explicit = false)
    }

    public fun setRank(owner: Player, friendName: String, rank: Int) {
        val key = nameKey(friendName)
        val ranks = owner.attr[FRIEND_CHAT_RANKS] ?: mutableMapOf()
        if (rank in FriendChatRank.RECRUIT..FriendChatRank.GENERAL) {
            val max = if (owner.members) MAX_RANKED else MAX_RANKED_FREE
            if (key !in ranks && ranks.size >= max) {
                return
            }
            ranks[key] = rank
        } else {
            ranks.remove(key)
        }
        owner.attr[FRIEND_CHAT_RANKS] = ranks
        refreshFriendList(owner)
        changed(owner)
    }

    public fun friendDeleted(owner: Player, friendName: String) {
        owner.attr[FRIEND_CHAT_RANKS]?.remove(nameKey(friendName))
        changed(owner)
    }

    public fun changed(owner: Player) {
        channels[nameKey(owner.chatName)]?.let { loadSocial(owner.characterId, it) }
        if (owner.vars[CHANGES_PENDING_VARP] == 1) {
            return
        }
        VarPlayerIntMapSetter.set(owner, CHANGES_PENDING_VARP, 1)
        owner.softTimer(FRIEND_CHAT_CHANGES_TIMER, CHANGES_DELAY_CYCLES)
        owner.mes(
            "Changes will take effect on your friends channel in the next 60 seconds.",
            ChatType.FriendNotification,
        )
    }

    public fun applyChanges(owner: Player) {
        owner.clearSoftTimer(FRIEND_CHAT_CHANGES_TIMER)
        VarPlayerIntMapSetter.set(owner, CHANGES_PENDING_VARP, 0)
        val channel = channels[nameKey(owner.chatName)] ?: return
        update(channel, ChannelSettings.of(owner))
    }

    private fun loadSocial(characterId: Int, channel: Channel) {
        db.request(
            request = { GameDbResult.Ok(central.socialSnapshot(characterId)) },
            response = { result ->
                val sync = result.fold(onOk = { it }, onErr = { null })
                if (sync is OpenRuneCentralWorldLink.CentralSocialSnapshotResult.Ok) {
                    channel.pendingSocial = sync.snapshot
                }
            },
        )
    }

    private fun update(channel: Channel, settings: ChannelSettings?) {
        if (settings == null) {
            for (member in channel.members.toList()) {
                leave(member, explicit = false)
                member.mes("The channel has been disabled.", ChatType.FriendsChatNotification)
            }
            return
        }
        val oldName = channel.name
        val oldKickRank = channel.kickRank
        val oldRanks = channel.members.associateWith(channel::rankOf)
        channel.pendingSocial?.let(channel::setSocial)
        channel.pendingSocial = null
        channel.apply(settings)
        for (member in channel.members.toList()) {
            val rank = channel.rankOf(member)
            if (rank < FriendChatRank.OWNER && nameKey(member.chatName) in channel.ignores) {
                leave(member, explicit = false)
                member.mes("You have been removed from this channel.", ChatType.FriendsChatNotification)
            } else if (rank < channel.enterRank) {
                leave(member, explicit = false)
                member.mes(
                    "You do not have a high enough rank to be in this chat-channel.",
                    ChatType.FriendsChatNotification,
                )
            }
        }
        if (channel.name != oldName || channel.kickRank != oldKickRank) {
            channel.members.forEach { channel.sendFull(it) }
            return
        }
        for (member in channel.members.filter { oldRanks[it] != channel.rankOf(it) }) {
            channel.members.forEach { channel.sendUserTo(it, member) }
        }
    }

    private fun refreshFriendList(owner: Player) {
        val uid = owner.uid
        val characterId = owner.characterId
        db.request(
            request = { GameDbResult.Ok(central.socialSnapshot(characterId)) },
            response = { result ->
                val current = uid.resolve(playerList) ?: return@request
                result.fold(
                    onOk = { sync ->
                        if (sync is OpenRuneCentralWorldLink.CentralSocialSnapshotResult.Ok) {
                            current.writeCentralSocialSnapshot(sync.snapshot)
                        }
                    },
                    onErr = {},
                )
            },
        )
    }

    private fun load(player: Player, ownerName: String) {
        val uid = player.uid
        val online = playerList.firstOrNull { nameKey(it.chatName) == nameKey(ownerName) }
        val onlineOwner =
            online?.let { OwnerData(it.characterId, it.chatName, it.members, ChannelSettings.of(it)) }
        val schema = player.worldType.key
        db.request(
            request = { connection ->
                val owner = onlineOwner ?: selectOwner(connection, schema, ownerName)
                val snapshot =
                    owner?.let {
                        val result = central.socialSnapshot(it.characterId)
                        (result as? OpenRuneCentralWorldLink.CentralSocialSnapshotResult.Ok)?.snapshot
                    }
                GameDbResult.Ok(owner to snapshot)
            },
            response = { result ->
                val current = uid.resolve(playerList)
                if (current == null) {
                    joining.remove(player)
                    return@request
                }
                joining.remove(current)
                result.fold(
                    onOk = { (owner, snapshot) -> joinLoaded(current, ownerName, owner, snapshot) },
                    onErr = { current.mes("Unable to join the chat-channel right now.", ChatType.FriendsChatNotification) },
                )
            },
        )
    }

    private fun joinLoaded(
        player: Player,
        ownerName: String,
        owner: OwnerData?,
        snapshot: WorldLinkFrameSpecs.CentralSocialSnapshot?,
    ) {
        val settings = owner?.settings
        if (owner == null || settings == null) {
            player.mes("The channel you tried to join does not exist.", ChatType.FriendsChatNotification)
            return
        }
        val key = nameKey(ownerName)
        val channel =
            channels.getOrPut(key) {
                Channel(ownerKey = key, ownerName = owner.displayName, ownerMembers = owner.members).also {
                    snapshot?.let(it::setSocial)
                    it.apply(settings)
                }
            }
        tryJoin(player, channel)
    }

    private fun tryJoin(player: Player, channel: Channel) {
        val key = nameKey(player.chatName)
        val banExpiry = channel.bans[key]
        if (banExpiry != null && banExpiry > mapClock.cycle) {
            player.mes("You are temporarily banned from this chat-channel.", ChatType.FriendsChatNotification)
            channel.removeIfEmpty()
            return
        }
        val rank = channel.rankOf(player)
        if (rank < FriendChatRank.OWNER && key in channel.ignores) {
            player.mes("You are not allowed to join this user's chat-channel.", ChatType.FriendsChatNotification)
            channel.removeIfEmpty()
            return
        }
        if (rank < channel.enterRank) {
            player.mes(
                "You do not have a high enough rank to join this chat-channel.",
                ChatType.FriendsChatNotification,
            )
            channel.removeIfEmpty()
            return
        }
        if (channel.members.size >= CAPACITY) {
            val bumped = channel.members.filter { channel.rankOf(it) < rank }.minByOrNull(channel::rankOf)
            if (bumped == null) {
                player.mes("The channel is full.", ChatType.FriendsChatNotification)
                return
            }
            leave(bumped, explicit = false)
            bumped.mes("You have been removed from this channel.", ChatType.FriendsChatNotification)
        }
        channel.members += player
        joined[player] = channel
        player.attr[FRIEND_CHAT_LAST] = channel.ownerName
        channel.sendFull(player)
        channel.members.filter { it !== player }.forEach { channel.sendUserTo(it, player) }
        player.mes("Now talking in chat-channel ${channel.name}", ChatType.FriendsChatNotification)
        player.mes("To talk, start each line of chat with the / symbol.", ChatType.FriendsChatNotification)
    }

    private fun selectOwner(
        connection: org.rsmod.api.db.DatabaseConnection,
        schema: String,
        ownerName: String,
    ): OwnerData? {
        val sql =
            """
            SELECT c.id, c.display_name, c.members, a.attr, a.value_json
            FROM account_characters c
            LEFT JOIN "$schema".character_attrs a ON a.character_id = c.id AND a.attr LIKE 'friend_chat_%'
            WHERE LOWER(TRIM(c.display_name)) = LOWER(TRIM(?))
            """
                .trimIndent()
        val owner =
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, ownerName)
                ps.executeQuery().use { rs ->
                    var id = 0
                    var displayName = ""
                    var members = false
                    val attrs = HashMap<String, Any>()
                    while (rs.next()) {
                        id = rs.getInt("id")
                        displayName = rs.getString("display_name")
                        members = rs.getBoolean("members")
                        val attr = rs.getString("attr") ?: continue
                        attrs[attr] = objectMapper.readValue(rs.getString("value_json"), object : TypeReference<Any>() {})
                    }
                    if (id <= 0) null else Triple(id, displayName, members) to attrs
                }
            } ?: return null
        val (id, displayName, members) = owner.first
        val vars = VarPlayerIntMap()
        val setupSql = """SELECT value FROM "$schema".character_varps WHERE character_id = ? AND varp = ?"""
        connection.prepareStatement(setupSql).use { ps ->
            ps.setInt(1, id)
            ps.setString(2, SETUP_VARP)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    vars.backing[SETUP_VARP.asRSCM(RSCMType.VARP)] = rs.getInt("value")
                }
            }
        }
        return OwnerData(id, displayName, members, ChannelSettings.of(owner.second, vars))
    }

    private fun Channel.removeIfEmpty() {
        if (members.isEmpty()) {
            channels.remove(ownerKey)
        }
    }

    private fun Channel.broadcast(message: net.rsprot.protocol.message.OutgoingGameMessage) {
        members.forEach { it.client.write(message) }
    }

    private fun Channel.message(sender: Player, text: String): MessageFriendChannel =
        MessageFriendChannel(
            sender = sender.chatName,
            channelName = name,
            worldId = config.world,
            worldMessageCounter = nextPrivateMessageCounter(config.world),
            chatCrownType = sender.modLevel.clientCode,
            message = text,
        )

    private fun Channel.entry(member: Player): FriendChatEntry =
        FriendChatEntry(member.chatName, config.world, rankOf(member), formatWorldName(config.world, config))

    private fun Channel.sendFull(member: Player) {
        val entries = members.map { entry(it) }
        val update = UpdateFriendChatChannelFullV2.JoinUpdate(ownerName, name, kickRank, entries)
        member.client.write(UpdateFriendChatChannelFullV2(update))
    }

    private fun Channel.sendUserTo(receiver: Player, member: Player) {
        val added =
            UpdateFriendChatChannelSingleUser.AddedFriendChatUser(
                member.chatName,
                config.world,
                rankOf(member),
                formatWorldName(config.world, config),
            )
        receiver.client.write(UpdateFriendChatChannelSingleUser(added))
    }

    private class OwnerData(
        val characterId: Int,
        val displayName: String,
        val members: Boolean,
        val settings: ChannelSettings?,
    )

    private class ChannelSettings(
        val name: String,
        val enterRank: Int,
        val talkRank: Int,
        val kickRank: Int,
        val ranks: Map<String, Int>,
    ) {
        companion object {
            fun of(owner: Player): ChannelSettings? =
                of(owner.attr[FRIEND_CHAT_NAME], owner.attr[FRIEND_CHAT_RANKS]?.toMap(), owner.vars)

            @Suppress("UNCHECKED_CAST")
            fun of(attrs: Map<String, Any>, vars: VarPlayerIntMap): ChannelSettings? =
                of(
                    attrs[FRIEND_CHAT_NAME.persistenceKey] as? String,
                    attrs[FRIEND_CHAT_RANKS.persistenceKey] as? Map<String, Int>,
                    vars,
                )

            private fun of(name: String?, ranks: Map<String, Int>?, vars: VarPlayerIntMap): ChannelSettings? {
                name ?: return null
                return ChannelSettings(
                    name = name,
                    enterRank = friendChatOptionRank(vars[FRIEND_CHAT_ENTER_OPTION]),
                    talkRank = friendChatOptionRank(vars[FRIEND_CHAT_TALK_OPTION]),
                    kickRank = friendChatOptionRank(vars.friendChatKickOption()),
                    ranks = ranks ?: emptyMap(),
                )
            }
        }
    }

    private class Channel(val ownerKey: String, val ownerName: String, val ownerMembers: Boolean) {
        var name: String = ""
        var enterRank: Int = FriendChatRank.GUEST
        var talkRank: Int = FriendChatRank.GUEST
        var kickRank: Int = FriendChatRank.OWNER
        var ranks: Map<String, Int> = emptyMap()
        var friends: Set<String> = emptySet()
        var ignores: Set<String> = emptySet()
        var pendingSocial: WorldLinkFrameSpecs.CentralSocialSnapshot? = null
        val members = LinkedHashSet<Player>()
        val bans = HashMap<String, Int>()

        fun setSocial(snapshot: WorldLinkFrameSpecs.CentralSocialSnapshot) {
            friends = snapshot.friends.mapTo(HashSet()) { nameKey(it.displayName) }
            ignores = snapshot.ignores.mapTo(HashSet()) { nameKey(it.displayName) }
        }

        fun apply(settings: ChannelSettings) {
            name = settings.name
            enterRank = settings.enterRank
            talkRank = settings.talkRank
            kickRank = settings.kickRank
            ranks = settings.ranks
        }

        fun rankOf(player: Player): Int {
            val key = nameKey(player.chatName)
            return when {
                player.modLevel.isAtLeast(Rights.ADMINISTRATOR) -> FriendChatRank.JMOD
                key == ownerKey -> FriendChatRank.OWNER
                key !in friends -> FriendChatRank.GUEST
                else -> ranks[key] ?: FriendChatRank.FRIEND
            }
        }
    }
}

private val Player.chatName: String
    get() = displayName.ifBlank { username }
