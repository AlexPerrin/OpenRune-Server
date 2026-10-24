package org.rsmod.content.interfaces.diary

import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * Achievement diary task progress. Done tasks are bits (task number - 1) in server varps
 * `diary_tasks_<area>_<tier>` (the per-task bits of the real `lumb_dray_achievement_diary` /
 * `varrock_achievement_diary` varps are not mapped; **guess**). As captured (rsprox, completing
 * Ardougne easy tasks), completing a task sends on the same tick the chat message "Well done! You
 * have completed an easy task in the <area> area. Your Achievement Diary has been updated.", the
 * tier's `<area>_<tier>_count` varbit and, on the diary's first task, `started_<area>_diary`; the
 * journal strikes the task. [TIER_COMPLETE_DELAY] ticks after a tier's last task its
 * `<area>_diary_<tier>_complete` varbit is set and a message box congratulates the player
 * ([tierCompleted], scheduled by [DiaryTierCompleteScript]).
 */
object DiaryTasks {
    const val TIER_COMPLETE_DELAY = 3

    /**
     * [title] is the diary's name in the messages; [rewardNpc] how the tier-complete box names the
     * reward NPC and where they stand.
     */
    enum class Area(
        val key: String,
        val countPrefix: String,
        val title: String,
        val rewardNpc: String,
        val tierTotals: List<Int>,
    ) {
        Lumbridge(
            "lumbridge",
            "lumbridge",
            "Lumbridge & Draynor",
            "Hatius Cosaintus east of Lumbridge Castle",
            listOf(12, 12, 11, 6),
        ),
        Varrock("varrock", "varrock", "Varrock", "Toby behind the general store in Varrock", listOf(14, 13, 10, 5)),
    }

    enum class Tier(val key: String, val countKey: String, val completeKey: String, val article: String) {
        Easy("easy", "easy", "easy", "an"),
        Medium("medium", "med", "medium", "a"),
        Hard("hard", "hard", "hard", "a"),
        Elite("elite", "elite", "elite", "an"),
    }

    /**
     * Runs on a tier's last task. [DiaryTierCompleteScript] installs the delayed varbit and
     * message box; until it does, the varbit is set at once.
     */
    internal var tierCompleted: (Player, Area, Tier) -> Unit = ::setTierComplete

    fun isDone(player: Player, area: Area, tier: Tier, task: Int): Boolean =
        player.vars[tasksVarp(area, tier)] and (1 shl (task - 1)) != 0

    fun doneTasks(player: Player, area: Area, tier: Tier): Set<Int> {
        val bits = player.vars[tasksVarp(area, tier)]
        return (1..31).filter { bits and (1 shl (it - 1)) != 0 }.toSet()
    }

    fun isTierDone(player: Player, area: Area, tier: Tier): Boolean =
        Integer.bitCount(player.vars[tasksVarp(area, tier)]) == area.tierTotals[tier.ordinal]

    /** Marks [task] (1-based, journal order) done. Returns false if it already was. */
    fun complete(player: Player, area: Area, tier: Tier, task: Int): Boolean {
        val total = area.tierTotals[tier.ordinal]
        require(task in 1..total) { "No task $task in ${area.key} ${tier.key}." }
        if (isDone(player, area, tier, task)) return false
        val varp = tasksVarp(area, tier)
        val bits = player.vars[varp] or (1 shl (task - 1))
        VarPlayerIntMapSetter.set(player, varp, bits)
        player.mes(taskCompleteMessage(area, tier))
        val count = Integer.bitCount(bits)
        VarPlayerIntMapSetter.set(player, "varbit.${area.countPrefix}_${tier.countKey}_count", count)
        VarPlayerIntMapSetter.set(player, "varbit.started_${area.key}_diary", 1)
        if (count == total) tierCompleted(player, area, tier)
        return true
    }

    fun taskCompleteMessage(area: Area, tier: Tier): String =
        "<col=dc143c>Well done! You have completed ${tier.article} ${tier.key} task in the " +
            "${area.title} area. Your Achievement Diary has been updated.</col>"

    fun tierCompleteText(area: Area, tier: Tier): String =
        "Congratulations! You have completed all of the ${tier.key} tasks in the ${area.title} " +
            "area. Speak to ${area.rewardNpc} to claim your reward."

    fun setTierComplete(player: Player, area: Area, tier: Tier) {
        VarPlayerIntMapSetter.set(player, completeVarbit(area, tier), 1)
    }

    fun isTierComplete(player: Player, area: Area, tier: Tier): Boolean =
        player.vars[completeVarbit(area, tier)] != 0

    /** Re-sends the client varbits from the server bits (they may not persist on their own). */
    fun sync(player: Player) {
        for (area in Area.entries) {
            var started = false
            for (tier in Tier.entries) {
                val count = Integer.bitCount(player.vars[tasksVarp(area, tier)])
                if (count == 0) continue
                started = true
                VarPlayerIntMapSetter.set(player, "varbit.${area.countPrefix}_${tier.countKey}_count", count)
                if (count == area.tierTotals[tier.ordinal]) setTierComplete(player, area, tier)
            }
            if (started) VarPlayerIntMapSetter.set(player, "varbit.started_${area.key}_diary", 1)
        }
    }

    private fun completeVarbit(area: Area, tier: Tier) = "varbit.${area.key}_diary_${tier.completeKey}_complete"

    private fun tasksVarp(area: Area, tier: Tier) = "varp.diary_tasks_${area.key}_${tier.key}"
}
