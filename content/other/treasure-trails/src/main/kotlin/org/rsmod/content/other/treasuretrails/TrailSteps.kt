package org.rsmod.content.other.treasuretrails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.dbRow
import dev.openrune.types.dbcol.list
import org.rsmod.api.table.MusicRow
import org.rsmod.api.table.TrailCharlieTasksRow
import org.rsmod.api.table.TrailEmoteSeqsRow
import org.rsmod.api.table.TrailMapInterfacesRow
import org.rsmod.api.table.cluehelper.CluehelperChallengeQuestionRow
import org.rsmod.api.table.cluehelper.CluehelperClueAnagramRow
import org.rsmod.api.table.cluehelper.CluehelperClueCipherRow
import org.rsmod.api.table.cluehelper.CluehelperClueCoordinateRow
import org.rsmod.api.table.cluehelper.CluehelperClueCrypticRow
import org.rsmod.api.table.cluehelper.CluehelperClueEmoteRow
import org.rsmod.api.table.cluehelper.CluehelperClueHotcoldRow
import org.rsmod.api.table.cluehelper.CluehelperClueMapRow
import org.rsmod.api.table.cluehelper.CluehelperClueMusicRow
import org.rsmod.api.table.cluehelper.CluehelperClueSkillchallengeRow
import org.rsmod.api.table.cluehelper.CluehelperOutfitRow
import org.rsmod.api.table.cluehelper.CluehelperTargetCoordRow
import org.rsmod.api.table.cluehelper.CluehelperTargetKeyRow
import org.rsmod.api.table.cluehelper.CluehelperTargetLocRow
import org.rsmod.api.table.cluehelper.CluehelperTargetNpcRow
import org.rsmod.game.interact.InteractionOp
import org.rsmod.map.CoordGrid

/** One clue step, built from a row of Jagex's `cluehelper_clue_*` tables. */
sealed class TrailStep(val row: Int, val tier: TrailTier, val text: String?) {
    /** Talk to one of [npcs] (anagrams, ciphers, npc cryptics); from medium up some ask [challenge]. */
    class Talk(row: Int, tier: TrailTier, text: String, val npcs: Set<Int>, val challenge: Challenge? = null) :
        TrailStep(row, tier, text)

    /** "Talk to Charlie the Tramp": he picks one of [tasks] and wants its item. */
    class Charlie(row: Int, tier: TrailTier, text: String, val npcs: Set<Int>, val tasks: List<CharlieTask>) :
        TrailStep(row, tier, text)

    /** Reldo hands out the strange device; the dig spot is one of [spots]. */
    class HotCold(row: Int, tier: TrailTier, text: String, val npcs: Set<Int>, val spots: List<HotColdSpot>) :
        TrailStep(row, tier, text)

    /**
     * Search one of [locs] (cryptics such as "search the crates", map clues that show a crate). Key
     * steps need [key] in the inventory, which one of [keyNpcs] drops when killed.
     */
    class Search(
        row: Int,
        tier: TrailTier,
        text: String?,
        val locs: List<Pair<Int, CoordGrid>>,
        val map: String? = null,
        val key: Int? = null,
        val keyNpcs: Set<Int> = emptySet(),
    ) : TrailStep(row, tier, text)

    /** Dig within [radius] of [coord]; map clues open [map]. */
    class Dig(row: Int, tier: TrailTier, text: String?, val coord: CoordGrid, val radius: Int, val map: String?) :
        TrailStep(row, tier, text)

    /** Perform [seq] near [coord] in one of [outfits]; Uri then comes ([seq2]: then do that one too). */
    class Emote(
        row: Int,
        tier: TrailTier,
        text: String,
        val seq: Int,
        val seq2: Int?,
        val coord: CoordGrid,
        val outfits: List<CluehelperOutfitRow>,
        val stashLoc: Int?,
    ) : TrailStep(row, tier, text)

    /** Play [music] and talk to one of [npcs] (Cecilia). */
    class Music(row: Int, tier: TrailTier, text: String, val npcs: Set<Int>, val music: MusicRow) :
        TrailStep(row, tier, text)

    fun covers(coords: CoordGrid, centre: CoordGrid, radius: Int): Boolean =
        coords.level == centre.level && coords.chebyshevDistance(centre) <= radius
}

