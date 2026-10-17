package org.rsmod.content.interfaces.diary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.table.AchievementDiariesRow
import org.rsmod.api.table.AchievementDiaryTasksRow
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.interfaces.diary.DiaryTasks.Area
import org.rsmod.content.interfaces.diary.DiaryTasks.Tier
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class DiaryTasksTest {
    @Test
    fun GameTestState.`a task counts once and sets the client varbits`() = runGameTest {
        assertTrue(DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 5))
        assertFalse(DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 5))
        DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 4)
        assertEquals(2, player.vars["varbit.lumbridge_easy_count"])
        assertEquals(1, player.vars["varbit.started_lumbridge_diary"])
        assertEquals(0, player.vars["varbit.lumbridge_diary_easy_complete"])
        assertEquals(setOf(4, 5), DiaryTasks.doneTasks(player, Area.Lumbridge, Tier.Easy))
    }

    @Test
    fun GameTestState.`a task sends the captured chat message`() = runGameTest {
        DiaryTasks.complete(player, Area.Lumbridge, Tier.Easy, 5)
        DiaryTasks.complete(player, Area.Varrock, Tier.Medium, 3)
        assertMessagesSent(
            "<col=dc143c>Well done! You have completed an easy task in the Lumbridge & Draynor area. " +
                "Your Achievement Diary has been updated.</col>",
            "<col=dc143c>Well done! You have completed a medium task in the Varrock area. " +
                "Your Achievement Diary has been updated.</col>",
        )
    }

    @Test
    fun GameTestState.`the tier completes 3 ticks after its last task, with the box`() =
        runGameTest(DiaryTierCompleteScript::class) {
            for (task in 1..5) DiaryTasks.complete(player, Area.Varrock, Tier.Elite, task)
            assertEquals(5, player.vars["varbit.varrock_elite_count"])
            assertEquals(0, player.vars["varbit.varrock_diary_elite_complete"])
            advance(DiaryTasks.TIER_COMPLETE_DELAY - 1)
            assertEquals(0, player.vars["varbit.varrock_diary_elite_complete"])
            advance()
            assertEquals(1, player.vars["varbit.varrock_diary_elite_complete"])
            assertModalOpen("interface.messagebox")
            assertEquals(
                "Congratulations! You have completed all of the elite tasks in the Varrock area. " +
                    "Speak to Toby behind the general store in Varrock to claim your reward.",
                DiaryTasks.tierCompleteText(Area.Varrock, Tier.Elite),
            )
        }

    @Test
    fun GameTestState.`oak logs count only when cut and burned in Lumbridge`() = runGameTest {
        val lumbridge = CoordGrid(3200, 3240, 0)
        DiaryHooks.logsBurned(player, "obj.oak_logs", lumbridge)
        assertFalse(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Easy, 7))
        DiaryHooks.logsCut(player, "obj.oak_logs", CoordGrid(3000, 3400, 0))
        DiaryHooks.logsBurned(player, "obj.oak_logs", lumbridge)
        assertFalse(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Easy, 7))
        DiaryHooks.logsCut(player, "obj.oak_logs", lumbridge)
        DiaryHooks.logsBurned(player, "obj.oak_logs", lumbridge)
        assertTrue(DiaryTasks.isDone(player, Area.Lumbridge, Tier.Easy, 7))
    }

    @Test
    fun GameTestState.`done tasks are struck in the journal`() = runGameTest {
        val diary = AchievementDiariesRow.all().first { it.area == 6 }
        val lines = AchievementDiaryScript().journalLines(diary, { tier, task -> tier == 0 && task == 5 })
        assertTrue(lines.contains("<str>Learn your age from Hans in Lumbridge."))
        assertTrue(lines.contains("<col=000000>Have Sedridor teleport you to the Essence Mine."))
    }

    /**
     * Rebuilds every captured journal from the progress it shows (struck tasks, green tiers). The
     * reward reminders ("If I ever lose my ...") are left out: rewards are not implemented.
     */
    @Test
    fun GameTestState.`every captured journal is rebuilt line for line`() = runGameTest {
        val script = AchievementDiaryScript()
        val captured = capturedJournals()
        assertEquals(12, captured.size)
        for ((name, journal) in captured) {
            val diary = AchievementDiariesRow.all().single { it.name == name }
            val expected = journal.withoutReminders()
            val headings = expected.indices.filter { TIER_HEADING.matches(expected[it]) }
            val sections =
                headings.mapIndexed { i, start ->
                    expected.subList(start, headings.getOrElse(i + 1) { expected.size }).joinToString("\n")
                }
            val tasks = AchievementDiaryTasksRow.all().filter { it.area == diary.area }
            val done =
                tasks
                    .filter { task ->
                        val struck = script.wrap(task.text).joinToString("\n") { "<str>$it" }
                        sections[task.tier].contains(struck)
                    }
                    .map { it.tier to tasks.filter { t -> t.tier == it.tier }.indexOf(it) + 1 }
                    .toSet()
            val green = headings.map { expected[it].startsWith("<col=00ff00>") }
            val lines = script.journalLines(diary, { tier, task -> (tier to task) in done }, { green[it] })
            assertEquals(expected, lines, name)
        }
    }

    @Test
    fun GameTestState.`opening a journal sets pagepos and busy, closing clears busy`() =
        runGameTest(AchievementDiaryScript::class) {
            val script = AchievementDiaryScript()
            player.withProtectedAccess { with(script) { openJournal(8) } }
            assertModalOpen("interface.journalscroll")
            val diary = AchievementDiariesRow.all().single { it.area == 8 }
            assertEquals(script.journalLines(diary).size, player.vars["varp.pagepos"])
            assertEquals(1, player.vars["varbit.busy"])
            player.ifClose()
            advance()
            assertEquals(0, player.vars["varbit.busy"])
        }

    private fun capturedJournals(): Map<String, List<String>> {
        val text = javaClass.getResource("/diary-journals.txt")!!.readText(Charsets.UTF_8)
        val journals = linkedMapOf<String, MutableList<String>>()
        var current: MutableList<String>? = null
        for (line in text.lines()) {
            when {
                line.startsWith("#") || line.isEmpty() -> continue
                line.startsWith("== ") -> current = journals.getOrPut(line.removePrefix("== ")) { mutableListOf() }
                else -> current!! += line.removeSuffix("|")
            }
        }
        return journals
    }

    private fun List<String>.withoutReminders(): List<String> {
        val result = mutableListOf<String>()
        var skip = 0
        for (line in this) {
            if (line.contains("If I ever lose my")) skip = 2
            if (skip > 0) skip-- else result += line
        }
        return result
    }

    private companion object {
        val TIER_HEADING = Regex("<col=(ffff00|00ff00)>(Easy|Medium|Hard|Elite)")
    }
}
