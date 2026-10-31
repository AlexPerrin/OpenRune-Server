package org.rsmod.content.interfaces.diary

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.FontType
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.table.AchievementDiariesRow
import org.rsmod.api.table.AchievementDiaryTasksRow
import org.rsmod.content.interfaces.emotes.PlayEmote
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * "Open <area> Journal" in the Achievement Diaries side tab (interface 259, whose list script 709
 * builds one entry per enum 595 area) opens the journal scroll (741) with the area's tasks per
 * tier. RuneLite's Diary Requirements plugin reads 741: the title must start with "Achievement
 * Diary", the first line names the diary ("Lumbridge & Draynor Tasks"), and it matches task lines
 * (joined across wrapped lines) against its own task texts. Done tasks ([DiaryTasks], Lumbridge &
 * Draynor and Varrock only) are struck through line by line.
 *
 * As captured (rsprox, opening every diary from the tab): the rows get OP1 and OP2 (the client's
 * "Wiki"); opening sets `pagepos` to the line count and `busy`, runs script 6844, writes the title
 * `<col=800000>Achievement Diary - <name>` and the lines, runs `toplevel_mainmodal_open` (2524),
 * opens 741 as the main modal and runs script 6845 with the line count; closing clears `busy`. The
 * header is yellow, each tier heading yellow (green once the tier is complete), open tasks
 * `<col=000000>`, done ones `<str>`, with each diary's own spacer lines ([JournalLayout]). Areas
 * without a row in `achievement_diaries` say so in the chat.
 */
class AchievementDiaryScript : PluginScript() {
    private val font: FontType by lazy { ServerCacheManager.getFont("font.p12_full".asRSCM())!! }

    override fun ScriptContext.startup() {
        onPlayerLogin { DiaryTasks.sync(player) }
        onEvent<SkillingActionCompleteEvent> {
            val product = context as? SkillingActionContext.Product ?: return@onEvent
            when (val source = product.source) {
                is SkillingProductSource.Mining -> DiaryHooks.oreMined(player, product.item, source.rock.coords)
                is SkillingProductSource.Woodcutting -> {
                    DiaryHooks.logsCut(player, product.item, source.tree.coords)
                    DiaryHooks.logsChopped(player, product.item, source.tree.coords)
                }
                is SkillingProductSource.Fishing -> DiaryHooks.fishCaught(player, product.item, player.coords)
            }
        }
        onEvent<PlayEmote> { DiaryHooks.emotePerformed(player, RSCM.getReverseMapping(RSCMType.SEQ, seq.id)) }
        onIfOpen("interface.area_task") {
            player.ifSetEvents(TASKBOX, 0 until AREAS, IfEvent.Op1, IfEvent.Op2)
        }
        onIfClose(JOURNAL) { VarPlayerIntMapSetter.set(player, "varbit.busy", 0) }
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
        val tracked = TRACKED_AREAS[diary.area]
        val shown = journalLines(diary, doneTasks(tracked), tierComplete(tracked)).take(MAX_LINES)
        VarPlayerIntMapSetter.set(player, "varp.pagepos", shown.size)
        VarPlayerIntMapSetter.set(player, "varbit.busy", 1)
        runClientScript(JOURNAL_RESET)
        ifSetText("component.journalscroll:title", "<col=800000>Achievement Diary - ${diary.name}")
        shown.forEachIndexed { index, line -> ifSetText("component.journalscroll:qj${index + 1}", line) }
        ifOpenMainModal(JOURNAL)
        runClientScript(JOURNAL_SCROLL_SETUP, 1, shown.size)
    }

    private fun ProtectedAccess.doneTasks(tracked: DiaryTasks.Area?): (Int, Int) -> Boolean {
        tracked ?: return { _, _ -> false }
        val done = DiaryTasks.Tier.entries.map { DiaryTasks.doneTasks(player, tracked, it) }
        return { tier, task -> task in done[tier] }
    }

    private fun ProtectedAccess.tierComplete(tracked: DiaryTasks.Area?): (Int) -> Boolean {
        tracked ?: return { false }
        return { tier -> DiaryTasks.isTierComplete(player, tracked, DiaryTasks.Tier.entries[tier]) }
    }