/** A challenge scroll question ([row] in `cluehelper_challenge_question`) and its scroll [obj]. */
data class Challenge(val row: Int, val question: String, val answer: Int, val obj: Int)

data class CharlieTask(val row: Int, val text: String, val obj: Int, val request: String)

data class HotColdSpot(val row: Int, val coord: CoordGrid)

object TrailSteps {
    const val EMOTE_RADIUS = 5
    const val MAP_RADIUS = 1
    const val HOT_COLD_RADIUS = 4
    const val MUSIC_TEXT_START = "I'd like to hear some music.<br>Come and see me on the bridge in<br>Falador Park, and play:<br>"
    const val MUSIC_TEXT_END = "<br>Thanks, Cecilia"
    private const val TARGET_NPC_TABLE = "dbtable.cluehelper_target_npc"
    private const val TARGET_LOC_TABLE = "dbtable.cluehelper_target_loc"
    private const val TARGET_COORD_TABLE = "dbtable.cluehelper_target_coord"
    private const val TARGET_KEY_TABLE = "dbtable.cluehelper_target_key"
    private const val CHALLENGE_BOX_TABLE = "dbtable.cluehelper_challenge_box"
    private const val CHARLIE = "npc.tramppg"
    private const val RELDO = "npc.reldo"

    val ALL: Map<TrailTier, List<TrailStep>> by lazy {
        TrailTier.entries.associateWith { tier -> build(tier) }
    }

    fun byRow(tier: TrailTier, row: Int): TrailStep? = ALL.getValue(tier).firstOrNull { it.row == row }

    private fun build(tier: TrailTier): List<TrailStep> {
        val d = tier.difficulty
        val steps = mutableListOf<TrailStep>()
        for (r in CluehelperClueAnagramRow.all().filter { it.difficulty == d }) {
            if (r.challenge?.tableId == CHALLENGE_BOX_TABLE.asRSCM(RSCMType.DBTABLE)) continue
            if (!targetsOnly(r.rowId, "dbcol.cluehelper_clue_anagram:target", TARGET_NPC_TABLE)) continue
            val challenge = r.challenge?.let { challenge(it.rowId, r.rowId) }
            val text = "The anagram reveals<br>who to speak to next:<br>${r.clueText.uppercase()}"
            steps += TrailStep.Talk(r.rowId, tier, text, r.target.map { it.npc.id }.toSet(), challenge)
        }
        for (r in CluehelperClueCipherRow.all().filter { it.difficulty == d }) {
            val challenge = r.challenge?.let { challenge(it.rowId, r.rowId) } ?: continue
            val text = "The cipher reveals<br>who to speak to next:<br>${r.clueText}"
            steps += TrailStep.Talk(r.rowId, tier, text, r.target.map { it.npc.id }.toSet(), challenge)
        }
        for (r in CluehelperClueCoordinateRow.all().filter { it.difficulty == d }) {
            val text = coordinateText(r.clueText) ?: continue
            steps += TrailStep.Dig(r.rowId, tier, text, r.target.coord, MAP_RADIUS, null)
        }
        for (r in CluehelperClueCrypticRow.all().filter { it.difficulty == d }) {
            steps += cryptic(tier, r) ?: continue
        }
        for (r in CluehelperClueMapRow.all().filter { it.difficulty == d }) {
            val map = TrailMapInterfacesRow.all().firstOrNull { it.clue == r.rowId }?.map ?: continue
            val target = DbHelper.row(r.rowId).dbRow("dbcol.cluehelper_clue_map:target").id
            if (DbHelper.row(target).tableId == TARGET_LOC_TABLE.asRSCM(RSCMType.DBTABLE)) {
                val loc = CluehelperTargetLocRow.getRow(target)
                val searchable = listOf(loc.loc.id to loc.coord).mapNotNull { (l, c) -> searchTarget(l)?.let { it to c } }
                if (searchable.isNotEmpty()) steps += TrailStep.Search(r.rowId, tier, null, searchable, map)
                continue
            }
            val coord = CluehelperTargetCoordRow.getRow(target)
            steps += TrailStep.Dig(r.rowId, tier, coord.description, coord.coord, MAP_RADIUS, map)
        }
        for (r in CluehelperClueEmoteRow.all().filter { it.difficulty == d && it.emote.size in 1..2 }) {
            val seqs = r.emote.map { slot -> TrailEmoteSeqsRow.all().firstOrNull { it.slot == slot }?.seq }
            if (seqs.any { it == null }) continue
            val seq = seqs[0]!!
            steps +=
                TrailStep.Emote(
                    r.rowId,
                    tier,
                    r.clueText,
                    seq.asRSCM(RSCMType.SEQ),
                    seqs.getOrNull(1)?.asRSCM(RSCMType.SEQ),
                    r.target.coord,
                    r.outfit,
                    r.hideyHoleLoc?.id,
                )
        }
        for (r in CluehelperClueMusicRow.all().filter { it.difficulty == d }) {
            val text = "$MUSIC_TEXT_START<col=ffffff>${r.music.displayname}</col>$MUSIC_TEXT_END"
            steps += TrailStep.Music(r.rowId, tier, text, setOf(r.target.npc.id), r.music)
        }
        val objs = STEP_OBJS.getValue(tier)
        return if (tier.clueObj == null) steps.filter { it.row in objs } else steps
    }

