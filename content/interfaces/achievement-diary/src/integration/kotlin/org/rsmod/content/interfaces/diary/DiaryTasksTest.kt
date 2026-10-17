package org.rsmod.content.interfaces.diary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.table.AchievementDiariesRow
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
    fun GameTestState.`the last task of a tier completes it`() = runGameTest {
        for (task in 1..5) DiaryTasks.complete(player, Area.Varrock, Tier.Elite, task)
        assertEquals(5, player.vars["varbit.varrock_elite_count"])
        assertEquals(1, player.vars["varbit.varrock_diary_elite_complete"])
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
        val lines = AchievementDiaryScript().journalLines(diary) { tier, task -> tier == 0 && task == 5 }
        assertTrue(lines.contains("<str>Learn your age from Hans in Lumbridge.</str>"))
        assertTrue(lines.contains("Have Sedridor teleport you to the Essence Mine."))
    }
}