    fun journalLines(
        diary: AchievementDiariesRow,
        isDone: (tier: Int, task: Int) -> Boolean = { _, _ -> false },
        isTierComplete: (tier: Int) -> Boolean = { false },
    ): List<String> {
        val layout = LAYOUTS[diary.area] ?: JournalLayout()
        val lines = mutableListOf("<col=$YELLOW>${diary.heading}", layout.afterHeader)
        val tasks = AchievementDiaryTasksRow.all().filter { it.area == diary.area }
        for ((tier, name) in TIER_NAMES.withIndex()) {
            val tierTasks = tasks.filter { it.tier == tier }
            if (tierTasks.isEmpty()) continue
            val elite = tier == TIER_NAMES.lastIndex
            lines += "<col=${if (isTierComplete(tier)) GREEN else YELLOW}>$name"
            lines += if (elite) layout.eliteBefore else layout.before
            for ((index, task) in tierTasks.withIndex()) {
                val wrapped = wrap(task.text)
                lines +=
                    if (isDone(tier, index + 1)) {
                        wrapped.map { "<str>$it" }
                    } else {
                        wrapped.mapIndexed { i, line ->
                            val prefix = if (line.startsWith("<col=")) "" else "<col=000000>"
                            val suffix = if (i == wrapped.lastIndex) layout.openSuffix else ""
                            "$prefix$line$suffix"
                        }
                    }
            }
            lines += if (elite) layout.eliteAfter else layout.after
        }
        return lines
    }

    /**
     * Word-wraps [text] to the journal's width: p12_full advances, at most [LINE_WIDTH] px (the
     * width every wrapped line of the captured journals fits, and no wider). Colour tags take no width; a colour still open at a
     * break is repeated at the start of the next line (as the game does: "...Logs.<col=800000>
     * (Requires Chewed" / "<col=800000>Bones.)<col=000000>").
     */
    fun wrap(text: String): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && width(candidate) > LINE_WIDTH) {
                lines += line
                line = (COLOUR_TAG.findAll(line).lastOrNull()?.value ?: "") + word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    private fun width(text: String): Int =
        text.replace(TAG, "").sumOf { font.glyphAdvances[it.code and 0xFF] }

    /**
     * The spacer lines each diary's journal has around its header and tiers (captured; they differ
     * per diary). [eliteBefore] / [eliteAfter] are the Elite tier's, [openSuffix] ends an open task.
     */
    private class JournalLayout(
        val afterHeader: String = " ",
        val before: List<String> = listOf("<col=000000>", " "),
        val eliteBefore: List<String> = before,
        val after: List<String> = listOf(" "),
        val eliteAfter: List<String> = emptyList(),
        val openSuffix: String = "",
    )

    private companion object {
        const val TASKBOX = "component.area_task:taskbox"
        const val JOURNAL = "interface.journalscroll"
        const val AREAS = 12
        const val YELLOW = "ffff00"
        const val GREEN = "00ff00"
        const val LINE_WIDTH = 331
        const val MAX_LINES = 200
        const val JOURNAL_RESET = 6844
        const val JOURNAL_SCROLL_SETUP = 6845
        val TIER_NAMES = listOf("Easy", "Medium", "Hard", "Elite")
        val TAG = Regex("<[^>]*>")
        val COLOUR_TAG = Regex("<col=[0-9a-f]{6}>")
        val TRACKED_AREAS = mapOf(6 to DiaryTasks.Area.Lumbridge, 8 to DiaryTasks.Area.Varrock)
        val ELITE_STR = listOf("<str>", " ")

        /** By enum 595 index; the rest use the default layout. */
        val LAYOUTS =
            mapOf(
                0 to JournalLayout(afterHeader = "", before = listOf("<col=000000>"), after = listOf("")),
                1 to JournalLayout(after = listOf(" ", " "), eliteAfter = listOf(" ")),
                3 to JournalLayout(eliteBefore = ELITE_STR),
                5 to JournalLayout(eliteBefore = ELITE_STR),
                6 to JournalLayout(eliteBefore = ELITE_STR),
                7 to JournalLayout(eliteBefore = ELITE_STR),
                8 to JournalLayout(eliteBefore = ELITE_STR),
                11 to
                    JournalLayout(afterHeader = "", before = listOf(""), after = listOf(""), openSuffix = "</col>"),
            )
    }
}
