package org.rsmod.content.other.treasuretrails.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * One roll of the beginner reward casket (OSRS Wiki: Reward casket (beginner)), flattened to a
 * single weighted table out of [TOTAL_WEIGHT] = 44,280: each generic item is 11/492, each black
 * item 11/8,856, each unique 1/360 and noted cabbages 1/24. A casket makes 1-3 rolls.
 */
object BeginnerCasketTable {
    const val OBJ = 0
    const val MIN = 1
    const val MAX = 2
    const val WEIGHT = 3
    const val NOTED = 4

    const val TOTAL_WEIGHT = 44280

    private const val GENERIC = 990
    private const val BLACK = 55
    private const val UNIQUE = 123
    private const val CABBAGE = 1845

    private data class Reward(
        val obj: String,
        val weight: Int,
        val min: Int = 1,
        val max: Int = min,
        val noted: Boolean = false,
    )

    private val UNIQUES =
        listOf(
            "mole_slippers",
            "frog_slippers",
            "bear_slippers",
            "demon_slippers",
            "jester_cape",
            "shoulder_parrot",
            "monk_robetop_t",
            "monk_robebottom_t",
            "amulet_of_defence_t",
            "sandwich_lady_hat",
            "sandwich_lady_top",
            "sandwich_lady_bottom",
            "rune_scimitar_ornament_kit_guthix",
            "rune_scimitar_ornament_kit_saradomin",
            "rune_scimitar_ornament_kit_zamorak",
        )

    private val BLACK_ITEMS =
        listOf(
            "black_2h_sword",
            "black_axe",
            "black_battleaxe",
            "black_chainbody",
            "black_dagger",
            "black_full_helm",
            "black_kiteshield",
            "black_longsword",
            "black_mace",
            "black_med_helm",
            "black_pickaxe",
            "black_platebody",
            "black_plateskirt",
            "black_platelegs",
            "black_sq_shield",
            "black_scimitar",
            "black_sword",
            "black_warhammer",
        )

    private val EQUIPMENT =
        listOf(
            "shortbow",
            "longbow",
            "oak_shortbow",
            "oak_longbow",
            "iron_pickaxe",
            "staff_of_air",
            "staff_of_water",
            "staff_of_earth",
            "staff_of_fire",
            "steel_full_helm",
            "steel_platebody",
            "steel_platelegs",
            "steel_plateskirt",
            "steel_longsword",
            "steel_dagger",
            "steel_axe",
            "steel_battleaxe",
            "leather_cowl",
            "leather_armour",
            "leather_chaps",
            "leather_vambraces",
            "hardleather_body",
            "bluewizhat",
            "wizards_robe",
            "blackwizhat",
            "black_robe",
        )

    private val REWARDS =
        UNIQUES.map { Reward(it, UNIQUE) } +
            BLACK_ITEMS.map { Reward(it, BLACK) } +
            EQUIPMENT.map { Reward(it, GENERIC) } +
            listOf("airrune", "mindrune", "waterrune", "earthrune", "firerune", "bodyrune").map {
                Reward(it, GENERIC, 15, 35)
            } +
            listOf("chaosrune", "naturerune", "lawrune").map { Reward(it, GENERIC, 2, 7) } +
            listOf(
                Reward("bronze_arrow", GENERIC, 15, 30),
                Reward("iron_arrow", GENERIC, 7, 15),
                Reward("cabbage", CABBAGE, 5, 9, noted = true),
                Reward("shrimp", GENERIC, 5, 14, noted = true),
                Reward("sardine", GENERIC, 5, 12, noted = true),
                Reward("herring", GENERIC, 5, 9, noted = true),
            )

    fun rewards() =
        dbTable("dbtable.trail_beginner_casket", serverOnly = true) {
            column("obj", OBJ, VarType.OBJ)
            column("min", MIN, VarType.INT)
            column("max", MAX, VarType.INT)
            column("weight", WEIGHT, VarType.INT)
            column("noted", NOTED, VarType.BOOLEAN)

            check(REWARDS.sumOf { it.weight } == TOTAL_WEIGHT)
            for (r in REWARDS) {
                row("dbrow.trail_beginner_casket_${r.obj}") {
                    columnRSCM(OBJ, "obj.${r.obj}")
                    column(MIN, r.min)
                    column(MAX, r.max)
                    column(WEIGHT, r.weight)
                    column(NOTED, r.noted)
                }
            }
        }
}
