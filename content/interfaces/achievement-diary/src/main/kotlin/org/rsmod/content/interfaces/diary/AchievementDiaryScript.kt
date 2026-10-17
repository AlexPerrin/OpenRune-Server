package org.rsmod.content.interfaces.diary

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.FontType
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.table.AchievementDiariesRow
import org.rsmod.api.table.AchievementDiaryTasksRow
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * "Open <area> Journal" in the Achievement Diaries side tab (interface 259, whose list script 709
 * builds one entry per enum 595 area) opens the journal scroll (741) with the area's tasks per
 * tier. RuneLite's Diary Requirements plugin reads 741: the title must start with "Achievement
 * Diary", the first line names the diary ("Lumbridge & Draynor Tasks"), and it matches task lines
 * (joined across wrapped lines) against its own task texts. No task progress is tracked yet, so no
 * line is struck through.
 *
 * Guesses: the title wording, the tier heading lines and their colour. Areas without a row in
 * `achievement_diaries` say so in the chat.
 */
class AchievementDiaryScript : PluginScript() {
    private val font: FontType by lazy { ServerCacheManager.getFont("font.p12_full".asRSCM())!! }

    override fun ScriptContext.startup() {
        onIfOpen("interface.area_task") { player.ifSetEvents(TASKBOX, 0 until AREAS, IfEvent.Op1) }
        onIfOverlayButton(TASKBOX) {
            if (it.op == IfButtonOp.Op1) openJournal(it.comsub)
        }
    }

    fun ProtectedAccess.openJournal(area: Int) {
        val diary = AchievementDiariesRow.all().firstOrNull { it.area == area }
        if (diary == null) {
            mes("That diary's journal isn't available yet.")
            return
        }
        val lines = journalLines(diary)
        ifOpenMain("interface.journalscroll")
        ifSetText("component.journalscroll:title", TITLE)
        val shown = lines.take(MAX_LINES)
        shown.forEachIndexed { index, line -> ifSetText("component.journalscroll:qj${index + 1}", line) }
        runClientScript(JOURNAL_SCROLL_SETUP, 1, shown.size)
    }

    fun journalLines(diary: AchievementDiariesRow): List<String> {
        val lines = mutableListOf(diary.heading)
        val tasks = AchievementDiaryTasksRow.all().filter { it.area == diary.area }
        for ((tier, name) in TIER_NAMES.withIndex()) {
            val tierTasks = tasks.filter { it.tier == tier }
            if (tierTasks.isEmpty()) continue
            lines += ""
            lines += "<col=$TIER_COLOUR>$name</col>"
            for (task in tierTasks) {
                lines += wrap(task.text)
            }
        }
        return lines
    }

    private fun wrap(text: String): List<String> {
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && width(candidate) > LINE_WIDTH) {
                lines += line.toString()
                line = StringBuilder(word)
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }

    private fun width(text: String): Int = text.sumOf { font.glyphAdvances[it.code and 0xFF] }

    private companion object {
        const val TASKBOX = "component.area_task:taskbox"
        const val AREAS = 12
        const val TITLE = "Achievement Diary"
        const val TIER_COLOUR = "800000"
        const val LINE_WIDTH = 404
        const val MAX_LINES = 200
        const val JOURNAL_SCROLL_SETUP = 6845
        val TIER_NAMES = listOf("Easy", "Medium", "Hard", "Elite")
    }
}
