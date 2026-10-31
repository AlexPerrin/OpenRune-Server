package org.rsmod.content.interfaces.diary

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onEvent
import org.rsmod.content.interfaces.diary.DiaryTasks.Area
import org.rsmod.content.interfaces.diary.DiaryTasks.Tier
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * As captured (rsprox, the last Ardougne easy task): [DiaryTasks.TIER_COMPLETE_DELAY] ticks after a
 * tier's last task, its `<area>_diary_<tier>_complete` varbit is set and a message box says
 * "Congratulations! You have completed all of the easy tasks in the <area> area. Speak to <NPC> to
 * claim your reward.". The box waits until the player is free (they may still be in the dialogue
 * that completed the task).
 */
class DiaryTierCompleteScript @Inject constructor(private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {
    private class Pending(val player: Player, val area: Area, val tier: Tier, var cycles: Int) {
        var announced = false
    }

    private val pending = mutableListOf<Pending>()

    override fun ScriptContext.startup() {
        DiaryTasks.tierCompleted = { player, area, tier ->
            pending += Pending(player, area, tier, DiaryTasks.TIER_COMPLETE_DELAY)
        }
        onEvent<GameLifecycle.StartCycle> { process() }
    }

    private fun process() {
        if (pending.isEmpty()) return
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!entry.player.isSlotAssigned) {
                iterator.remove()
                continue
            }
            if (--entry.cycles > 0) continue
            if (!entry.announced) {
                if (!DiaryTasks.isTierDone(entry.player, entry.area, entry.tier)) {
                    iterator.remove()
                    continue
                }
                DiaryTasks.setTierComplete(entry.player, entry.area, entry.tier)
                entry.announced = true
            }
            val text = DiaryTasks.tierCompleteText(entry.area, entry.tier)
            if (protectedAccess.launch(entry.player) { mesbox(text) }) iterator.remove()
        }
    }
}
