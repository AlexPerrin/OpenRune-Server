package org.rsmod.content.skills.farming.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * Allotment, flower and herb crops, from the OSRS Wiki (Allotment patch, Flower patch, Herb patch,
 * and each seed's yield calculator for the chance-to-save constants).
 *
 * [PLANT_VALUE] is the patch's `farming_transmit` value right after planting; each growth cycle adds
 * one until [STAGES] cycles have passed, and the crop then shows [HARVEST_VALUE]. These are the
 * values RuneLite's Time Tracking decodes (PatchImplementation). XP is in tenths. [CTS_LOW] and
 * [CTS_HIGH] are the wiki's chance-to-save constants (out of 256, at level 1 and 99) for crops that
 * use harvest lives; flowers give [FIXED_YIELD] items once, limpwurt with a level bonus.
 */
object FarmingCropsTable {
    const val SEED = 0
    const val PATCH = 1
    const val LEVEL = 2
    const val PLANT_VALUE = 3
    const val STAGES = 4
    const val HARVEST_VALUE = 5
    const val PRODUCE = 6
    const val PLANT_XP = 7
    const val HARVEST_XP = 8
    const val CTS_LOW = 9
    const val CTS_HIGH = 10
    const val FIXED_YIELD = 11
    const val LEVEL_BONUS = 12

    const val ALLOTMENT = 0
    const val FLOWER = 1
    const val HERB = 2

    private data class Row(
        val name: String,
        val patch: Int,
        val level: Int,
        val plantValue: Int,
        val stages: Int,
        val harvestValue: Int,
        val produce: String,
        val plantXp: Int,
        val harvestXp: Int,
        val ctsLow: Int = 0,
        val ctsHigh: Int = 0,
        val fixedYield: Int = 0,
        val levelBonus: Boolean = false,
    )

    private fun allotment(
        name: String,
        level: Int,
        plantValue: Int,
        stages: Int,
        harvestValue: Int,
        plantXp: Int,
        harvestXp: Int,
        ctsLow: Int,
        ctsHigh: Int = 180,
    ) = Row(name, ALLOTMENT, level, plantValue, stages, harvestValue, name, plantXp, harvestXp, ctsLow, ctsHigh)

    private fun herb(name: String, level: Int, plantValue: Int, plantXp: Int, harvestXp: Int, ctsLow: Int) =
        Row(name, HERB, level, plantValue, 4, plantValue + 4, "unidentified_$name", plantXp, harvestXp, ctsLow, 80)

    private fun flower(
        name: String,
        produce: String,
        level: Int,
        plantValue: Int,
        plantXp: Int,
        harvestXp: Int,
        fixedYield: Int = 1,
        levelBonus: Boolean = false,
    ) = Row(name, FLOWER, level, plantValue, 4, plantValue + 4, produce, plantXp, harvestXp, fixedYield = fixedYield, levelBonus = levelBonus)

    private val ROWS =
        listOf(
            allotment("potato", 1, 6, 4, 10, 80, 90, 101),
            allotment("onion", 5, 13, 4, 17, 95, 105, 105),
            allotment("cabbage", 7, 20, 4, 24, 100, 115, 107),
            allotment("tomato", 12, 27, 4, 31, 125, 140, 112),
            allotment("sweetcorn", 20, 34, 6, 40, 170, 190, 88),
            allotment("strawberry", 31, 43, 6, 49, 260, 290, 103),
            allotment("watermelon", 47, 52, 8, 60, 485, 545, 126),
            allotment("snape_grass", 61, 63, 7, 138, 820, 820, 148, 195),
            flower("marigold", "marigold", 2, 8, 85, 470),
            flower("rosemary", "rosemary", 11, 13, 120, 665),
            flower("nasturtium", "nasturtium", 24, 18, 195, 1110),
            flower("woad", "woadleaf", 25, 23, 205, 1155, fixedYield = 3),
            flower("limpwurt", "limpwurt_root", 26, 28, 215, 1200, fixedYield = 3, levelBonus = true),
            flower("white_lily", "whitelilly", 58, 37, 420, 2500),
            herb("guam", 9, 4, 110, 125, 25),
            herb("marrentill", 14, 11, 135, 150, 28),
            herb("tarromin", 19, 18, 160, 180, 31),
            herb("harralander", 26, 25, 215, 240, 36),
            herb("ranarr", 32, 32, 270, 305, 39),
            herb("toadflax", 38, 39, 340, 385, 43),
            herb("irit", 44, 46, 430, 485, 46),
            herb("avantoe", 50, 53, 545, 615, 50),
            herb("kwuarm", 56, 68, 690, 780, 54),
            herb("snapdragon", 62, 75, 875, 985, 57),
            herb("cadantine", 67, 82, 1065, 1200, 60),
            herb("lantadyme", 73, 89, 1345, 1515, 64),
            herb("dwarf_weed", 79, 96, 1705, 1920, 67),
            herb("torstol", 85, 103, 1995, 2245, 71),
        )

    private fun produceName(row: Row): String =
        if (row.produce == "unidentified_marrentill") "unidentified_marentill" else row.produce

    fun farmingCrops() =
        dbTable("dbtable.farming_crops", serverOnly = true) {
            column("seed", SEED, VarType.OBJ)
            column("patch", PATCH, VarType.INT)
            column("level", LEVEL, VarType.INT)
            column("plant_value", PLANT_VALUE, VarType.INT)
            column("stages", STAGES, VarType.INT)
            column("harvest_value", HARVEST_VALUE, VarType.INT)
            column("produce", PRODUCE, VarType.OBJ)
            column("plant_xp", PLANT_XP, VarType.INT)
            column("harvest_xp", HARVEST_XP, VarType.INT)
            column("cts_low", CTS_LOW, VarType.INT)
            column("cts_high", CTS_HIGH, VarType.INT)
            column("fixed_yield", FIXED_YIELD, VarType.INT)
            column("level_bonus", LEVEL_BONUS, VarType.BOOLEAN)

            for (r in ROWS) {
                row("dbrow.farming_crop_${r.name}") {
                    columnRSCM(SEED, "obj.${r.name}_seed")
                    column(PATCH, r.patch)
                    column(LEVEL, r.level)
                    column(PLANT_VALUE, r.plantValue)
                    column(STAGES, r.stages)
                    column(HARVEST_VALUE, r.harvestValue)
                    columnRSCM(PRODUCE, "obj.${produceName(r)}")
                    column(PLANT_XP, r.plantXp)
                    column(HARVEST_XP, r.harvestXp)
                    column(CTS_LOW, r.ctsLow)
                    column(CTS_HIGH, r.ctsHigh)
                    column(FIXED_YIELD, r.fixedYield)
                    column(LEVEL_BONUS, r.levelBonus)
                }
            }
        }
}
