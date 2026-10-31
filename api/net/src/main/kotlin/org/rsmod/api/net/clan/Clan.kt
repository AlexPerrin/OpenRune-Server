package org.rsmod.api.net.clan

import org.rsmod.game.entity.Player

public class ClanMember(
    public val characterId: Int,
    public var name: String,
    public var rank: Int,
    public val joinRuneDay: Int,
    public val joinedAt: Long,
    public var lastSeenDay: Int = 0,
)

public class Clan(
    public val id: Int,
    public var name: String,
    public val createdMinute: Int,
    public var resignAt: Long,
) {
    public val members: MutableList<ClanMember> = mutableListOf()
    public val intSettings: MutableMap<Int, Int> = HashMap()
    public val stringSettings: MutableMap<Int, String> = HashMap()
    public val channel: MutableList<Player> = mutableListOf()
    public val online: MutableSet<Player> = LinkedHashSet()
    public var settingsUpdateNum: Int = 0
    public var channelUpdateNum: Long = System.currentTimeMillis()

    public val owner: ClanMember?
        get() = members.firstOrNull { it.rank == ClanRank.OWNER }

    public fun memberOf(player: Player): ClanMember? =
        members.firstOrNull { it.characterId == player.characterId }

    public fun rankOf(player: Player): Int = memberOf(player)?.rank ?: ClanRank.GUEST

    public fun talkRank(): Int = intSettings[TALK_RANK_KEY] ?: DEFAULT_TALK_RANK

    public fun kickRank(): Int = intSettings[KICK_RANK_KEY] ?: DEFAULT_KICK_RANK

    public fun allowUnaffined(): Boolean = (intSettings[ALLOW_UNAFFINED_KEY] ?: 1) == 1

    internal companion object {
        const val TALK_RANK_KEY = -1
        const val KICK_RANK_KEY = -2
        const val ALLOW_UNAFFINED_KEY = -3
        const val DEFAULT_TALK_RANK = ClanRank.GUEST
        const val DEFAULT_KICK_RANK = ClanRank.ADMINISTRATOR
    }
}

public object ClanRank {
    public const val GUEST: Int = -1
    public const val MEMBER: Int = 0
    public const val ADMINISTRATOR: Int = 100
    public const val DEPUTY_OWNER: Int = 125
    public const val OWNER: Int = 126
    public const val JMOD: Int = 127
}

internal fun nameKey(name: String): String = name.trim().lowercase().replace('_', ' ')