    private fun targetsOnly(row: Int, column: String, table: String): Boolean =
        DbHelper.row(row).list(column, DbColumnCodec.DbRowTypeCodec).all {
            DbHelper.row(it.id).tableId == table.asRSCM(RSCMType.DBTABLE)
        }

    private fun cryptic(tier: TrailTier, r: CluehelperClueCrypticRow): TrailStep? {
        val targets = DbHelper.row(r.rowId).list("dbcol.cluehelper_clue_cryptic:target", DbColumnCodec.DbRowTypeCodec)
        val tables = targets.map { DbHelper.row(it.id).tableId }.toSet()
        if (tables.size != 1) return null
        val table = tables.single()
        return when (table) {
            TARGET_NPC_TABLE.asRSCM(RSCMType.DBTABLE) -> {
                val npcs = targets.map { CluehelperTargetNpcRow.getRow(it.id) }
                val ids = npcs.flatMap { listOfNotNull(it.npc.id, it.fallbackNpc?.id) }.toSet()
                when {
                    CHARLIE.asRSCM(RSCMType.NPC) in ids ->
                        TrailStep.Charlie(r.rowId, tier, r.clueText, ids, charlieTasks(tier))
                    RELDO.asRSCM(RSCMType.NPC) in ids ->
                        TrailStep.HotCold(r.rowId, tier, r.clueText, ids, hotColdSpots(tier))
                    else -> TrailStep.Talk(r.rowId, tier, r.clueText, ids, r.challenge?.let { challenge(it.rowId, r.rowId) })
                }
            }
            TARGET_LOC_TABLE.asRSCM(RSCMType.DBTABLE) -> {
                val locs =
                    targets.flatMap {
                        val loc = CluehelperTargetLocRow.getRow(it.id)
                        listOfNotNull(loc.loc.id to loc.coord, loc.fallbackLoc?.let { f -> f.id to loc.coord })
                    }
                val searchable = locs.mapNotNull { (loc, coord) -> searchTarget(loc)?.let { it to coord } }.distinct()
                if (searchable.isEmpty()) return null
                TrailStep.Search(r.rowId, tier, r.clueText, searchable)
            }
            TARGET_KEY_TABLE.asRSCM(RSCMType.DBTABLE) -> {
                val key = CluehelperTargetKeyRow.getRow(targets.single().id)
                val target = searchTarget(key.loc.id) ?: return null
                TrailStep.Search(
                    r.rowId,
                    tier,
                    r.clueText,
                    listOf(target to key.locCoord),
                    key = key.key.id,
                    keyNpcs = key.npcs.map { it.id }.toSet(),
                )
            }
            TARGET_COORD_TABLE.asRSCM(RSCMType.DBTABLE) -> {
                val coord = CluehelperTargetCoordRow.getRow(targets.first().id).coord
                TrailStep.Dig(r.rowId, tier, r.clueText, coord, MAP_RADIUS, null)
            }
            else -> null
        }
    }

