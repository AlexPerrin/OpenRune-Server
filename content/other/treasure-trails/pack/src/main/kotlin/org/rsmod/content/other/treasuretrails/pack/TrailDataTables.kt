package org.rsmod.content.other.treasuretrails.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * What the cache's `cluehelper_*` tables (Jagex's clue data: texts, targets, outfits, STASH locs)
 * leave out: the item Charlie the Tramp asks for in each of his skill challenge rows, the map
 * interface each map clue row opens, and the emote seq for each emote slot the emote rows name.
 */
object TrailDataTables {
    private val CHARLIE =
        listOf(
            Triple(1811, "obj.trout", "a cooked trout"),
            Triple(1812, "obj.pike", "a cooked pike"),
            Triple(1813, "obj.raw_herring", "a raw herring"),
            Triple(1814, "obj.raw_trout", "a raw trout"),
            Triple(1815, "obj.iron_ore", "a piece of iron ore"),
            Triple(1816, "obj.iron_dagger", "an iron dagger"),
            Triple(1817, "obj.leather_armour", "a leather body"),
            Triple(1818, "obj.leather_chaps", "some leather chaps"),
        )

    private val MAPS =
        listOf(
            2223 to "interface.trail_map01",
            2224 to "interface.trail_map02",
            2225 to "interface.trail_map03",
            2226 to "interface.trail_map06",
            2227 to "interface.trail_map11",
            2229 to "interface.trail_map01",
            2230 to "interface.trail_map02",
            2231 to "interface.trail_map06",
            2233 to "interface.trail_map11",
            2228 to "interface.trail_map18",
            2232 to "interface.trail_map09",
            2234 to "interface.trail_clue_easy_map006",
            2235 to "interface.trail_map03",
            2236 to "interface.trail_map07",
            2237 to "interface.trail_map10",
            2238 to "interface.trail_map15",
            2239 to "interface.trail_map16",
            2240 to "interface.trail_map17",
            2241 to "interface.trail_clue_medium_map008",
            2242 to "interface.trail_clue_medium_map009",
            2243 to "interface.trail_clue_medium_map010",
            2244 to "interface.trail_clue_medium_map011",
            2245 to "interface.trail_clue_medium_map012",
            2246 to "interface.trail_map04",
            2247 to "interface.trail_map05",
            2248 to "interface.trail_map08",
            2249 to "interface.trail_map12",
            2250 to "interface.trail_map13",
            2251 to "interface.trail_map14",
            2252 to "interface.trail_clue_hard_map006",
            2253 to "interface.trail_clue_hard_map007",
        )

    private val EMOTES =
        listOf(
            "seq.emote_yes",
            "seq.emote_no",
            "seq.emote_bow",
            "seq.emote_angry",
            "seq.emote_think",
            "seq.emote_wave",
            "seq.emote_shrug",
            "seq.emote_cheer",
            "seq.emote_beckon",
            "seq.emote_laugh",
            "seq.emote_jump_with_joy",
            "seq.emote_yawn",
            "seq.emote_dance",
            "seq.emote_dance_scottish",
            "seq.emote_dance_spin",
            "seq.emote_dance_headbang",
            "seq.emote_cry",
            "seq.emote_blow_kiss",
            "seq.emote_panic",
            "seq.emote_ya_boo_sucks",
            "seq.emote_clap",
            "seq.emote_fremmenik_salute",
        )

    fun charlie() =
        dbTable("dbtable.trail_charlie_tasks", serverOnly = true) {
            column("challenge", 0, VarType.INT)
            column("obj", 1, VarType.OBJ)
            column("request", 2, VarType.STRING)
            for ((i, task) in CHARLIE.withIndex()) {
                row("dbrow.trail_charlie_task_$i") {
                    column(0, task.first)
                    columnRSCM(1, task.second)
                    column(2, "I really need ${task.third}.")
                }
            }
        }

    fun maps() =
        dbTable("dbtable.trail_map_interfaces", serverOnly = true) {
            column("clue", 0, VarType.INT)
            column("map", 1, VarType.STRING)
            for ((i, map) in MAPS.withIndex()) {
                row("dbrow.trail_map_interface_$i") {
                    column(0, map.first)
                    column(1, map.second)
                }
            }
        }

    fun emotes() =
        dbTable("dbtable.trail_emote_seqs", serverOnly = true) {
            column("slot", 0, VarType.INT)
            column("seq", 1, VarType.STRING)
            for ((slot, seq) in EMOTES.withIndex()) {
                row("dbrow.trail_emote_seq_$slot") {
                    column(0, slot)
                    column(1, seq)
                }
            }
        }
}
