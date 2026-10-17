package org.rsmod.content.interfaces.diary

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * Achievement diary task progress. Done tasks are bits (task number - 1) in server varps
 * `diary_tasks_<area>_<tier>` (the per-task bits of the real `lumb_dray_achievement_diary` /
 * `varrock_achievement_diary` varps are not mapped; **guess**). Completing a task updates the
 * client-visible varbits the Achievement Diaries tab reads (`<area>_<tier>_count`,
 * `started_<area>_diary`, `<area>_diary_<tier>_complete` once every task of the tier is done) and
 * strikes the task in the journal. No chat message: its wording is in neither the wiki nor any
 * plugin (known gap).
 */
object DiaryTasks {
    enum class Area(val key: String, val countPrefix: String, val tierTotals: List<Int>) {
        Lumbridge("lumbridge", "lumbridge", listOf(12, 12, 11, 6)),
        Varrock("varrock", "varrock", listOf(14, 13, 10, 5)),
    }

    enum class Tier(val key: String, val countKey: String, val completeKey: String) {
        Easy("easy", "easy", "easy"),
        Medium("medium", "med", "medium"),
        Hard("hard", "hard", "hard"),
        Elite("elite", "elite", "elite"),
    }

    fun isDone(player: Player, area: Area, tier: Tier, task: Int): Boolean =
        player.vars[tasksVarp(area, tier)] and (1 shl (task - 1)) != 0

    fun doneTasks(player: Player, area: Area, tier: Tier): Set<Int> {
        val bits = player.vars[tasksVarp(area, tier)]
        return (1..31).filter { bits and (1 shl (it - 1)) != 0 }.toSet()
    }

    /** Marks [task] (1-based, journal order) done. Returns false if it already was. */
    fun complete(player: Player, area: Area, tier: Tier, task: Int): Boolean {
        val total = area.tierTotals[tier.ordinal]
        require(task in 1..total) { "No task $task in ${area.key} ${tier.key}." }
        if (isDone(player, area, tier, task)) return false
        val varp = tasksVarp(area, tier)
        val bits = player.vars[varp] or (1 shl (task - 1))
        VarPlayerIntMapSetter.set(player, varp, bits)
        val count = Integer.bitCount(bits)
        VarPlayerIntMapSetter.set(player, "varbit.${area.countPrefix}_${tier.countKey}_count", count)
        VarPlayerIntMapSetter.set(player, "varbit.started_${area.key}_diary", 1)
        if (count == total) {
            VarPlayerIntMapSetter.set(
                player,
                "varbit.${area.key}_diary_${tier.completeKey}_complete",
                1,
            )
        }
        return true
    }

    /** Re-sends the client varbits from the server bits (they may not persist on their own). */
    fun sync(player: Player) {
        for (area in Area.entries) {
            var started = false
            for (tier in Tier.entries) {
                val count = Integer.bitCount(player.vars[tasksVarp(area, tier)])
                if (count == 0) continue
                started = true
                VarPlayerIntMapSetter.set(player, "varbit.${area.countPrefix}_${tier.countKey}_count", count)
                if (count == area.tierTotals[tier.ordinal]) {
                    VarPlayerIntMapSetter.set(player, "varbit.${area.key}_diary_${tier.completeKey}_complete", 1)
                }
            }
            if (started) VarPlayerIntMapSetter.set(player, "varbit.started_${area.key}_diary", 1)
        }
    }

    private fun tasksVarp(area: Area, tier: Tier) = "varp.diary_tasks_${area.key}_${tier.key}"
}
