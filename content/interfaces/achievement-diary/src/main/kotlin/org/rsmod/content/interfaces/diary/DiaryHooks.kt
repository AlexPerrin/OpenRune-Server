package org.rsmod.content.interfaces.diary

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.interfaces.diary.DiaryTasks.Area
import org.rsmod.content.interfaces.diary.DiaryTasks.Tier
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Calls from content into the diary task tracking, one per action that completes a task. Task
 * numbers are the journal order (the wiki's). The area boxes are **guesses** (whole towns).
 */
object DiaryHooks {
    private const val OAK_CUT_IN_LUMBRIDGE = 1
    private const val BOWL_SPUN_IN_BARBARIAN_VILLAGE = 1
    private const val MAHOGANY_PLANKS_AT_ONCE = 20

    /** Bits in `diary_progress_varrock` for the Stronghold emotes (bit 0 is the bowl). */
    private val STRONGHOLD_EMOTE_BITS =
        mapOf(
            "seq.emote_panic_flap" to 2,
            "seq.emote_slap_head" to 4,
            "seq.emote_lightbulb" to 8,
            "seq.emote_stampfeet" to 16,
        )

    /** Lumbridge & Draynor easy 5: "Learn your age from Hans in Lumbridge." */
    fun hansAge(player: Player) {
        DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 5)
    }

    /** Easy 4 (Lumbridge & Draynor) water runes, easy 12 (Varrock) earth runes, from essence. */
    fun runesCrafted(player: Player, rune: String) {
        when (rune) {
            "obj.waterrune" -> DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 4)
            "obj.earthrune" -> DiaryTasks.complete(player, Area.Varrock, Tier.Easy, 12)
        }
    }

    /** Lumbridge & Draynor easy 7 (first half): oak logs cut in Lumbridge. */
    fun logsCut(player: Player, logs: String, coords: CoordGrid) {
        if (logs != "obj.oak_logs" || !inLumbridge(coords)) return
        val progress = player.vars["varp.diary_progress_lumbridge"]
        VarPlayerIntMapSetter.set(player, "varp.diary_progress_lumbridge", progress or OAK_CUT_IN_LUMBRIDGE)
    }

    /** Lumbridge & Draynor easy 7: "Chop and burn some oak logs in Lumbridge." */
    fun logsBurned(player: Player, logs: String, coords: CoordGrid) {
        if (logs != "obj.oak_logs" || !inLumbridge(coords)) return
        if (player.vars["varp.diary_progress_lumbridge"] and OAK_CUT_IN_LUMBRIDGE == 0) return
        DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 7)
    }

    /** Lumbridge & Draynor easy 10: "Bake some Bread on the Lumbridge kitchen range." */
    fun cooked(player: Player, product: String, lumbridgeRange: Boolean) {
        if (product == "obj.bread" && lumbridgeRange) {
            DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 10)
        }
    }

    /**
     * Lumbridge & Draynor easy 11: "Mine some Iron ore at the Al Kharid mine."; Varrock easy 3: "Mine
     * some Iron in the south east mining patch near Varrock."
     */
    fun oreMined(player: Player, ore: String, coords: CoordGrid) {
        if (ore != "obj.iron_ore") return
        if (inAlKharidMine(coords)) DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 11)
        if (inSouthEastVarrockMine(coords)) DiaryTasks.complete(player, Area.Varrock, Tier.Easy, 3)
    }

    /** Lumbridge & Draynor medium 8: "Chop some willow logs in Draynor Village." */
    fun logsChopped(player: Player, logs: String, coords: CoordGrid) {
        if (logs == "obj.willow_logs" && inDraynor(coords)) {
            DiaryTasks.complete(player, Area.Lumbridge, Tier.Medium, 8)
        }
    }

    /**
     * Lumbridge & Draynor easy 9 (anchovies in Al Kharid), medium 6 (salmon in Lumbridge); Varrock easy
     * 13 (trout at Barbarian Village). [coords] is where the player stands.
     */
    fun fishCaught(player: Player, fish: String, coords: CoordGrid) {
        when {
            fish == "obj.raw_anchovies" && inAlKharid(coords) ->
                DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 9)
            fish == "obj.raw_salmon" && inLumbridge(coords) ->
                DiaryTasks.complete(player, Area.Lumbridge, Tier.Medium, 6)
            fish == "obj.raw_trout" && inBarbarianVillage(coords) ->
                DiaryTasks.complete(player, Area.Varrock, Tier.Easy, 13)
        }
    }

    /**
     * Lumbridge & Draynor easy 6 ("Pickpocket a man or woman in Lumbridge.") and medium 9
     * ("Pickpocket Martin the Master Gardener.").
     */
    fun pickpocketed(player: Player, npc: String, npcName: String, coords: CoordGrid) {
        when {
            npc == "npc.martin_the_master_farmer" ->
                DiaryTasks.complete(player, Area.Lumbridge, Tier.Medium, 9)
            (npcName == "Man" || npcName == "Woman") && inLumbridge(coords) ->
                DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 6)
        }
    }

    /**
     * Lumbridge & Draynor easy 2 ("Slay a Cave bug beneath Lumbridge Swamp.") and easy 8 ("Kill a
     * Zombie in Draynor Sewers.").
     */
    fun npcKilled(player: Player, npcName: String, coords: CoordGrid) {
        when {
            npcName == "Cave bug" && inLumbridgeSwampCaves(coords) ->
                DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 2)
            npcName == "Zombie" && inDraynorSewers(coords) ->
                DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 8)
        }
    }

    /**
     * Crafting products: Lumbridge & Draynor medium 7 ("Craft a coif in the Lumbridge cow pen."); Varrock
     * easy 10 ("Spin a bowl on the pottery wheel and fire it in the oven in Barb Village.").
     */
    fun crafted(player: Player, product: String, coords: CoordGrid) {
        when (product) {
            "obj.coif" ->
                if (inLumbridgeCowPen(coords)) DiaryTasks.complete(player, Area.Lumbridge, Tier.Medium, 7)
            "obj.bowl_unfired" ->
                if (inBarbarianVillage(coords)) {
                    val progress = player.vars["varp.diary_progress_varrock"]
                    VarPlayerIntMapSetter.set(
                        player,
                        "varp.diary_progress_varrock",
                        progress or BOWL_SPUN_IN_BARBARIAN_VILLAGE,
                    )
                }
            "obj.bowl_empty" ->
                if (
                    inBarbarianVillage(coords) &&
                        player.vars["varp.diary_progress_varrock"] and BOWL_SPUN_IN_BARBARIAN_VILLAGE != 0
                ) {
                    DiaryTasks.complete(player, Area.Varrock, Tier.Easy, 10)
                }
        }
    }

    /** Varrock easy 1: "Browse Thessalia's store." */
    fun thessaliaStoreBrowsed(player: Player) {
        DiaryTasks.complete(player, Area.Varrock, Tier.Easy, 1)
    }

    /** Varrock easy 5: "Enter the second level of the Stronghold of Security." */
    fun strongholdSecondLevelEntered(player: Player) {
        DiaryTasks.complete(player, Area.Varrock, Tier.Easy, 5)
    }

    /** Lumbridge & Draynor medium 10 (Chaeldar, master 4) and Varrock medium 9 (Vannaka, master 3). */
    fun slayerTaskAssigned(player: Player, masterId: Int) {
        when (masterId) {
            4 -> DiaryTasks.complete(player, Area.Lumbridge, Tier.Medium, 10)
            3 -> DiaryTasks.complete(player, Area.Varrock, Tier.Medium, 9)
        }
    }

    /** Varrock medium 5: "Perform the 4 emotes from the Stronghold of Security." (any order, anywhere) */
    fun emotePerformed(player: Player, seq: String) {
        val bit = STRONGHOLD_EMOTE_BITS.entries.firstOrNull { seq.startsWith(it.key) }?.value ?: return
        val progress = player.vars["varp.diary_progress_varrock"] or bit
        VarPlayerIntMapSetter.set(player, "varp.diary_progress_varrock", progress)
        if (STRONGHOLD_EMOTE_BITS.values.all { progress and it != 0 }) {
            DiaryTasks.complete(player, Area.Varrock, Tier.Medium, 5)
        }
    }

    /** Lumbridge & Draynor hard 4: "Travel from Lumbridge to Edgeville on a Waka Canoe." */
    fun canoeTravelled(player: Player, waka: Boolean, from: String, to: String) {
        if (waka && from == "Lumbridge" && to == "Edgeville") {
            DiaryTasks.complete(player, Area.Lumbridge, Tier.Hard, 4)
        }
    }

    /** Varrock hard 3: "Make a Waka Canoe near Edgeville." */
    fun canoeShaped(player: Player, waka: Boolean, station: String) {
        if (waka && station == "Edgeville") DiaryTasks.complete(player, Area.Varrock, Tier.Hard, 3)
    }

    /** Varrock medium 3: "Select a colour for your kitten." */
    fun kittenColourSelected(player: Player) {
        DiaryTasks.complete(player, Area.Varrock, Tier.Medium, 3)
    }

    /** Lumbridge & Draynor medium 5 and Varrock medium 8: the standard teleport spells. */
    fun spellTeleport(player: Player, spell: String) {
        when (spell) {
            "lumbridge" -> DiaryTasks.complete(player, Area.Lumbridge, Tier.Medium, 5)
            "varrock" -> DiaryTasks.complete(player, Area.Varrock, Tier.Medium, 8)
        }
    }

    /** Lumbridge & Draynor easy 3 (Sedridor) and Varrock easy 2 (Aubury): essence mine teleports. */
    fun essenceMineTeleport(player: Player, npc: String) {
        when (npc) {
            "npc.head_wizard" -> DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 3)
            "npc.aubury" -> DiaryTasks.complete(player, Area.Varrock, Tier.Easy, 2)
        }
    }

    /** Varrock easy 4 ("Make a normal plank at the Sawmill.") and medium 10 ("Make 20 Mahogany Planks in one go."). */
    fun planksMade(player: Player, plank: String, count: Int = 1) {
        if (plank == "obj.wood_plank") DiaryTasks.complete(player, Area.Varrock, Tier.Easy, 4)
        if (plank == "obj.mahogany_plank" && count >= MAHOGANY_PLANKS_AT_ONCE) {
            DiaryTasks.complete(player, Area.Varrock, Tier.Medium, 10)
        }
    }

    private fun inLumbridge(c: CoordGrid) = c.level == 0 && c.x in 3170..3270 && c.z in 3190..3270

    private fun inAlKharidMine(c: CoordGrid) = c.level == 0 && c.x in 3280..3310 && c.z in 3270..3320

    private fun inAlKharid(c: CoordGrid) = c.level == 0 && c.x in 3240..3340 && c.z in 3120..3230

    private fun inSouthEastVarrockMine(c: CoordGrid) = c.level == 0 && c.x in 3280..3292 && c.z in 3360..3372

    private fun inDraynor(c: CoordGrid) = c.level == 0 && c.x in 3070..3135 && c.z in 3220..3300

    private fun inBarbarianVillage(c: CoordGrid) = c.level == 0 && c.x in 3070..3112 && c.z in 3400..3445

    private fun inLumbridgeCowPen(c: CoordGrid) = c.level == 0 && c.x in 3253..3266 && c.z in 3255..3299

    private fun inLumbridgeSwampCaves(c: CoordGrid) = c.level == 0 && c.x in 3136..3263 && c.z in 9530..9600

    private fun inDraynorSewers(c: CoordGrid) = c.level == 0 && c.x in 3072..3135 && c.z in 9600..9727
}