    /** `00.05S 01.13E` (Jagex's short form) -> the scroll text the Clue Scroll plugin parses. */
    fun coordinateText(short: String): String? {
        val m = Regex("""(\d+)\.(\d+)([NS]) (\d+)\.(\d+)([EW])""").matchEntire(short.trim()) ?: return null
        val (degY, minY, ns, degX, minX, ew) = m.destructured
        val lat = if (ns == "N") "north" else "south"
        val lon = if (ew == "E") "east" else "west"
        return "$degY degrees $minY minutes $lat<br>$degX degrees $minX minutes $lon"
    }

    /** The challenge scroll obj names the clue row whose npc hands it out (param 623). */
    private fun challenge(row: Int, clueRow: Int): Challenge? {
        val question = CluehelperChallengeQuestionRow.getRow(row).question.firstOrNull() ?: return null
        val obj = CHALLENGE_OBJS[clueRow] ?: return null
        return Challenge(row, question.t0, question.t1, obj)
    }

    /** Challenge scroll objs by the clue row they belong to (item param 623). */
    private val CHALLENGE_OBJS: Map<Int, Int> by lazy {
        ServerCacheManager.getItems()
            .values
            .filter { it.name.startsWith("Challenge scroll") }
            .sortedBy { it.id }
            .mapNotNull { item -> (item.paramsRaw?.get(CLUE_ROW_PARAM) as? Int)?.let { it to item.id } }
            .distinctBy { it.first }
            .toMap()
    }

    fun searchOp(loc: Int): InteractionOp? = op(loc, "Search")

    /** The op that completes a search step on [loc]: Search, else Open (the Edgeville coffin). */
    fun stepOp(loc: Int): InteractionOp? = searchOp(loc) ?: op(loc, "Open")

    /**
     * The loc a search step is done on: [loc] itself if it has Search, else its open variant
     * (closed drawers, chests and wardrobes are opened first; the variant is named `<loc>open`,
     * `<loc>_open` or with `closed` -> `open`), else [loc] if it only opens.
     */
    fun searchTarget(loc: Int): Int? {
        if (searchOp(loc) != null) return loc
        val name = dev.openrune.rscm.RSCM.getReverseMapping(RSCMType.LOC, loc)
        val opened =
            listOf("${name}open", "${name}_open", name.replace("closed", "open"))
                .filter { it != name }
                .mapNotNull { runCatching { it.asRSCM(RSCMType.LOC) }.getOrNull() }
                .firstOrNull { searchOp(it) != null }
        return opened ?: loc.takeIf { op(it, "Open") != null }
    }

    private fun op(loc: Int, name: String): InteractionOp? {
        val type = ServerCacheManager.getObject(loc) ?: return null
        return InteractionOp.entries.firstOrNull { type.actions.getOpOrNull(it.slot - 1) == name }
    }

    private fun charlieTasks(tier: TrailTier): List<CharlieTask> =
        CluehelperClueSkillchallengeRow.all()
            .filter { it.difficulty == tier.difficulty }
            .mapNotNull { r ->
                val task = TrailCharlieTasksRow.all().firstOrNull { it.challenge == r.rowId } ?: return@mapNotNull null
                CharlieTask(r.rowId, r.clueText, task.obj.id, task.request)
            }

    private fun hotColdSpots(tier: TrailTier): List<HotColdSpot> =
        CluehelperClueHotcoldRow.all()
            .filter { it.difficulty == tier.difficulty }
            .map { HotColdSpot(it.rowId, it.target.coord) }

    /** The clue scroll obj that shows [step]: one item per step from easy up (param 623). */
    fun clueObj(step: TrailStep): Int =
        step.tier.clueObj?.asRSCM(RSCMType.OBJ) ?: STEP_OBJS.getValue(step.tier).getValue(step.row)

    val STEP_OBJS: Map<TrailTier, Map<Int, Int>> by lazy {
        TrailTier.entries.associateWith { tier ->
            if (tier.clueObj != null) return@associateWith emptyMap()
            ServerCacheManager.getItems()
                .values
                .filter { it.name == tier.clueName }
                .sortedBy { it.id }
                .mapNotNull { item -> (item.paramsRaw?.get(CLUE_ROW_PARAM) as? Int)?.let { it to item.id } }
                .distinctBy { it.first }
                .toMap()
        }
    }

    const val CLUE_ROW_PARAM = 623
}
