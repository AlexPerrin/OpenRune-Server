package org.rsmod.content.interfaces.clans

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.rsmod.api.net.clan.Clan
import org.rsmod.api.net.clan.ClanRank

private const val TITLE_BITS = 10
private val CREATION_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.UK)

internal const val HIGHLIGHT = "<col=ffb83f>"
internal const val END = "</col>"

internal val RANK_TITLE_FIELDS: Map<Int, Pair<Int, Int>> =
    linkedMapOf(
        0 to (1 to 0),
        10 to (1 to 10),
        20 to (1 to 20),
        30 to (2 to 0),
        40 to (2 to 10),
        50 to (2 to 20),
        60 to (3 to 0),
        70 to (3 to 10),
        80 to (3 to 20),
        90 to (4 to 0),
        105 to (4 to 10),
        110 to (4 to 20),
        115 to (5 to 0),
        120 to (5 to 10),
        95 to (5 to 20),
        96 to (102 to 0),
        97 to (102 to 10),
        98 to (102 to 20),
        99 to (103 to 0),
        101 to (103 to 10),
        102 to (103 to 20),
        103 to (104 to 0),
        104 to (104 to 10),
        124 to (104 to 20),
    )

private val FIXED_TITLES =
    mapOf(
        ClanRank.GUEST to -1,
        ClanRank.ADMINISTRATOR to -2,
        ClanRank.DEPUTY_OWNER to -3,
        ClanRank.OWNER to -4,
        ClanRank.JMOD to -5,
        128 to -6,
    )

internal fun cacheEnum(id: Int): Map<Int, Any> = ServerCacheManager.getEnum(id)?.values?.toSortedMap() ?: emptyMap()

internal fun cacheEnum(name: String): Map<Int, Any> = cacheEnum(name.asRSCM(RSCMType.ENUM))

internal fun clanRanks(): List<Int> = cacheEnum("enum.clan_ranks").values.map { it as Int }

internal fun lockedRank(rank: Int): Boolean =
    cacheEnum("enum.clan_ranks_locked")[clanRanks().indexOf(rank)].let { it == true || it == 1 }

internal fun Clan.titleOf(rank: Int): Int {
    FIXED_TITLES[rank]?.let {
        return it
    }
    val (setting, start) = RANK_TITLE_FIELDS[rank] ?: return 0
    return ((intSettings[setting] ?: 0) ushr start) and ((1 shl TITLE_BITS) - 1)
}

internal fun Clan.withTitle(titles: Map<Int, Int>): Map<Int, Int> {
    val settings = HashMap<Int, Int>()
    for ((rank, field) in RANK_TITLE_FIELDS) {
        val (setting, start) = field
        val current = settings[setting] ?: intSettings[setting] ?: 0
        val mask = ((1 shl TITLE_BITS) - 1) shl start
        settings[setting] = (current and mask.inv()) or ((titles[rank] ?: 0) shl start)
    }
    return settings
}

internal fun Clan.rankName(rank: Int): String {
    val title = cacheEnum("enum.clan_rank_titles")[titleOf(rank)] as? String
    if (!title.isNullOrEmpty()) {
        return title
    }
    return if (rank > 0) "Rank $rank" else "Not ranked"
}

internal fun interestsText(bits: Int): String {
    val names =
        cacheEnum("enum.clan_interest_names").filterKeys { bits and (1 shl it) != 0 }
    if (names.isEmpty()) {
        return "${HIGHLIGHT}None$END"
    }
    val items = names.values.map { "$HIGHLIGHT$it$END" }
    return if (items.size == 1) "${items[0]}." else items.dropLast(1).joinToString(", ") + " and ${items.last()}."
}

internal fun creationDate(minute: Int): String =
    CREATION_DATE.format(Instant.ofEpochSecond(minute * 60L).atZone(ZoneOffset.UTC))

internal class GuestRequirement(private val start: Int, private val end: Int) {
    val max: Int = (1 shl (end - start + 1)) - 1

    fun of(requirements: Int): Int = (requirements ushr start) and max

    fun with(requirements: Int, value: Int): Int =
        (requirements and (max shl start).inv()) or ((value and max) shl start)
}

internal val GUEST_COMBAT = GuestRequirement(0, 6)
internal val GUEST_TOTAL = GuestRequirement(7, 18)
internal val GUEST_QP = GuestRequirement(19, 29)
internal val GUEST_IRON = GuestRequirement(30, 31)
internal const val IRON_ANY = 1
internal const val IRON_ULTIMATE = 2
internal const val IRON_HARDCORE = 3

internal val GUEST_REQUIREMENTS: Map<Int, GuestRequirement> =
    mapOf(2958 to GUEST_COMBAT, 2959 to GUEST_TOTAL, 2960 to GUEST_QP, 2961 to GUEST_IRON)
