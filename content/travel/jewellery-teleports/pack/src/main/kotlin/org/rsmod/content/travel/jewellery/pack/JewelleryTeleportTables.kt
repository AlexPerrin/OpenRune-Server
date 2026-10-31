package org.rsmod.content.travel.jewellery.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

private const val GAMES = 0
private const val DUELING = 1
private const val GLORY = 2
private const val SKILLS = 3
private const val COMBAT = 4

/**
 * Charged teleport jewellery (OSRS Wiki: Games necklace, Ring of dueling, Amulet of glory, Skills
 * necklace, Combat bracelet). One row
 * per charge variant: [CHARGES] left before use, [NEXT] the item after one use. [CRUMBLES] is 1 when
 * the last use destroys it ("Your games necklace crumbles to dust."); the glory's last use leaves an
 * uncharged amulet. [FAMILY] picks the destinations in [JewelleryTeleportDestsTable].
 * [WILDERNESS_30] is 1 for the glory, skills necklace and combat bracelet (teleports up to level 30
 * Wilderness; their messages say "charges" rather than "uses"). [CHARGE_NAME] is the noun of the
 * charge lines: "Your amulet has ..." / "...your amulet's last charge" for the glory, the full name
 * for the others (the chat prefixes the Supplies Tracker hub plugin matches).
 */
object JewelleryTeleportItemsTable {
    const val OBJ = 0
    const val FAMILY = 1
    const val CHARGES = 2
    const val NEXT = 3
    const val CRUMBLES = 4
    const val NAME = 5
    const val RUB_WORD = 6
    const val WILDERNESS_30 = 7
    const val CHARGE_NAME = 8

    private data class Family(
        val key: String,
        val id: Int,
        val prefix: String,
        val max: Int,
        val name: String,
        val rub: String,
        val spent: String?,
        val wilderness30: Int,
        val chargeName: String = name,
    )

    private val FAMILIES =
        listOf(
            Family("games_necklace", GAMES, "obj.necklace_of_minigames_", 8, "games necklace", "necklace", null, 0),
            Family("ring_of_dueling", DUELING, "obj.ring_of_dueling_", 8, "ring of dueling", "ring", null, 0),
            Family("amulet_of_glory", GLORY, "obj.amulet_of_glory_", 6, "amulet of glory", "amulet", "obj.amulet_of_glory", 1, "amulet"),
            Family("skills_necklace", SKILLS, "obj.jewl_necklace_of_skills_", 6, "skills necklace", "necklace", "obj.jewl_necklace_of_skills", 1),
            Family("combat_bracelet", COMBAT, "obj.jewl_bracelet_of_combat_", 6, "combat bracelet", "bracelet", "obj.jewl_bracelet_of_combat", 1),
        )

    fun items() =
        dbTable("dbtable.jewellery_teleport_items", serverOnly = true) {
            column("obj", OBJ, VarType.OBJ)
            column("family", FAMILY, VarType.INT)
            column("charges", CHARGES, VarType.INT)
            column("next", NEXT, VarType.OBJ)
            column("crumbles", CRUMBLES, VarType.INT)
            column("name", NAME, VarType.STRING)
            column("rub_word", RUB_WORD, VarType.STRING)
            column("wilderness_30", WILDERNESS_30, VarType.INT)
            column("charge_name", CHARGE_NAME, VarType.STRING)

            for (family in FAMILIES) {
                for (charges in 1..family.max) {
                    val obj = "${family.prefix}$charges"
                    val next = if (charges > 1) "${family.prefix}${charges - 1}" else family.spent ?: obj
                    row("dbrow.jewellery_teleport_${family.key}_$charges") {
                        columnRSCM(OBJ, obj)
                        column(FAMILY, family.id)
                        column(CHARGES, charges)
                        columnRSCM(NEXT, next)
                        column(CRUMBLES, if (charges == 1 && family.spent == null) 1 else 0)
                        column(NAME, family.name)
                        column(RUB_WORD, family.rub)
                        column(WILDERNESS_30, family.wilderness30)
                        column(CHARGE_NAME, family.chargeName)
                    }
                }
            }
        }
}

/**
 * Destinations in menu order (the item's worn ops after Remove; wiki "Teleportation options").
 * Tiles are the Shortest Path hub plugin's (`teleportation_items.tsv`); the wiki's random radius is
 * not used. Corporeal Beast is the lair's safe room in map square 46,68 (Shortest Path 2964,4382,2,
 * the same tile its lair cave entrance leads to; RuneLite's world map marks 2967,4384). [INSIDE_COORDS]
 * replaces [COORDS] when the player's Farming level is at least [INSIDE_FARMING_LEVEL]: the Farming
 * Guild teleport lands inside the guild with 45 Farming (wiki Skills necklace, note a).
 */
