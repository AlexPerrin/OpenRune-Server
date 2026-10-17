package org.rsmod.content.other.treasuretrails

/**
 * The clue tiers this module runs. [difficulty] is the `difficulty` column of the `cluehelper_*`
 * tables; [clueObj] is the single scroll obj of tiers whose steps share one, otherwise each step has
 * its own obj named [clueName]. Trail state lives in the tier's server varps.
 */
enum class TrailTier(
    val difficulty: Int,
    val clueObj: String?,
    val clueName: String,
    val dropObj: String,
    val casket: String,
    val scrollBox: String,
    val lengths: List<Int>,
    val rolls: IntRange,
    val stashLevel: Int,
    val varpPrefix: String,
    val uri: String,
) {
    Beginner(
        difficulty = 0,
        clueObj = "obj.trail_clue_beginner",
        clueName = "Clue scroll (beginner)",
        dropObj = "obj.trail_clue_beginner",
        casket = "obj.trail_reward_casket_beginner",
        scrollBox = "obj.league_clue_box_beginner",
        lengths = List(2) { 1 } + List(9) { 2 } + List(9) { 3 },
        rolls = 1..3,
        stashLevel = 12,
        varpPrefix = "varp.trail_beginner",
        uri = "npc.trail_beginner_uri",
    ),
    Easy(
        difficulty = 1,
        clueObj = null,
        clueName = "Clue scroll (easy)",
        dropObj = "obj.trail_clue_easy_simple001",
        casket = "obj.trail_reward_casket_easy",
        scrollBox = "obj.league_clue_box_easy",
        lengths = listOf(2, 3, 4),
        rolls = 2..4,
        stashLevel = 27,
        varpPrefix = "varp.trail_easy",
        uri = "npc.trail_easy_uri",
    );

    val stepVarp: String get() = "${varpPrefix}_step"
    val stepsLeftVarp: String get() = "${varpPrefix}_steps_left"
    val stepsDoneVarp: String get() = "${varpPrefix}_steps_done"
    val completedVarp: String get() = "${varpPrefix}_completed"
    val auxVarp: String get() = "${varpPrefix}_aux"
    val stashFilledVarp: String get() = "varp.trail_stash_${name.lowercase()}_filled"
    val label: String get() = name.lowercase()
}
