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

    /** Lumbridge & Draynor easy 11: "Mine some Iron ore at the Al Kharid mine." */
    fun oreMined(player: Player, ore: String, coords: CoordGrid) {
        if (ore == "obj.iron_ore" && inAlKharidMine(coords)) {
            DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 11)
        }
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

    /** Varrock easy 4: "Make a normal plank at the Sawmill." */
    fun planksMade(player: Player, plank: String) {
        if (plank == "obj.wood_plank") DiaryTasks.complete(player, Area.Varrock, Tier.Easy, 4)
    }

    private fun inLumbridge(c: CoordGrid) = c.level == 0 && c.x in 3170..3270 && c.z in 3190..3270

    private fun inAlKharidMine(c: CoordGrid) = c.level == 0 && c.x in 3280..3310 && c.z in 3270..3320
}