object JewelleryTeleportDestsTable {
    const val FAMILY = 0
    const val INDEX = 1
    const val NAME = 2
    const val COORDS = 3
    const val INSIDE_COORDS = 4
    const val INSIDE_FARMING_LEVEL = 5

    private data class Dest(
        val key: String,
        val family: Int,
        val name: String,
        val coords: CoordGrid,
        val inside: CoordGrid? = null,
        val insideFarmingLevel: Int = 0,
    )

    private val DESTS =
        listOf(
            Dest("games_burthorpe", GAMES, "Burthorpe", CoordGrid(2899, 3553, 0)),
            Dest("games_barbarian_outpost", GAMES, "Barbarian Outpost", CoordGrid(2520, 3571, 0)),
            Dest("games_corporeal_beast", GAMES, "Corporeal Beast", CoordGrid(2964, 4382, 2)),
            Dest("games_tears_of_guthix", GAMES, "Tears of Guthix", CoordGrid(3245, 9500, 2)),
            Dest("games_wintertodt", GAMES, "Wintertodt Camp", CoordGrid(1631, 3940, 0)),
            Dest("dueling_emirs_arena", DUELING, "Emir's Arena", CoordGrid(3315, 3235, 0)),
            Dest("dueling_castle_wars", DUELING, "Castle Wars", CoordGrid(2440, 3090, 0)),
            Dest("dueling_ferox_enclave", DUELING, "Ferox Enclave", CoordGrid(3151, 3635, 0)),
            Dest("dueling_fortis_colosseum", DUELING, "Fortis Colosseum", CoordGrid(1793, 3107, 0)),
            Dest("glory_edgeville", GLORY, "Edgeville", CoordGrid(3087, 3496, 0)),
            Dest("glory_karamja", GLORY, "Karamja", CoordGrid(2918, 3176, 0)),
            Dest("glory_draynor_village", GLORY, "Draynor Village", CoordGrid(3105, 3251, 0)),
            Dest("glory_al_kharid", GLORY, "Al Kharid", CoordGrid(3293, 3163, 0)),
            Dest("skills_fishing_guild", SKILLS, "Fishing Guild", CoordGrid(2611, 3390, 0)),
            Dest("skills_mining_guild", SKILLS, "Mining Guild", CoordGrid(3049, 9763, 0)),
            Dest("skills_crafting_guild", SKILLS, "Crafting Guild", CoordGrid(2933, 3295, 0)),
            Dest("skills_cooking_guild", SKILLS, "Cooking Guild", CoordGrid(3144, 3438, 0)),
            Dest("skills_woodcutting_guild", SKILLS, "Woodcutting Guild", CoordGrid(1662, 3505, 0)),
            Dest(
                "skills_farming_guild",
                SKILLS,
                "Farming Guild",
                CoordGrid(1248, 3719, 0),
                inside = CoordGrid(1248, 3725, 0),
                insideFarmingLevel = 45,
            ),
            Dest("combat_warriors_guild", COMBAT, "Warriors' Guild", CoordGrid(2882, 3547, 0)),
            Dest("combat_champions_guild", COMBAT, "Champions' Guild", CoordGrid(3192, 3368, 0)),
            Dest("combat_monastery", COMBAT, "Monastery", CoordGrid(3052, 3490, 0)),
            Dest("combat_ranging_guild", COMBAT, "Ranging Guild", CoordGrid(2653, 3439, 0)),
        )

    fun dests() =
        dbTable("dbtable.jewellery_teleport_dests", serverOnly = true) {
            column("family", FAMILY, VarType.INT)
            column("index", INDEX, VarType.INT)
            column("name", NAME, VarType.STRING)
            column("coords", COORDS, VarType.COORDGRID)
            column("inside_coords", INSIDE_COORDS, VarType.COORDGRID)
            column("inside_farming_level", INSIDE_FARMING_LEVEL, VarType.INT)

            val counts = mutableMapOf<Int, Int>()
            for (dest in DESTS) {
                val index = counts.merge(dest.family, 1, Int::plus)!! - 1
                row("dbrow.jewellery_teleport_dest_${dest.key}") {
                    column(FAMILY, dest.family)
                    column(INDEX, index)
                    column(NAME, dest.name)
                    columnCoord(COORDS, dest.coords)
                    if (dest.inside != null) {
                        columnCoord(INSIDE_COORDS, dest.inside)
                        column(INSIDE_FARMING_LEVEL, dest.insideFarmingLevel)
                    }
                }
            }
        }
}
